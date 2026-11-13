<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from 'vue';
import Icon from './ui/Icon.vue';
import { useT } from '../lib/useT.js';

const props = defineProps({
  imageSrc: { type: String, default: '' },
  brushSize: { type: Number, default: 28 },
  /** brush | eraser | rect | lasso | wand | pan */
  tool: { type: String, default: 'brush' },
  /** mint | red | purple */
  maskTint: { type: String, default: 'mint' },
  /** 是否显示内置工具栏 */
  toolbar: { type: Boolean, default: true },
});

const emit = defineEmits(['mask-change', 'ready', 'update:tool', 'update:brushSize']);
const { t } = useT();

const TOOLS = [
  { id: 'brush', icon: 'brush', key: 'uiLeToolBrush', kbd: 'B' },
  { id: 'eraser', icon: 'eraser', key: 'uiLeToolEraser', kbd: 'E' },
  { id: 'rect', icon: 'square', key: 'uiLeToolRect', kbd: 'R' },
  { id: 'lasso', icon: 'lasso', key: 'uiLeToolLasso', kbd: 'L' },
  { id: 'wand', icon: 'wand', key: 'uiLeToolWand', kbd: 'W' },
  { id: 'pan', icon: 'hand', key: 'uiLeToolPan', kbd: 'H' },
];
const KEY_TOOL = { b: 'brush', e: 'eraser', r: 'rect', l: 'lasso', w: 'wand', h: 'pan' };
const TINTS = { mint: [45, 212, 191], red: [255, 55, 70], purple: [168, 85, 247] };
const MAX_HISTORY = 40;
const WAND_MAX_SIDE = 1600;

const rootRef = ref(null);
const viewportRef = ref(null);
const imgCanvasRef = ref(null);
const maskVisRef = ref(null);

let img = null;
let imgW = 0;
let imgH = 0;
let maskCanvas = null;
let maskCtx = null;
/** 魔棒取色用的缩略像素 */
let wandSrc = null;

const history = [];
const historyIndex = ref(-1);
const historyLength = ref(0);

let isDrawing = false;
let isPanning = false;
let panStart = null;
let lastBrush = null;
let rectStart = null;
let rectPreview = null;
let lassoPts = null;
let subtractStroke = false;
let rafId = 0;

const spaceDown = ref(false);
const altDown = ref(false);
const ready = ref(false);
const size = ref(props.brushSize);
const tolerance = ref(32);
const zoom = ref(1);
const panX = ref(0);
const panY = ref(0);
const dispScale = ref(1);
const cursor = ref({ x: 0, y: 0, show: false });

const canUndo = computed(() => historyIndex.value > 0);
const canRedo = computed(() => historyIndex.value < historyLength.value - 1);
const zoomPercent = computed(() => Math.round(zoom.value * 100));
const panning = computed(() => props.tool === 'pan' || spaceDown.value);
const stageStyle = computed(() => ({
  transform: `translate(${panX.value}px, ${panY.value}px) scale(${zoom.value})`,
}));
const showBrushCursor = computed(
  () => cursor.value.show && !panning.value && (props.tool === 'brush' || props.tool === 'eraser')
);
const cursorStyle = computed(() => {
  const d = Math.max(4, size.value * dispScale.value);
  return { width: d + 'px', height: d + 'px', left: cursor.value.x + 'px', top: cursor.value.y + 'px' };
});

/** 按视口等比缩放，避免仅 img canvas 被 CSS max-width 压扁而 mask 仍为原尺寸。 */
function computeDisplaySize() {
  if (!imgW || !imgH) return { displayW: 1, displayH: 1 };
  let maxW = 900;
  let maxH = 720;
  const vp = viewportRef.value;
  if (vp) {
    maxW = Math.min(maxW, Math.max(1, vp.clientWidth - 8));
    maxH = Math.min(maxH, Math.max(1, vp.clientHeight - 8));
  }
  const scale = Math.min(1, maxW / imgW, maxH / imgH);
  return {
    displayW: Math.max(1, Math.round(imgW * scale)),
    displayH: Math.max(1, Math.round(imgH * scale)),
  };
}

function initMask() {
  maskCanvas = document.createElement('canvas');
  maskCanvas.width = imgW;
  maskCanvas.height = imgH;
  maskCtx = maskCanvas.getContext('2d', { willReadFrequently: true });
  maskCtx.fillStyle = '#000';
  maskCtx.fillRect(0, 0, imgW, imgH);
  history.length = 0;
  historyIndex.value = -1;
  pushHistory();
}

function buildWandSource() {
  wandSrc = null;
  if (!img) return;
  const s = Math.min(1, WAND_MAX_SIDE / Math.max(imgW, imgH));
  const w = Math.max(1, Math.round(imgW * s));
  const h = Math.max(1, Math.round(imgH * s));
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const ctx = c.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(img, 0, 0, w, h);
  try {
    wandSrc = { w, h, data: ctx.getImageData(0, 0, w, h).data };
  } catch {
    wandSrc = null;
  }
}

function pushHistory() {
  if (!maskCtx) return;
  const snap = maskCtx.getImageData(0, 0, imgW, imgH);
  history.splice(historyIndex.value + 1);
  history.push(snap);
  if (history.length > MAX_HISTORY) history.shift();
  historyIndex.value = history.length - 1;
  historyLength.value = history.length;
}

function restore(i) {
  historyIndex.value = i;
  maskCtx.putImageData(history[i], 0, 0);
  rectPreview = null;
  lassoPts = null;
  scheduleRedraw();
  emit('mask-change');
}

function undo() {
  if (canUndo.value) restore(historyIndex.value - 1);
}

function redo() {
  if (canRedo.value) restore(historyIndex.value + 1);
}

function commit() {
  pushHistory();
  scheduleRedraw();
  emit('mask-change');
}

function clearMask() {
  if (!maskCtx) return;
  maskCtx.fillStyle = '#000';
  maskCtx.fillRect(0, 0, imgW, imgH);
  rectPreview = null;
  lassoPts = null;
  lastBrush = null;
  commit();
}

function invertMask() {
  if (!maskCtx) return;
  maskCtx.save();
  maskCtx.globalCompositeOperation = 'difference';
  maskCtx.fillStyle = '#fff';
  maskCtx.fillRect(0, 0, imgW, imgH);
  maskCtx.restore();
  commit();
}

function loadImage(src) {
  ready.value = false;
  if (!src) {
    img = null;
    imgW = 0;
    imgH = 0;
    return;
  }
  const el = new Image();
  el.crossOrigin = 'anonymous';
  el.onload = () => {
    img = el;
    imgW = el.naturalWidth;
    imgH = el.naturalHeight;
    zoom.value = 1;
    panX.value = 0;
    panY.value = 0;
    initMask();
    buildWandSource();
    ready.value = true;
    redraw(true);
    emit('ready', { width: imgW, height: imgH });
    emit('mask-change');
  };
  el.onerror = () => {
    img = null;
  };
  el.src = src;
}

function getPointerPos(e) {
  const canvas = maskVisRef.value;
  if (!canvas || !imgW) return { x: 0, y: 0 };
  const rect = canvas.getBoundingClientRect();
  return {
    x: ((e.clientX - rect.left) / rect.width) * imgW,
    y: ((e.clientY - rect.top) / rect.height) * imgH,
  };
}

function paintColor() {
  return props.tool === 'eraser' || subtractStroke ? '#000' : '#fff';
}

function drawBrushSegment(x0, y0, x1, y1) {
  const r = size.value / 2;
  maskCtx.lineCap = 'round';
  maskCtx.lineJoin = 'round';
  maskCtx.lineWidth = r * 2;
  maskCtx.strokeStyle = paintColor();
  maskCtx.beginPath();
  maskCtx.moveTo(x0, y0);
  maskCtx.lineTo(x1, y1);
  maskCtx.stroke();
}

function drawBrushAt(x, y) {
  maskCtx.beginPath();
  maskCtx.arc(x, y, size.value / 2, 0, Math.PI * 2);
  maskCtx.fillStyle = paintColor();
  maskCtx.fill();
}

/** 魔棒：以点击处颜色为种子做扫描线泛洪填充，结果按原图尺寸合入蒙版。 */
function magicWand(x, y, subtract) {
  if (!wandSrc) return false;
  const { w, h, data } = wandSrc;
  const sx = Math.min(w - 1, Math.max(0, Math.floor((x / imgW) * w)));
  const sy = Math.min(h - 1, Math.max(0, Math.floor((y / imgH) * h)));
  const si = (sy * w + sx) * 4;
  const r0 = data[si];
  const g0 = data[si + 1];
  const b0 = data[si + 2];
  const tol = tolerance.value;
  const seen = new Uint8Array(w * h);
  const match = (p) => {
    const i = p * 4;
    return (
      Math.abs(data[i] - r0) <= tol && Math.abs(data[i + 1] - g0) <= tol && Math.abs(data[i + 2] - b0) <= tol
    );
  };
  const out = new ImageData(w, h);
  const od = out.data;
  const v = subtract ? 0 : 255;
  const stack = [sx, sy];
  while (stack.length) {
    const py = stack.pop();
    let px = stack.pop();
    let p = py * w + px;
    while (px >= 0 && !seen[p] && match(p)) {
      px--;
      p--;
    }
    px++;
    p++;
    let up = false;
    let down = false;
    while (px < w && !seen[p] && match(p)) {
      seen[p] = 1;
      const o = p * 4;
      od[o] = v;
      od[o + 1] = v;
      od[o + 2] = v;
      od[o + 3] = 255;
      if (py > 0) {
        const q = p - w;
        const m = !seen[q] && match(q);
        if (m && !up) stack.push(px, py - 1);
        up = m;
      }
      if (py < h - 1) {
        const q = p + w;
        const m = !seen[q] && match(q);
        if (m && !down) stack.push(px, py + 1);
        down = m;
      }
      px++;
      p++;
    }
  }
  const tmp = document.createElement('canvas');
  tmp.width = w;
  tmp.height = h;
  tmp.getContext('2d').putImageData(out, 0, 0);
  maskCtx.drawImage(tmp, 0, 0, imgW, imgH);
  return true;
}

function shouldPan(e) {
  return panning.value || e.button === 1;
}

function onPointerDown(e) {
  if (!img || !maskCtx) return;
  if (shouldPan(e)) {
    e.preventDefault();
    isPanning = true;
    panStart = { x: e.clientX - panX.value, y: e.clientY - panY.value };
    return;
  }
  if (e.pointerType === 'mouse' && e.button !== 0) return;
  e.preventDefault();
  subtractStroke = e.altKey;
  const { x, y } = getPointerPos(e);
  if (props.tool === 'wand') {
    if (magicWand(x, y, subtractStroke)) commit();
    return;
  }
  maskVisRef.value?.setPointerCapture?.(e.pointerId);
  isDrawing = true;
  if (props.tool === 'rect') {
    rectStart = { x, y };
    rectPreview = { x, y, w: 0, h: 0 };
  } else if (props.tool === 'lasso') {
    lassoPts = [{ x, y }];
  } else {
    lastBrush = { x, y };
    drawBrushAt(x, y);
    scheduleRedraw();
  }
}

function onPointerMove(e) {
  const vp = viewportRef.value?.getBoundingClientRect();
  if (vp) cursor.value = { x: e.clientX - vp.left, y: e.clientY - vp.top, show: true };
  if (isPanning && panStart) {
    panX.value = e.clientX - panStart.x;
    panY.value = e.clientY - panStart.y;
    return;
  }
  if (!isDrawing || !img || !maskCtx) return;
  const { x, y } = getPointerPos(e);
  if (props.tool === 'rect' && rectStart) {
    rectPreview = {
      x: Math.min(rectStart.x, x),
      y: Math.min(rectStart.y, y),
      w: Math.abs(x - rectStart.x),
      h: Math.abs(y - rectStart.y),
    };
  } else if (props.tool === 'lasso' && lassoPts) {
    const last = lassoPts[lassoPts.length - 1];
    if (Math.hypot(x - last.x, y - last.y) * dispScale.value > 2) lassoPts.push({ x, y });
  } else if (lastBrush) {
    drawBrushSegment(lastBrush.x, lastBrush.y, x, y);
    lastBrush = { x, y };
  }
  scheduleRedraw();
}

function onPointerUp(e) {
  if (isPanning) {
    isPanning = false;
    panStart = null;
    return;
  }
  if (!isDrawing) return;
  isDrawing = false;
  lastBrush = null;
  maskVisRef.value?.releasePointerCapture?.(e.pointerId);
  if (props.tool === 'rect' && rectPreview) {
    const { x: rx, y: ry, w, h } = rectPreview;
    if (w > 2 && h > 2) {
      maskCtx.fillStyle = paintColor();
      maskCtx.fillRect(rx, ry, w, h);
    }
    rectStart = null;
    rectPreview = null;
  } else if (props.tool === 'lasso' && lassoPts) {
    if (lassoPts.length > 2) {
      maskCtx.beginPath();
      maskCtx.moveTo(lassoPts[0].x, lassoPts[0].y);
      for (let i = 1; i < lassoPts.length; i++) maskCtx.lineTo(lassoPts[i].x, lassoPts[i].y);
      maskCtx.closePath();
      maskCtx.fillStyle = paintColor();
      maskCtx.fill();
    }
    lassoPts = null;
  }
  subtractStroke = false;
  commit();
}

function onPointerLeave(e) {
  cursor.value = { ...cursor.value, show: false };
  onPointerUp(e);
}

function paintMaskOverlay(vctx, displayW, displayH) {
  const tmp = document.createElement('canvas');
  tmp.width = displayW;
  tmp.height = displayH;
  const tctx = tmp.getContext('2d', { willReadFrequently: true });
  tctx.drawImage(maskCanvas, 0, 0, displayW, displayH);
  const id = tctx.getImageData(0, 0, displayW, displayH);
  const data = id.data;
  const [r, g, b] = TINTS[props.maskTint] || TINTS.mint;
  for (let i = 0; i < data.length; i += 4) {
    const lum = data[i];
    if (lum < 10) {
      data[i + 3] = 0;
      continue;
    }
    data[i] = r;
    data[i + 1] = g;
    data[i + 2] = b;
    data[i + 3] = Math.min(255, Math.round(lum * 0.5));
  }
  tctx.putImageData(id, 0, 0);
  vctx.drawImage(tmp, 0, 0);
}

function scheduleRedraw() {
  if (rafId) return;
  rafId = requestAnimationFrame(() => {
    rafId = 0;
    redraw(false);
  });
}

function strokeGuide(vctx, draw) {
  vctx.save();
  vctx.lineWidth = 1.5;
  vctx.setLineDash([6, 4]);
  vctx.strokeStyle = 'rgba(0, 0, 0, 0.7)';
  draw();
  vctx.stroke();
  vctx.lineDashOffset = 5;
  vctx.strokeStyle = 'rgba(255, 255, 255, 0.95)';
  vctx.stroke();
  vctx.restore();
}

function redraw(resize = true) {
  const imgC = imgCanvasRef.value;
  const visC = maskVisRef.value;
  if (!imgC || !visC || !img || !maskCanvas) return;

  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const { displayW, displayH } = computeDisplaySize();
  dispScale.value = displayW / imgW;
  const changed = resize || imgC.width !== displayW * dpr || imgC.height !== displayH * dpr;

  if (changed) {
    for (const c of [imgC, visC]) {
      c.width = displayW * dpr;
      c.height = displayH * dpr;
      c.style.width = `${displayW}px`;
      c.style.height = `${displayH}px`;
    }
    const ictx = imgC.getContext('2d');
    ictx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ictx.clearRect(0, 0, displayW, displayH);
    ictx.drawImage(img, 0, 0, displayW, displayH);
  }

  const vctx = visC.getContext('2d');
  vctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  vctx.clearRect(0, 0, displayW, displayH);
  paintMaskOverlay(vctx, displayW, displayH);

  const k = dispScale.value;
  if (rectPreview && rectPreview.w > 0 && rectPreview.h > 0) {
    const { x, y, w, h } = rectPreview;
    strokeGuide(vctx, () => {
      vctx.beginPath();
      vctx.rect(x * k, y * k, w * k, h * k);
    });
  }
  if (lassoPts && lassoPts.length > 1) {
    strokeGuide(vctx, () => {
      vctx.beginPath();
      vctx.moveTo(lassoPts[0].x * k, lassoPts[0].y * k);
      for (let i = 1; i < lassoPts.length; i++) vctx.lineTo(lassoPts[i].x * k, lassoPts[i].y * k);
    });
  }
}

function exportMaskBlob() {
  if (!maskCanvas) return Promise.resolve(null);
  return new Promise((resolve) => {
    maskCanvas.toBlob((blob) => resolve(blob), 'image/png');
  });
}

function hasMaskContent() {
  if (!maskCtx) return false;
  const data = maskCtx.getImageData(0, 0, imgW, imgH).data;
  for (let i = 0; i < data.length; i += 4) {
    if (data[i] > 12) return true;
  }
  return false;
}

function setTool(id) {
  emit('update:tool', id);
}

function zoomBy(d) {
  zoom.value = Math.min(4, Math.max(0.25, +(zoom.value + d).toFixed(2)));
}

function zoomReset() {
  zoom.value = 1;
  panX.value = 0;
  panY.value = 0;
}

function onWheel(e) {
  if (!img) return;
  if (e.ctrlKey || e.metaKey || e.altKey) {
    if (props.tool === 'brush' || props.tool === 'eraser') {
      size.value = Math.min(160, Math.max(4, size.value + (e.deltaY > 0 ? -2 : 2)));
      return;
    }
  }
  zoomBy(e.deltaY > 0 ? -0.1 : 0.1);
}

function requestFullscreen() {
  const el = rootRef.value;
  if (!el) return;
  if (document.fullscreenElement) document.exitFullscreen();
  else el.requestFullscreen?.();
}

function isTyping(e) {
  return e.target?.matches?.('input, textarea, select, [contenteditable="true"]');
}

function onKeyDown(e) {
  altDown.value = e.altKey;
  if (isTyping(e) || !ready.value) return;
  const k = e.key.toLowerCase();
  if ((e.ctrlKey || e.metaKey) && k === 'z') {
    e.preventDefault();
    if (e.shiftKey) redo();
    else undo();
    return;
  }
  if ((e.ctrlKey || e.metaKey) && k === 'y') {
    e.preventDefault();
    redo();
    return;
  }
  if (e.ctrlKey || e.metaKey || e.altKey) return;
  if (e.code === 'Space' && !spaceDown.value) {
    spaceDown.value = true;
    e.preventDefault();
    return;
  }
  if (KEY_TOOL[k]) setTool(KEY_TOOL[k]);
  else if (k === '[') size.value = Math.max(4, size.value - 4);
  else if (k === ']') size.value = Math.min(160, size.value + 4);
}

function onKeyUp(e) {
  altDown.value = e.altKey;
  if (e.code === 'Space') spaceDown.value = false;
}

watch(
  () => props.imageSrc,
  (src) => loadImage(src),
  { immediate: true }
);
watch(
  () => props.brushSize,
  (v) => (size.value = v)
);
watch(size, (v) => emit('update:brushSize', v));
watch(
  () => [props.tool, props.maskTint],
  () => {
    rectPreview = null;
    lassoPts = null;
    scheduleRedraw();
  }
);

let resizeObserver = null;

onMounted(() => {
  window.addEventListener('keydown', onKeyDown);
  window.addEventListener('keyup', onKeyUp);
  if (viewportRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => redraw(true));
    resizeObserver.observe(viewportRef.value);
  }
});

onUnmounted(() => {
  if (rafId) cancelAnimationFrame(rafId);
  resizeObserver?.disconnect();
  resizeObserver = null;
  window.removeEventListener('keydown', onKeyDown);
  window.removeEventListener('keyup', onKeyUp);
});

defineExpose({
  undo,
  redo,
  canUndo,
  canRedo,
  clearMask,
  invertMask,
  exportMaskBlob,
  hasMaskContent,
  requestFullscreen,
  redraw,
  zoomIn: () => zoomBy(0.25),
  zoomOut: () => zoomBy(-0.25),
  zoomReset,
});
</script>

<template>
  <div ref="rootRef" class="me">
    <div v-if="toolbar" class="me__bar" role="toolbar">
      <div class="me__group">
        <button
          v-for="it in TOOLS"
          :key="it.id"
          type="button"
          class="me__tool"
          :class="{ on: tool === it.id }"
          :title="`${t(it.key)} (${it.kbd})`"
          :aria-pressed="tool === it.id"
          @click="setTool(it.id)"
        >
          <Icon :name="it.icon" :size="17" />
          <span class="me__tool-label">{{ t(it.key) }}</span>
        </button>
      </div>

      <div v-if="tool === 'brush' || tool === 'eraser'" class="me__slider">
        <span>{{ t('localEditBrushSize') }}</span>
        <input v-model.number="size" type="range" min="4" max="160" step="2" />
        <output>{{ size }}</output>
      </div>
      <div v-else-if="tool === 'wand'" class="me__slider">
        <span>{{ t('uiLeTolerance') }}</span>
        <input v-model.number="tolerance" type="range" min="4" max="120" step="2" />
        <output>{{ tolerance }}</output>
      </div>

      <div class="me__group me__group--end">
        <button type="button" class="me__icon" :disabled="!canUndo" :title="t('localEditUndo') + ' (Ctrl+Z)'" @click="undo">
          <Icon name="undo" :size="17" />
        </button>
        <button
          type="button"
          class="me__icon"
          :disabled="!canRedo"
          :title="t('localEditRedo') + ' (Ctrl+Shift+Z)'"
          @click="redo"
        >
          <Icon name="redo" :size="17" />
        </button>
        <span class="me__sep"></span>
        <button type="button" class="me__icon" :title="t('uiLeInvert')" @click="invertMask">
          <Icon name="compare" :size="17" />
        </button>
        <button type="button" class="me__icon" :title="t('localEditClearMask')" @click="clearMask">
          <Icon name="trash" :size="17" />
        </button>
        <span class="me__sep"></span>
        <button type="button" class="me__icon" :title="t('localEditZoomOut')" @click="zoomBy(-0.25)">
          <Icon name="zoomOut" :size="17" />
        </button>
        <button type="button" class="me__zoom" :title="t('localEditZoomReset')" @click="zoomReset">{{ zoomPercent }}%</button>
        <button type="button" class="me__icon" :title="t('localEditZoomIn')" @click="zoomBy(0.25)">
          <Icon name="zoomIn" :size="17" />
        </button>
        <button type="button" class="me__icon" :title="t('localEditFullscreen')" @click="requestFullscreen">
          <Icon name="maximize" :size="17" />
        </button>
      </div>
    </div>

    <div ref="viewportRef" class="me__viewport" @wheel.prevent="onWheel">
      <div class="me__stage" :style="stageStyle">
        <div class="me__stack">
          <canvas ref="imgCanvasRef" class="me__img" aria-hidden="true" />
          <canvas
            ref="maskVisRef"
            class="me__mask"
            :class="{
              'me__mask--pan': panning,
              'me__mask--brush': !panning && (tool === 'brush' || tool === 'eraser'),
              'me__mask--wand': !panning && tool === 'wand',
            }"
            aria-label="mask editor"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointerleave="onPointerLeave"
          />
        </div>
      </div>
      <span
        v-if="showBrushCursor"
        class="me__cursor"
        :class="{ 'me__cursor--erase': tool === 'eraser' || altDown }"
        :style="[cursorStyle, { transform: `translate(-50%, -50%) scale(${zoom})` }]"
      ></span>
      <div class="me__hint">
        <template v-if="tool === 'wand'">{{ t('uiLeWandHint') }}</template>
        <template v-else-if="tool === 'lasso'">{{ t('uiLeLassoHint') }}</template>
        <template v-else-if="tool === 'rect'">{{ t('uiLeRectHint') }}</template>
        <template v-else-if="tool === 'pan'">{{ t('localEditPanHint') }}</template>
        <template v-else>{{ t('uiLeBrushHint') }}</template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.me {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-height: 320px;
  gap: 10px;
}
.me:fullscreen {
  background: var(--bg, #0b0d10);
  padding: 16px;
}
.me__bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding: 6px;
  border-radius: var(--radius);
  background: var(--panel-2);
  border: 1px solid var(--line);
}
.me__group {
  display: flex;
  align-items: center;
  gap: 2px;
}
.me__group--end {
  margin-left: auto;
}
.me__tool,
.me__icon,
.me__zoom {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 32px;
  padding: 0 9px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-2);
  font: inherit;
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}
.me__icon {
  width: 32px;
  padding: 0;
}
.me__zoom {
  min-width: 52px;
  padding: 0 6px;
  font-variant-numeric: tabular-nums;
}
.me__tool:hover,
.me__icon:hover:not(:disabled),
.me__zoom:hover {
  background: var(--panel-3);
  color: var(--text);
}
.me__tool.on {
  background: var(--brand-soft);
  border-color: rgba(108, 76, 246, 0.4);
  color: var(--brand);
}
.me__icon:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.me__sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: var(--line);
}
.me__slider {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.75rem;
  color: var(--text-3);
}
.me__slider input {
  width: 110px;
  accent-color: var(--brand);
}
.me__slider output {
  min-width: 26px;
  font-variant-numeric: tabular-nums;
  color: var(--text);
}
.me__viewport {
  position: relative;
  flex: 1;
  min-height: 300px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  background:
    repeating-conic-gradient(rgba(255, 255, 255, 0.03) 0 25%, transparent 0 50%) 0 0 / 20px 20px,
    var(--bg-elev);
  border: 1px solid var(--line);
}
.me__stage {
  transform-origin: center center;
  will-change: transform;
}
.me__stack {
  position: relative;
  line-height: 0;
}
.me__img {
  display: block;
  border-radius: 6px;
}
.me__mask {
  position: absolute;
  left: 0;
  top: 0;
  cursor: crosshair;
  touch-action: none;
  border-radius: 6px;
}
.me__mask--brush {
  cursor: none;
}
.me__mask--wand {
  cursor: copy;
}
.me__mask--pan {
  cursor: grab;
}
.me__mask--pan:active {
  cursor: grabbing;
}
.me__cursor {
  position: absolute;
  pointer-events: none;
  border-radius: 50%;
  border: 1.5px solid #fff;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.6), inset 0 0 0 1px rgba(0, 0, 0, 0.35);
  background: rgba(108, 76, 246, 0.18);
}
.me__cursor--erase {
  background: rgba(244, 63, 94, 0.18);
}
.me__hint {
  position: absolute;
  left: 50%;
  bottom: 10px;
  transform: translateX(-50%);
  max-width: calc(100% - 24px);
  padding: 5px 12px;
  border-radius: 999px;
  background: rgba(11, 13, 16, 0.78);
  backdrop-filter: blur(8px);
  color: var(--text-2);
  font-size: 0.72rem;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  pointer-events: none;
}
@media (max-width: 760px) {
  .me__tool-label {
    display: none;
  }
  .me__tool {
    width: 34px;
    padding: 0;
  }
  .me__group--end {
    margin-left: 0;
  }
}
</style>
