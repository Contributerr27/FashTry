<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { useRouter } from 'vue-router';
import { toPng } from 'html-to-image';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';

const router = useRouter();

const TEMPLATES = [
  { id: 'clipnote', labelKey: 'xhsTplClipnote', swatch: '#f5c518', ink: '#111', family: 'sheet' },
  { id: 'textnote', labelKey: 'xhsTplTextnote', swatch: '#f0a202', ink: '#111', family: 'bubble' },
  { id: 'handbook', labelKey: 'xhsTplHandbook', swatch: '#cfe8ff', ink: '#1a3a6b', family: 'handbook' },
  { id: 'boardmint', labelKey: 'xhsTplBoardmint', swatch: '#7dd3a0', ink: '#111', family: 'sheet' },
  { id: 'boardpink', labelKey: 'xhsTplBoardpink', swatch: '#ff8fab', ink: '#111', family: 'sheet' },
  { id: 'boardsky', labelKey: 'xhsTplBoardsky', swatch: '#7ec8e3', ink: '#111', family: 'sheet' },
  { id: 'papercraft', labelKey: 'xhsTplPapercraft', swatch: '#efe6d5', ink: '#1a1a1a', family: 'sheet' },
  { id: 'dark', labelKey: 'xhsTplDark', swatch: '#12151c', ink: '#f5f5f5', family: 'flat' },
  { id: 'customref', labelKey: 'xhsTplCustomref', swatch: '#334155', ink: '#fff', family: 'ref', needsRef: true },
  { id: 'random', labelKey: 'xhsTplRandom', swatch: 'conic-gradient(from 180deg, #f5c518, #ff8fab, #7dd3a0, #cfe8ff, #f5c518)', ink: '#111', isRandom: true, family: 'sheet' },
];

const PALETTE = [
  '#F5C518',
  '#F0A202',
  '#FF8FAB',
  '#7DD3A0',
  '#7EC8E3',
  '#EFE6D5',
  '#FAF7F2',
  '#0E1118',
  '#FFE4EC',
  '#FFF1D6',
];

const DEMO = {
  coverTitle: '答应我，先收藏再看',
  coverSubtitle: '3 个小技巧，立刻能用上',
  pages: [
    { heading: '为什么有效', body: '1️⃣ 抓住情绪钩子\n2️⃣ 数字制造确定性\n3️⃣ 留白让人想点开' },
    { heading: '怎么写标题', body: '1️⃣ 悬念开头\n2️⃣ 痛点直给\n3️⃣ 结尾给行动指令' },
    { heading: '排版秘诀', body: '1️⃣ 短句分行\n2️⃣ Emoji 节奏感\n3️⃣ 列表一眼扫完' },
  ],
  cta: '有用就点赞收藏✨',
  noteCaption:
    '姐妹们听我说！！🔥\n这个真的太好用了😭\n\n1️⃣ 先看效果\n2️⃣ 再看步骤\n3️⃣ 建议直接收藏⭐\n\n有用记得点赞呀～❤️\n#小红书干货 #AI工具 #效率提升',
};

const topic = ref('');
const sellingPoints = ref('');
const audience = ref('');
const brandName = ref('FashTry');
const templateId = ref('clipnote');
const backgroundColor = ref('');
const textColor = ref('');
const referenceImageUrl = ref('');
const referencePreview = ref('');
const refUploading = ref(false);
const refInputRef = ref(null);
const consumeCredits = ref(1);
const activeIdx = ref(0);

const generating = ref(false);
const progress = ref(0);
const progressLabel = ref('');
const exporting = ref(false);
const content = ref(null);
const currentTaskId = ref(null);
const toast = ref({ show: false, msg: '', type: 'info' });
const cardRefs = ref([]);
const historyRecords = ref([]);
const historyLoading = ref(false);

let progressTimer = null;

const canSubmit = computed(() => topic.value.trim().length > 0 && !generating.value);
const hasResult = computed(() => !!content.value);
const displayBrand = computed(() => (brandName.value || '').trim() || 'FashTry');
const noteCaption = computed(() => {
  if (content.value?.noteCaption) return content.value.noteCaption;
  if (content.value) return '';
  return DEMO.noteCaption;
});
const cta = computed(() => content.value?.cta || DEMO.cta);

/** 预览实际使用的样式（random 时展示为 clipnote，直到生成回写） */
const previewStyleId = computed(() =>
  templateId.value === 'random' ? 'clipnote' : templateId.value
);

const templateFamily = computed(() => {
  const t = TEMPLATES.find((x) => x.id === previewStyleId.value);
  return t?.family || 'sheet';
});

const isSheetFamily = computed(
  () =>
    templateFamily.value === 'sheet' ||
    templateFamily.value === 'bubble' ||
    templateFamily.value === 'handbook'
);

const visibleTemplates = computed(() =>
  TEMPLATES.filter((t) => !t.needsRef || !!referenceImageUrl.value)
);

const customColors = computed(() => {
  const bg = normalizeHex(backgroundColor.value);
  if (!bg) return null;
  const fg = normalizeHex(textColor.value) || autoTextColor(bg);
  return { bg, fg };
});

const posterStyle = computed(() => {
  if (templateFamily.value === 'ref' && referenceImageUrl.value) {
    return {
      backgroundImage: `url(${referenceImageUrl.value})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
    };
  }
  if (!customColors.value) return undefined;
  if (isSheetFamily.value) {
    return {
      '--xhs-bg': customColors.value.bg,
      background: customColors.value.bg,
    };
  }
  return {
    '--xhs-bg': customColors.value.bg,
    '--xhs-fg': customColors.value.fg,
    background: customColors.value.bg,
    color: customColors.value.fg,
  };
});

const viewPages = computed(() => {
  if (content.value?.pages?.length) return content.value.pages;
  return DEMO.pages;
});

const cardStages = computed(() => {
  const coverTitle = content.value?.coverTitle || DEMO.coverTitle;
  const coverSubtitle = content.value?.coverSubtitle || DEMO.coverSubtitle;
  const pages = viewPages.value;
  const list = [{ key: 'cover', kind: 'cover', title: coverTitle, subtitle: coverSubtitle }];
  pages.forEach((p, i) => {
    list.push({
      key: `page-${i}`,
      kind: 'page',
      heading: p.heading || '',
      body: p.body || '',
      isLast: i === pages.length - 1,
    });
  });
  return list;
});

const activeStage = computed(() => cardStages.value[activeIdx.value] || cardStages.value[0]);

watch(content, () => {
  activeIdx.value = 0;
});

watch(backgroundColor, (v) => {
  if (normalizeHex(v) && !normalizeHex(textColor.value)) {
    textColor.value = autoTextColor(normalizeHex(v));
  }
});

function normalizeHex(raw) {
  if (!raw || typeof raw !== 'string') return '';
  let t = raw.trim();
  if (/^[0-9A-Fa-f]{6}$/.test(t)) t = `#${t}`;
  if (!/^#[0-9A-Fa-f]{6}$/.test(t)) return '';
  return t.toUpperCase();
}

function autoTextColor(bgHex) {
  const r = parseInt(bgHex.slice(1, 3), 16);
  const g = parseInt(bgHex.slice(3, 5), 16);
  const b = parseInt(bgHex.slice(5, 7), 16);
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return lum > 0.55 ? '#161616' : '#F5F5F5';
}

function showToast(msg, type = 'info') {
  toast.value = { show: true, msg, type };
  setTimeout(() => {
    toast.value.show = false;
  }, 3000);
}

function setCardRef(el, idx) {
  if (el) cardRefs.value[idx] = el;
}

function escapeHtml(s) {
  return String(s || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function formatBody(text) {
  return escapeHtml(text).replace(/\n/g, '<br/>');
}

/** 标题局部马克笔高亮（Text Note 风） */
function highlightTitle(title) {
  const t = String(title || '');
  if (t.length < 4) return escapeHtml(t);
  const hiLen = Math.min(4, Math.max(2, Math.floor(t.length / 3)));
  const start = Math.max(1, Math.floor((t.length - hiLen) / 2));
  const end = Math.min(t.length, start + hiLen);
  return (
    escapeHtml(t.slice(0, start)) +
    '<mark class="xhs-mark">' +
    escapeHtml(t.slice(start, end)) +
    '</mark>' +
    escapeHtml(t.slice(end))
  );
}

function stageLabel(stage, idx) {
  if (stage.kind === 'cover') return i18n.t('xhsCover');
  return i18n.t('xhsPageN', { n: idx });
}

function clearCustomColor() {
  backgroundColor.value = '';
  textColor.value = '';
}

function clearReference() {
  referenceImageUrl.value = '';
  referencePreview.value = '';
  if (templateId.value === 'customref') templateId.value = 'handbook';
  if (refInputRef.value) refInputRef.value.value = '';
}

async function onReferenceFile(e) {
  const file = e.target?.files?.[0];
  if (!file) return;
  if (!file.type?.startsWith('image/')) {
    showToast(i18n.t('error'), 'error');
    return;
  }
  if (!auth.isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: '/xhs-card' } });
    return;
  }
  refUploading.value = true;
  try {
    const url = await api.uploadBusiness(file);
    referenceImageUrl.value = url;
    referencePreview.value = URL.createObjectURL(file);
    if (!['handbook', 'customref'].includes(templateId.value)) {
      templateId.value = 'handbook';
    }
    showToast(i18n.t('xhsRefUploaded'), 'success');
  } catch (err) {
    showToast(err?.message || i18n.t('error'), 'error');
  } finally {
    refUploading.value = false;
  }
}

function pickPalette(hex) {
  backgroundColor.value = hex;
  textColor.value = autoTextColor(hex);
}

function onCustomColorInput(e) {
  const v = normalizeHex(e.target.value);
  if (v) pickPalette(v);
}

function startProgress() {
  stopProgress();
  progress.value = 6;
  progressLabel.value = i18n.t('xhsProgress');
  progressTimer = setInterval(() => {
    if (progress.value >= 90) return;
    const step = 2 + Math.random() * 7;
    progress.value = Math.min(90, progress.value + step);
  }, 380);
}

function stopProgress(done = false) {
  if (progressTimer) {
    clearInterval(progressTimer);
    progressTimer = null;
  }
  if (done) {
    progress.value = 100;
    setTimeout(() => {
      if (!generating.value) progress.value = 0;
    }, 600);
  } else {
    progress.value = 0;
  }
}

function formatHistoryTime(iso) {
  if (!iso) return '';
  try {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return String(iso).slice(0, 16);
    return d.toLocaleString(undefined, {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '';
  }
}

function historySwatch(item) {
  if (item.backgroundColor) return item.backgroundColor;
  const t = TEMPLATES.find((x) => x.id === item.templateStyle);
  return t?.swatch || '#333';
}

async function loadPricing() {
  try {
    const d = await api.getXhsCardPricing();
    if (d?.consumeCredits != null) consumeCredits.value = d.consumeCredits;
  } catch {
    /* ignore */
  }
}

async function loadHistory() {
  if (!auth.isLoggedIn()) {
    historyRecords.value = [];
    return;
  }
  historyLoading.value = true;
  try {
    const d = await api.listXhsCardHistory(1, 16);
    historyRecords.value = d?.records || [];
  } catch {
    historyRecords.value = [];
  } finally {
    historyLoading.value = false;
  }
}

async function loadHistoryItem(item) {
  if (!item?.taskId) return;
  try {
    const d = await api.getXhsCardTask(item.taskId);
    if (!d?.content) {
      showToast(i18n.t('xhsHistoryFailed'), 'error');
      return;
    }
    content.value = d.content;
    currentTaskId.value = d.taskId;
    cardRefs.value = [];
    if (d.topic) topic.value = d.topic;
    if (d.sellingPoints != null) sellingPoints.value = d.sellingPoints || '';
    if (d.audience != null) audience.value = d.audience || '';
    if (d.brandName) brandName.value = d.brandName;
    if (d.templateStyle) templateId.value = d.templateStyle;
    backgroundColor.value = d.backgroundColor || '';
    textColor.value = d.textColor || '';
    referenceImageUrl.value = d.referenceImageUrl || '';
    referencePreview.value = d.referenceImageUrl || '';
    await nextTick();
    showToast(i18n.t('xhsHistoryLoad'), 'success');
  } catch (e) {
    showToast(e?.message || i18n.t('error'), 'error');
  }
}

async function generate() {
  if (!canSubmit.value) return;
  if (!auth.isLoggedIn()) {
    router.push({ path: '/login', query: { redirect: '/xhs-card' } });
    return;
  }
  generating.value = true;
  startProgress();
  try {
    const bg = normalizeHex(backgroundColor.value) || undefined;
    const fg = bg ? normalizeHex(textColor.value) || autoTextColor(bg) : undefined;
    const data = await api.generateXhsCard({
      topic: topic.value.trim(),
      sellingPoints: sellingPoints.value.trim() || undefined,
      audience: audience.value.trim() || undefined,
      brandName: brandName.value.trim() || undefined,
      templateStyle: templateId.value,
      backgroundColor: bg,
      textColor: fg,
      referenceImageUrl: referenceImageUrl.value || undefined,
    });
    content.value = data?.content || null;
    currentTaskId.value = data?.taskId || null;
    cardRefs.value = [];
    if (data?.brandName) brandName.value = data.brandName;
    if (data?.templateStyle) templateId.value = data.templateStyle;
    if (data?.backgroundColor) backgroundColor.value = data.backgroundColor;
    if (data?.textColor) textColor.value = data.textColor;
    if (data?.referenceImageUrl) referenceImageUrl.value = data.referenceImageUrl;
    if (data?.consumeCredits != null) consumeCredits.value = data.consumeCredits;
    stopProgress(true);
    showToast(i18n.t('xhsGenerateOk'), 'success');
    loadHistory();
    try {
      const u = await api.getUserInfo();
      auth.setUser(u);
    } catch {
      /* ignore */
    }
  } catch (e) {
    stopProgress(false);
    showToast(e?.message || i18n.t('error'), 'error');
    loadHistory();
  } finally {
    generating.value = false;
  }
}

async function copyCaption() {
  const text = noteCaption.value;
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    showToast(i18n.t('xhsCopied'), 'success');
  } catch {
    showToast(i18n.t('error'), 'error');
  }
}

async function exportOne(el, filename) {
  if (!el) return;
  await document.fonts?.ready?.catch?.(() => {});
  const dataUrl = await toPng(el, {
    width: 1080,
    height: 1440,
    pixelRatio: 2,
    cacheBust: true,
    style: {
      transform: 'none',
      position: 'relative',
      left: '0',
      top: '0',
      margin: '0',
      opacity: '1',
      pointerEvents: 'auto',
    },
  });
  const a = document.createElement('a');
  a.href = dataUrl;
  a.download = filename;
  a.click();
}

async function exportAll() {
  if (!hasResult.value || exporting.value) return;
  exporting.value = true;
  try {
    await nextTick();
    const nodes = cardRefs.value.filter(Boolean);
    const styleTag = previewStyleId.value;
    for (let i = 0; i < nodes.length; i++) {
      await exportOne(nodes[i], `xhs-card-${i + 1}-${styleTag}.png`);
      await new Promise((r) => setTimeout(r, 260));
    }
    showToast(i18n.t('xhsExportOk', { n: nodes.length }), 'success');
  } catch (e) {
    showToast(e?.message || i18n.t('error'), 'error');
  } finally {
    exporting.value = false;
  }
}

onMounted(() => {
  loadPricing();
  loadHistory();
});

onUnmounted(() => {
  stopProgress(false);
});
</script>

<template>
  <div class="xhs" id="xhs-card-page">
    <div class="xhs__glow xhs__glow--a" aria-hidden="true" />
    <div class="xhs__glow xhs__glow--b" aria-hidden="true" />

    <header class="xhs-hero">
      <p class="xhs-hero__brand">FashTry · Notes</p>
      <h1 class="xhs-hero__title">{{ i18n.t('xhsCardTitle') }}</h1>
      <p class="xhs-hero__lead">{{ i18n.t('xhsCardLead') }}</p>
    </header>

    <div class="xhs-layout">
      <aside class="xhs-composer">
        <div class="xhs-composer__inner">
          <div class="xhs-field">
            <label>{{ i18n.t('xhsTopic') }}</label>
            <input
              v-model="topic"
              type="text"
              maxlength="120"
              :placeholder="i18n.t('xhsTopicPh')"
              @keydown.enter.prevent="generate"
            />
          </div>
          <div class="xhs-field">
            <label>{{ i18n.t('xhsSelling') }}</label>
            <textarea
              v-model="sellingPoints"
              rows="3"
              maxlength="400"
              :placeholder="i18n.t('xhsSellingPh')"
            />
          </div>
          <div class="xhs-field-row">
            <div class="xhs-field">
              <label>{{ i18n.t('xhsAudience') }}</label>
              <input
                v-model="audience"
                type="text"
                maxlength="120"
                :placeholder="i18n.t('xhsAudiencePh')"
              />
            </div>
            <div class="xhs-field">
              <label>{{ i18n.t('xhsBrand') }}</label>
              <input
                v-model="brandName"
                type="text"
                maxlength="40"
                :placeholder="i18n.t('xhsBrandPh')"
              />
            </div>
          </div>

          <div class="xhs-field">
            <label>{{ i18n.t('xhsTemplate') }}</label>
            <div class="xhs-swatches">
              <button
                v-for="t in visibleTemplates"
                :key="t.id"
                type="button"
                class="xhs-swatch"
                :class="{ 'is-on': templateId === t.id }"
                @click="templateId = t.id"
              >
                <span
                  class="xhs-swatch__chip"
                  :class="{ 'is-random': t.isRandom }"
                  :style="t.isRandom ? { backgroundImage: t.swatch } : { background: t.swatch, color: t.ink }"
                >
                  {{ t.isRandom ? '🎲' : 'Aa' }}
                </span>
                <span class="xhs-swatch__name">{{ i18n.t(t.labelKey) }}</span>
              </button>
            </div>
          </div>

          <div class="xhs-field">
            <label>{{ i18n.t('xhsRefUpload') }}</label>
            <div class="xhs-ref">
              <input
                ref="refInputRef"
                type="file"
                accept="image/*"
                hidden
                @change="onReferenceFile"
              />
              <button
                type="button"
                class="xhs-ref__btn"
                :disabled="refUploading"
                @click="refInputRef?.click()"
              >
                {{ refUploading ? i18n.t('loading') : i18n.t('xhsRefPick') }}
              </button>
              <button
                v-if="referenceImageUrl"
                type="button"
                class="xhs-ref__clear"
                @click="clearReference"
              >
                {{ i18n.t('xhsRefClear') }}
              </button>
            </div>
            <p class="xhs-palette__hint">{{ i18n.t('xhsRefHint') }}</p>
            <div v-if="referencePreview || referenceImageUrl" class="xhs-ref__preview">
              <img :src="referencePreview || referenceImageUrl" alt="" />
            </div>
          </div>

          <div class="xhs-field">
            <label>{{ i18n.t('xhsPalette') }}</label>
            <div class="xhs-palette">
              <button
                type="button"
                class="xhs-palette__item xhs-palette__item--default"
                :class="{ 'is-on': !backgroundColor }"
                :title="i18n.t('xhsPaletteDefault')"
                @click="clearCustomColor"
              >
                ∅
              </button>
              <button
                v-for="hex in PALETTE"
                :key="hex"
                type="button"
                class="xhs-palette__item"
                :class="{ 'is-on': normalizeHex(backgroundColor) === hex }"
                :style="{ background: hex }"
                :title="hex"
                @click="pickPalette(hex)"
              />
              <label class="xhs-palette__item xhs-palette__item--picker" :title="i18n.t('xhsPaletteCustom')">
                <input type="color" :value="normalizeHex(backgroundColor) || '#FFD60A'" @input="onCustomColorInput" />
                +
              </label>
            </div>
            <p v-if="backgroundColor" class="xhs-palette__hint">
              {{ i18n.t('xhsTextAuto') }} · {{ textColor || autoTextColor(normalizeHex(backgroundColor)) }}
            </p>
          </div>

          <p class="xhs-credits">{{ i18n.t('xhsCreditsLine', { n: consumeCredits }) }}</p>

          <div v-if="generating || progress > 0" class="xhs-progress">
            <div class="xhs-progress__bar">
              <div class="xhs-progress__fill" :style="{ width: `${progress}%` }" />
            </div>
            <div class="xhs-progress__meta">
              <span>{{ progressLabel || i18n.t('xhsProgress') }}</span>
              <span>{{ Math.round(progress) }}%</span>
            </div>
          </div>

          <div class="xhs-actions">
            <button
              type="button"
              class="btn btn-primary xhs-actions__gen"
              :disabled="!canSubmit"
              @click="generate"
            >
              <span v-if="generating" class="xhs-spin" aria-hidden="true" />
              {{ generating ? i18n.t('loading') : i18n.t('xhsGenerate') }}
            </button>
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="!hasResult || exporting"
              @click="exportAll"
            >
              {{ exporting ? i18n.t('xhsExporting') : i18n.t('xhsExport') }}
            </button>
          </div>
        </div>
      </aside>

      <section class="xhs-stage">
        <div class="xhs-stage__head">
          <div>
            <h2>{{ hasResult ? i18n.t('xhsPreviewTitle') : i18n.t('xhsDemoTitle') }}</h2>
            <p>{{ hasResult ? i18n.t('xhsPreviewHint') : i18n.t('xhsEmpty') }}</p>
          </div>
          <div v-if="cardStages.length" class="xhs-pager">
            <button
              type="button"
              class="xhs-pager__btn"
              :disabled="activeIdx <= 0"
              @click="activeIdx = Math.max(0, activeIdx - 1)"
            >
              ‹
            </button>
            <span>{{ activeIdx + 1 }} / {{ cardStages.length }}</span>
            <button
              type="button"
              class="xhs-pager__btn"
              :disabled="activeIdx >= cardStages.length - 1"
              @click="activeIdx = Math.min(cardStages.length - 1, activeIdx + 1)"
            >
              ›
            </button>
          </div>
        </div>

        <div class="xhs-focus" :class="{ 'is-demo': !hasResult, 'is-busy': generating }">
          <Transition name="xhs-flip" mode="out-in">
            <div :key="`${previewStyleId}-${activeIdx}-${hasResult}-${backgroundColor}`" class="xhs-focus__frame">
              <article
                class="xhs-poster"
                :class="[
                  `xhs-poster--${previewStyleId}`,
                  `xhs-poster--family-${templateFamily}`,
                  { 'is-custom': !!customColors },
                ]"
                :style="posterStyle"
              >
                <div class="xhs-sheet">
                  <header v-if="templateFamily === 'sheet'" class="xhs-sheet__head">
                    <span class="xhs-sheet__label">NOTE</span>
                    <span class="xhs-sheet__rule" />
                  </header>
                  <header v-else-if="templateFamily === 'bubble'" class="xhs-sheet__chrome">
                    <span class="xhs-sheet__dots" aria-hidden="true"><i /><i /><i /></span>
                    <span class="xhs-sheet__tag">Text Note</span>
                  </header>
                  <header v-else-if="templateFamily === 'handbook'" class="xhs-hand__binder" aria-hidden="true">
                    <i /><i /><i /><i /><i /><i /><i /><i />
                  </header>

                  <template v-if="activeStage?.kind === 'cover'">
                    <div class="xhs-poster__cover" :class="{ 'is-handbook': templateFamily === 'handbook' }">
                      <p v-if="templateFamily === 'flat'" class="xhs-poster__eyebrow">{{ i18n.t('xhsCoverBadge') }}</p>
                      <div v-if="templateFamily === 'handbook'" class="xhs-hand__titlewrap">
                        <span class="xhs-hand__hl" aria-hidden="true" />
                        <h3 class="xhs-poster__title xhs-hand__title">{{ activeStage.title }}</h3>
                      </div>
                      <h3
                        v-else
                        class="xhs-poster__title"
                        :class="{ 'is-marked': templateFamily === 'bubble' }"
                        v-html="templateFamily === 'bubble' ? highlightTitle(activeStage.title) : escapeHtml(activeStage.title)"
                      />
                      <p class="xhs-poster__subtitle">{{ activeStage.subtitle }}</p>
                      <div v-if="templateFamily === 'flat'" class="xhs-poster__ornament" aria-hidden="true" />
                    </div>
                  </template>
                  <template v-else-if="activeStage">
                    <div
                      class="xhs-poster__page"
                      :class="{ 'is-handbook': templateFamily === 'handbook' }"
                    >
                      <template v-if="templateFamily === 'handbook'">
                        <div class="xhs-hand__q">
                          <span class="xhs-hand__check" aria-hidden="true">✓</span>
                          <h3 class="xhs-hand__question">{{ activeStage.heading }}</h3>
                        </div>
                        <div class="xhs-hand__a">
                          <div class="xhs-poster__body" v-html="formatBody(activeStage.body)" />
                        </div>
                        <p v-if="activeStage.isLast && cta" class="xhs-poster__cta">{{ cta }}</p>
                      </template>
                      <template v-else>
                        <span v-if="templateFamily === 'flat'" class="xhs-poster__index">{{ String(activeIdx).padStart(2, '0') }}</span>
                        <h3 class="xhs-poster__heading">{{ activeStage.heading }}</h3>
                        <div class="xhs-poster__body" v-html="formatBody(activeStage.body)" />
                        <p v-if="activeStage.isLast && cta" class="xhs-poster__cta">{{ cta }}</p>
                      </template>
                    </div>
                  </template>

                  <footer class="xhs-poster__foot">
                    <span>{{ displayBrand }}</span>
                    <span class="xhs-poster__ratio">3:4</span>
                  </footer>
                </div>

                <span v-if="templateFamily === 'sheet'" class="xhs-deco xhs-deco--clip" aria-hidden="true" />
                <span
                  v-if="previewStyleId === 'clipnote' || previewStyleId === 'boardpink'"
                  class="xhs-deco xhs-deco--sticky"
                  aria-hidden="true"
                >😮?</span>
              </article>
            </div>
          </Transition>
        </div>

        <div class="xhs-strip xhs-scroll-x">
          <button
            v-for="(stage, idx) in cardStages"
            :key="stage.key"
            type="button"
            class="xhs-thumb"
            :class="{ 'is-on': idx === activeIdx }"
            @click="activeIdx = idx"
          >
            <span
              class="xhs-thumb__mini"
              :class="[`xhs-thumb__mini--${previewStyleId}`, { 'is-custom': !!customColors }]"
              :style="posterStyle"
            >
              <strong v-if="stage.kind === 'cover'">{{ stage.title }}</strong>
              <strong v-else>{{ stage.heading }}</strong>
            </span>
            <em>{{ stageLabel(stage, idx) }}</em>
          </button>
        </div>

        <section class="xhs-caption" :class="{ 'is-empty': !hasResult }">
          <div class="xhs-caption__bar">
            <div>
              <h3>{{ i18n.t('xhsNoteCaption') }}</h3>
              <p class="xhs-caption__hint">{{ i18n.t('xhsNoteCaptionHint') }}</p>
            </div>
            <button
              type="button"
              class="btn btn-secondary xhs-caption__copy"
              :disabled="!noteCaption"
              @click="copyCaption"
            >
              {{ i18n.t('copy') }}
            </button>
          </div>
          <pre v-if="noteCaption" class="xhs-caption__text">{{ noteCaption }}</pre>
          <p v-else class="xhs-caption__placeholder">{{ i18n.t('xhsNoteCaptionEmpty') }}</p>
        </section>

        <section class="xhs-history">
          <div class="xhs-history__bar">
            <h3>{{ i18n.t('xhsHistory') }}</h3>
            <button type="button" class="xhs-history__refresh" :disabled="historyLoading" @click="loadHistory">
              ↻
            </button>
          </div>
          <div v-if="!historyRecords.length" class="xhs-history__empty">
            {{ i18n.t('xhsHistoryEmpty') }}
          </div>
          <div v-else class="xhs-history__grid xhs-scroll-x">
            <button
              v-for="item in historyRecords"
              :key="item.taskId"
              type="button"
              class="xhs-history__card"
              :class="{
                'is-on': currentTaskId === item.taskId,
                'is-failed': item.status === 'FAILED',
              }"
              @click="loadHistoryItem(item)"
            >
              <span class="xhs-history__swatch" :style="{ background: historySwatch(item) }" />
              <span class="xhs-history__body">
                <strong>{{ item.coverTitle || item.topic }}</strong>
                <em>
                  {{ formatHistoryTime(item.createdAt) }}
                  <template v-if="item.status === 'FAILED'"> · {{ i18n.t('xhsHistoryFailed') }}</template>
                </em>
              </span>
            </button>
          </div>
        </section>
      </section>
    </div>

    <div class="xhs-export" aria-hidden="true">
      <div
        v-for="(stage, idx) in cardStages"
        :key="'ex-' + stage.key"
        :ref="(el) => setCardRef(el, idx)"
        class="xhs-card"
        :class="[
          `xhs-card--${previewStyleId}`,
          `xhs-card--family-${templateFamily}`,
          { 'is-custom': !!customColors },
        ]"
        :style="posterStyle"
      >
        <div class="xhs-sheet">
          <header v-if="templateFamily === 'sheet'" class="xhs-sheet__head">
            <span class="xhs-sheet__label">NOTE</span>
            <span class="xhs-sheet__rule" />
          </header>
          <header v-else-if="templateFamily === 'bubble'" class="xhs-sheet__chrome">
            <span class="xhs-sheet__dots" aria-hidden="true"><i /><i /><i /></span>
            <span class="xhs-sheet__tag">Text Note</span>
          </header>
          <header v-else-if="templateFamily === 'handbook'" class="xhs-hand__binder" aria-hidden="true">
            <i /><i /><i /><i /><i /><i /><i /><i />
          </header>

          <template v-if="stage.kind === 'cover'">
            <div class="xhs-card__cover" :class="{ 'is-handbook': templateFamily === 'handbook' }">
              <p v-if="templateFamily === 'flat'" class="xhs-card__eyebrow">{{ i18n.t('xhsCoverBadge') }}</p>
              <div v-if="templateFamily === 'handbook'" class="xhs-hand__titlewrap">
                <span class="xhs-hand__hl" aria-hidden="true" />
                <h2 class="xhs-card__title xhs-hand__title">{{ stage.title }}</h2>
              </div>
              <h2
                v-else
                class="xhs-card__title"
                :class="{ 'is-marked': templateFamily === 'bubble' }"
                v-html="templateFamily === 'bubble' ? highlightTitle(stage.title) : escapeHtml(stage.title)"
              />
              <p class="xhs-card__subtitle">{{ stage.subtitle }}</p>
              <div v-if="templateFamily === 'flat'" class="xhs-card__ornament" />
            </div>
          </template>
          <template v-else>
            <div class="xhs-card__page" :class="{ 'is-handbook': templateFamily === 'handbook' }">
              <template v-if="templateFamily === 'handbook'">
                <div class="xhs-hand__q">
                  <span class="xhs-hand__check" aria-hidden="true">✓</span>
                  <h3 class="xhs-hand__question">{{ stage.heading }}</h3>
                </div>
                <div class="xhs-hand__a">
                  <div class="xhs-card__body" v-html="formatBody(stage.body)" />
                </div>
                <p v-if="stage.isLast && cta" class="xhs-card__cta">{{ cta }}</p>
              </template>
              <template v-else>
                <span v-if="templateFamily === 'flat'" class="xhs-card__index">{{ String(idx).padStart(2, '0') }}</span>
                <h3 class="xhs-card__heading">{{ stage.heading }}</h3>
                <div class="xhs-card__body" v-html="formatBody(stage.body)" />
                <p v-if="stage.isLast && cta" class="xhs-card__cta">{{ cta }}</p>
              </template>
            </div>
          </template>

          <footer class="xhs-card__footer">
            <span class="xhs-card__logo">{{ displayBrand }}</span>
          </footer>
        </div>

        <span v-if="templateFamily === 'sheet'" class="xhs-deco xhs-deco--clip" aria-hidden="true" />
        <span
          v-if="previewStyleId === 'clipnote' || previewStyleId === 'boardpink'"
          class="xhs-deco xhs-deco--sticky"
          aria-hidden="true"
        >😮?</span>
      </div>
    </div>

    <Transition name="xhs-toast">
      <div v-if="toast.show" class="xhs-toast" :class="`xhs-toast--${toast.type}`">
        {{ toast.msg }}
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.xhs {
  position: relative;
  min-height: calc(100vh - 56px);
  max-width: 1280px;
  margin: 0 auto;
  padding: 1.75rem 1.25rem 3.5rem;
  overflow: clip;
}

.xhs__glow {
  position: absolute;
  pointer-events: none;
  border-radius: 50%;
  filter: blur(60px);
  z-index: 0;
}

.xhs__glow--a {
  width: 420px;
  height: 280px;
  top: -40px;
  right: 8%;
  background: rgba(255, 214, 10, 0.09);
  animation: xhs-drift 12s ease-in-out infinite alternate;
}

.xhs__glow--b {
  width: 360px;
  height: 300px;
  bottom: 10%;
  left: -40px;
  background: rgba(108, 76, 246, 0.1);
  animation: xhs-drift 14s ease-in-out infinite alternate-reverse;
}

@keyframes xhs-drift {
  from { transform: translate(0, 0); }
  to { transform: translate(24px, 18px); }
}

.xhs-hero,
.xhs-layout {
  position: relative;
  z-index: 1;
}

.xhs-hero { margin-bottom: 1.75rem; }

.xhs-hero__brand {
  margin: 0 0 0.55rem;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--accent-secondary);
}

.xhs-hero__title {
  margin: 0;
  font-size: clamp(1.85rem, 4.2vw, 2.65rem);
  font-weight: 800;
  letter-spacing: -0.035em;
  line-height: 1.15;
  color: var(--text);
}

.xhs-hero__lead {
  margin: 0.65rem 0 0;
  max-width: 34rem;
  font-size: 0.98rem;
  line-height: 1.55;
  color: var(--text-muted);
}

.xhs-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
}

.xhs-composer {
  position: sticky;
  top: 72px;
  border: 1px solid var(--border-subtle);
  border-radius: 18px;
  background: linear-gradient(165deg, rgba(16, 22, 34, 0.95), rgba(8, 10, 16, 0.92));
  backdrop-filter: blur(12px);
  max-height: calc(100vh - 88px);
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.28) transparent;
}

.xhs-composer::-webkit-scrollbar {
  width: 5px;
}

.xhs-composer::-webkit-scrollbar-track {
  background: transparent;
  margin: 10px 0;
}

.xhs-composer::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.22);
  border-radius: 999px;
}

.xhs-composer:hover::-webkit-scrollbar-thumb {
  background: rgba(108, 76, 246, 0.42);
}

.xhs-composer::-webkit-scrollbar-thumb:hover {
  background: rgba(108, 76, 246, 0.58);
}

.xhs-composer::-webkit-scrollbar-corner {
  background: transparent;
}

.xhs-composer__inner { padding: 1.15rem 1.15rem 1.25rem; }

.xhs-field { margin-bottom: 0.95rem; }

.xhs-field label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.xhs-field input,
.xhs-field textarea {
  width: 100%;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: rgba(5, 5, 8, 0.55);
  color: var(--text);
  font: inherit;
  padding: 0.65rem 0.75rem;
  transition: border-color var(--transition), box-shadow var(--transition);
}

.xhs-field textarea {
  min-height: 4.6rem;
  resize: vertical;
  line-height: 1.5;
}

.xhs-field input:focus,
.xhs-field textarea:focus {
  outline: none;
  border-color: rgba(108, 76, 246, 0.55);
  box-shadow: 0 0 0 3px rgba(108, 76, 246, 0.12);
}

.xhs-field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.65rem;
}

.xhs-swatches {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.4rem;
}

.xhs-swatch {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.25rem 0.45rem;
  border-radius: 12px;
  border: 1px solid var(--border-subtle);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: border-color var(--transition), transform var(--transition), background var(--transition);
}

.xhs-swatch:hover {
  border-color: var(--border);
  transform: translateY(-1px);
}

.xhs-swatch.is-on {
  border-color: rgba(108, 76, 246, 0.55);
  background: rgba(108, 76, 246, 0.08);
  color: var(--text);
}

.xhs-swatch__chip {
  width: 100%;
  aspect-ratio: 3 / 4;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 0.85rem;
  font-weight: 800;
  letter-spacing: -0.03em;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.08);
  background-size: cover;
}

.xhs-swatch__chip.is-random {
  color: #111;
  font-size: 1rem;
}

.xhs-swatch__name {
  font-size: 0.65rem;
  font-weight: 600;
  text-align: center;
  line-height: 1.2;
}

.xhs-palette {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.xhs-palette__item {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 2px solid transparent;
  padding: 0;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
  transition: transform var(--transition), border-color var(--transition);
}

.xhs-palette__item:hover { transform: scale(1.08); }

.xhs-palette__item.is-on {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px rgba(108, 76, 246, 0.25);
}

.xhs-palette__item--default {
  background: repeating-conic-gradient(#334 0% 25%, #111 0% 50%) 50% / 10px 10px;
  color: var(--text-muted);
  font-size: 0.75rem;
  display: grid;
  place-items: center;
}

.xhs-palette__item--picker {
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text);
  font-weight: 700;
  position: relative;
  overflow: hidden;
}

.xhs-palette__item--picker input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
  border: none;
  width: 100%;
  height: 100%;
}

.xhs-palette__hint {
  margin: 0.45rem 0 0;
  font-size: 0.72rem;
  color: var(--text-muted);
}

.xhs-credits {
  margin: 0.15rem 0 0.75rem;
  font-size: 0.78rem;
  color: var(--accent-secondary);
  line-height: 1.4;
}

.xhs-progress {
  margin-bottom: 0.85rem;
}

.xhs-progress__bar {
  height: 6px;
  border-radius: 99px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.xhs-progress__fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent), #ffd60a);
  transition: width 0.35s ease;
}

.xhs-progress__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 0.35rem;
  font-size: 0.72rem;
  color: var(--text-muted);
}

.xhs-actions {
  display: grid;
  gap: 0.55rem;
}

.xhs-actions__gen { min-height: 2.7rem; }

.xhs-spin {
  width: 0.9rem;
  height: 0.9rem;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: xhs-spin 0.7s linear infinite;
}

@keyframes xhs-spin {
  to { transform: rotate(360deg); }
}

.xhs-stage {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.xhs-stage__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.xhs-stage__head h2 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.xhs-stage__head p {
  margin: 0.25rem 0 0;
  font-size: 0.82rem;
  color: var(--text-muted);
}

.xhs-pager {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  flex-shrink: 0;
}

.xhs-pager__btn {
  width: 2rem;
  height: 2rem;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  background: rgba(12, 15, 25, 0.7);
  color: var(--text);
  font-size: 1.15rem;
  line-height: 1;
  cursor: pointer;
}

.xhs-pager__btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.xhs-focus {
  display: grid;
  place-items: center;
  min-height: 520px;
  padding: 1.5rem 1rem;
  border-radius: 22px;
  border: 1px solid var(--border-subtle);
  background:
    radial-gradient(ellipse 70% 55% at 50% 0%, rgba(255, 214, 10, 0.06), transparent 55%),
    linear-gradient(180deg, rgba(14, 18, 28, 0.9), rgba(8, 10, 16, 0.75));
  transition: opacity 0.25s ease;
}

.xhs-focus.is-busy { opacity: 0.72; }
.xhs-focus.is-demo .xhs-poster { opacity: 0.92; }
.xhs-focus__frame { width: min(100%, 300px); }


.xhs-poster {
  aspect-ratio: 3 / 4;
  width: 100%;
  border-radius: 14px;
  padding: 0.85rem;
  display: flex;
  flex-direction: column;
  box-shadow:
    0 24px 60px rgba(0, 0, 0, 0.45),
    0 0 0 1px rgba(255, 255, 255, 0.06);
  overflow: hidden;
  position: relative;
}

.xhs-poster.is-custom:not(.xhs-poster--family-sheet):not(.xhs-poster--family-bubble):not(.xhs-poster--family-handbook),
.xhs-card.is-custom:not(.xhs-card--family-sheet):not(.xhs-card--family-bubble):not(.xhs-card--family-handbook),
.xhs-thumb__mini.is-custom {
  background: var(--xhs-bg) !important;
  color: var(--xhs-fg) !important;
}

.xhs-poster.is-custom.xhs-poster--family-sheet,
.xhs-poster.is-custom.xhs-poster--family-bubble,
.xhs-poster.is-custom.xhs-poster--family-handbook,
.xhs-card.is-custom.xhs-card--family-sheet,
.xhs-card.is-custom.xhs-card--family-bubble,
.xhs-card.is-custom.xhs-card--family-handbook {
  background: var(--xhs-bg) !important;
}

.xhs-sheet {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  color: #171717;
  border-radius: 4px;
  padding: 1.05rem 1rem 0.9rem;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.14);
}

.xhs-poster--family-bubble .xhs-sheet,
.xhs-card--family-bubble .xhs-sheet {
  border-radius: 18px;
  padding: 1rem 1.05rem 1.05rem;
  box-shadow:
    0 14px 30px rgba(0, 0, 0, 0.12),
    0 0 0 1px rgba(0, 0, 0, 0.03);
}

.xhs-poster--family-bubble .xhs-sheet::before,
.xhs-card--family-bubble .xhs-sheet::before {
  content: '';
  position: absolute;
  inset: 6px -5px -6px 5px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.55);
  z-index: -1;
}

.xhs-poster--family-flat .xhs-sheet,
.xhs-card--family-flat .xhs-sheet {
  background: transparent;
  box-shadow: none;
  color: inherit;
  padding: 0.55rem 0.45rem;
  border-radius: 0;
}

.xhs-sheet__head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  margin-bottom: 0.75rem;
}

.xhs-sheet__label {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 0.78rem;
  letter-spacing: 0.22em;
  color: #e8c547;
  font-weight: 600;
}

.xhs-sheet__rule {
  display: block;
  width: 100%;
  height: 1px;
  background: #f0d978;
}

.xhs-sheet__chrome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.85rem;
}

.xhs-sheet__dots {
  display: inline-flex;
  gap: 0.28rem;
}

.xhs-sheet__dots i {
  width: 0.42rem;
  height: 0.42rem;
  border-radius: 50%;
  background: #e8a317;
  display: block;
}

.xhs-sheet__tag {
  font-size: 0.68rem;
  color: #e8a317;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.xhs-deco--clip {
  position: absolute;
  top: 0.35rem;
  left: 1.15rem;
  width: 1.05rem;
  height: 2.1rem;
  z-index: 3;
  border: 2.5px solid #b8b8b8;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255,255,255,0.55), rgba(200,200,200,0.15));
  box-shadow: 1px 2px 4px rgba(0,0,0,0.18);
  transform: rotate(-8deg);
}

.xhs-deco--clip::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 28%;
  width: 55%;
  height: 42%;
  transform: translateX(-50%);
  border: 2px solid #b8b8b8;
  border-radius: 999px;
  border-bottom-color: transparent;
}

.xhs-deco--sticky {
  position: absolute;
  right: 0.55rem;
  bottom: 0.55rem;
  z-index: 3;
  width: 2.6rem;
  height: 2.6rem;
  background: #ffe566;
  color: #333;
  font-size: 0.85rem;
  display: grid;
  place-items: center;
  box-shadow: 2px 3px 8px rgba(0,0,0,0.16);
  transform: rotate(8deg);
  border-radius: 2px;
  line-height: 1;
}

.xhs-deco--sticky::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  border-width: 0 0 10px 10px;
  border-style: solid;
  border-color: transparent transparent #f0c400 #ffe566;
  filter: brightness(0.92);
}

.xhs-poster__cover,
.xhs-poster__page,
.xhs-card__cover,
.xhs-card__page {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 0;
}

.xhs-poster__eyebrow,
.xhs-card__eyebrow {
  margin: 0 0 0.85rem;
  font-size: 0.68rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  opacity: 0.65;
  font-weight: 700;
}

.xhs-poster__title {
  margin: 0;
  font-size: clamp(1.35rem, 3.4vw, 1.75rem);
  line-height: 1.28;
  font-weight: 850;
  letter-spacing: -0.03em;
  word-break: break-word;
}

.xhs-poster__title.is-marked,
.xhs-card__title.is-marked {
  text-align: center;
}

.xhs-poster__title :deep(.xhs-mark),
.xhs-card__title :deep(.xhs-mark) {
  background: rgba(125, 211, 252, 0.55);
  color: inherit;
  padding: 0 0.08em;
  border-radius: 0.12em;
  font-style: normal;
}

.xhs-poster__subtitle {
  margin: 0.85rem 0 0;
  font-size: 0.9rem;
  line-height: 1.45;
  opacity: 0.82;
}

.xhs-poster--family-bubble .xhs-poster__subtitle,
.xhs-card--family-bubble .xhs-card__subtitle {
  text-align: center;
}

.xhs-poster__ornament,
.xhs-card__ornament {
  margin-top: 1.4rem;
  width: 3rem;
  height: 4px;
  border-radius: 99px;
  background: currentColor;
  opacity: 0.28;
}

.xhs-poster__index,
.xhs-card__index {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  opacity: 0.45;
  margin-bottom: 0.55rem;
}

.xhs-poster__heading {
  margin: 0 0 0.85rem;
  font-size: 1.2rem;
  line-height: 1.25;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.xhs-poster__body {
  font-size: 0.86rem;
  line-height: 1.65;
  word-break: break-word;
}

.xhs-poster__cta {
  margin-top: 1rem;
  font-size: 0.9rem;
  font-weight: 750;
}

.xhs-poster__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 0.75rem;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  opacity: 0.55;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.xhs-poster--family-flat .xhs-poster__foot {
  border-top-color: currentColor;
}

.xhs-poster--family-bubble .xhs-poster__foot,
.xhs-card--family-bubble .xhs-card__footer {
  border-top-color: #f0d29a;
  justify-content: center;
  opacity: 0.7;
}

.xhs-poster--family-bubble .xhs-poster__ratio {
  display: none;
}

.xhs-poster__ratio { opacity: 0.7; font-weight: 600; }

.xhs-poster--clipnote, .xhs-card--clipnote { background: #f5c518; }
.xhs-poster--textnote, .xhs-card--textnote { background: #f0a202; }
.xhs-poster--handbook, .xhs-card--handbook {
  background-color: #d6ebff;
  background-image:
    linear-gradient(rgba(255,255,255,0.55) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.55) 1px, transparent 1px);
  background-size: 18px 18px;
}
.xhs-poster--boardmint, .xhs-card--boardmint { background: #6fcf97; }
.xhs-poster--boardpink, .xhs-card--boardpink { background: #ff8fab; }
.xhs-poster--boardsky, .xhs-card--boardsky { background: #6ec1de; }
.xhs-poster--papercraft, .xhs-card--papercraft { background: #efe6d5; }
.xhs-poster--customref, .xhs-card--customref {
  background-color: #334155;
}
.xhs-poster--dark, .xhs-card--dark {
  background: linear-gradient(165deg, #1a1f2b 0%, #0c0e14 100%);
  color: #f3f4f6;
  padding: 1.35rem 1.25rem 1.1rem;
}

.xhs-poster--family-handbook .xhs-sheet,
.xhs-card--family-handbook .xhs-sheet {
  border-radius: 6px 6px 18px 18px;
  clip-path: polygon(
    0% 0%, 100% 0%, 100% 92%,
    96% 96%, 92% 93%, 88% 97%, 84% 94%, 80% 98%, 76% 94%,
    72% 97%, 68% 93%, 64% 98%, 60% 94%, 56% 97%, 52% 93%,
    48% 98%, 44% 94%, 40% 97%, 36% 93%, 32% 98%, 28% 94%,
    24% 97%, 20% 93%, 16% 98%, 12% 94%, 8% 97%, 4% 93%,
    0% 96%
  );
  padding-top: 1.35rem;
  box-shadow: 0 12px 28px rgba(40, 90, 150, 0.18);
}

.xhs-poster--family-ref .xhs-sheet,
.xhs-card--family-ref .xhs-sheet {
  background: rgba(255, 255, 255, 0.92);
  border-radius: 14px;
  margin: 0.35rem;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.25);
}

.xhs-hand__binder {
  display: flex;
  justify-content: center;
  gap: 0.35rem;
  margin: -0.15rem 0 0.75rem;
}

.xhs-hand__binder i {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  background: #4d8fd6;
  box-shadow: inset 0 0 0 1px rgba(255,255,255,0.45);
  display: block;
}

.xhs-hand__titlewrap {
  position: relative;
  display: inline-block;
  align-self: center;
  max-width: 100%;
  margin: 0 auto;
}

.xhs-hand__hl {
  position: absolute;
  left: -4%;
  right: -4%;
  top: 42%;
  height: 48%;
  background: #ffe14a;
  border-radius: 4px;
  z-index: 0;
  transform: rotate(-1.5deg);
}

.xhs-hand__title {
  position: relative;
  z-index: 1;
  text-align: center;
  color: #fff;
  -webkit-text-stroke: 2.5px #2f6fb5;
  paint-order: stroke fill;
  font-weight: 900;
}

.xhs-poster__cover.is-handbook,
.xhs-card__cover.is-handbook {
  align-items: center;
  text-align: center;
}

.xhs-hand__q {
  display: flex;
  align-items: flex-start;
  gap: 0.45rem;
  margin-bottom: 0.65rem;
}

.xhs-hand__check {
  flex-shrink: 0;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 50%;
  background: #ffd400;
  color: #fff;
  font-size: 0.72rem;
  font-weight: 900;
  display: grid;
  place-items: center;
  margin-top: 0.12rem;
  box-shadow: 0 1px 0 #e6b800;
}

.xhs-hand__question {
  margin: 0;
  font-size: 1.02rem;
  line-height: 1.35;
  font-weight: 850;
  color: #f4f9ff;
  -webkit-text-stroke: 1.4px #2f6fb5;
  paint-order: stroke fill;
}

.xhs-hand__a {
  background: #eef3f8;
  border-radius: 10px;
  padding: 0.7rem 0.75rem;
}

.xhs-hand__a .xhs-poster__body,
.xhs-hand__a .xhs-card__body {
  font-size: 0.82rem;
  line-height: 1.55;
}

.xhs-hand__a .xhs-poster__body :deep(br:last-of-type),
.xhs-hand__a .xhs-card__body :deep(br:last-of-type) {
  display: none;
}

.xhs-poster__page.is-handbook .xhs-poster__cta,
.xhs-card__page.is-handbook .xhs-card__cta {
  text-align: center;
  color: #2f6fb5;
}

.xhs-ref {
  display: flex;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.xhs-ref__btn,
.xhs-ref__clear {
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: rgba(5, 5, 8, 0.55);
  color: var(--text);
  padding: 0.5rem 0.75rem;
  font-size: 0.8rem;
  cursor: pointer;
}

.xhs-ref__btn:hover {
  border-color: var(--accent);
  color: var(--accent-secondary);
}

.xhs-ref__clear {
  color: var(--text-muted);
}

.xhs-ref__preview {
  margin-top: 0.55rem;
  width: 88px;
  height: 118px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-subtle);
}

.xhs-ref__preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.xhs-poster--boardmint .xhs-sheet__label,
.xhs-card--boardmint .xhs-sheet__label { color: #7bc896; }
.xhs-poster--boardmint .xhs-sheet__rule,
.xhs-card--boardmint .xhs-sheet__rule { background: #b7e4c7; }
.xhs-poster--boardpink .xhs-sheet__label,
.xhs-card--boardpink .xhs-sheet__label { color: #ffb3c6; }
.xhs-poster--boardpink .xhs-sheet__rule,
.xhs-card--boardpink .xhs-sheet__rule { background: #ffc2d4; }
.xhs-poster--boardsky .xhs-sheet__label,
.xhs-card--boardsky .xhs-sheet__label { color: #8ecae6; }
.xhs-poster--boardsky .xhs-sheet__rule,
.xhs-card--boardsky .xhs-sheet__rule { background: #a8daf0; }
.xhs-poster--papercraft .xhs-sheet__label,
.xhs-card--papercraft .xhs-sheet__label { color: #d4c4a8; }
.xhs-poster--papercraft .xhs-sheet__rule,
.xhs-card--papercraft .xhs-sheet__rule { background: #e0d2b8; }

.xhs-poster--dark .xhs-poster__eyebrow,
.xhs-card--dark .xhs-card__eyebrow {
  color: #7dd3fc;
  opacity: 1;
}

.xhs-strip {
  display: flex;
  gap: 0.65rem;
  overflow-x: auto;
  padding: 0.15rem 0.1rem 0.35rem;
}

.xhs-scroll-x {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.xhs-scroll-x::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.xhs-thumb {
  flex: 0 0 auto;
  width: 92px;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  text-align: left;
  color: var(--text-muted);
}

.xhs-thumb__mini {
  display: block;
  width: 92px;
  height: 122px;
  border-radius: 10px;
  padding: 0.55rem 0.5rem;
  overflow: hidden;
  border: 2px solid transparent;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.25);
  transition: border-color var(--transition), transform var(--transition);
}

.xhs-thumb__mini strong {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 0.62rem;
  line-height: 1.3;
  font-weight: 750;
}

.xhs-thumb__mini--clipnote { background: #f5c518; color: #111; }
.xhs-thumb__mini--textnote { background: #f0a202; color: #111; }
.xhs-thumb__mini--handbook { background: #cfe8ff; color: #1a3a6b; }
.xhs-thumb__mini--boardmint { background: #6fcf97; color: #111; }
.xhs-thumb__mini--boardpink { background: #ff8fab; color: #111; }
.xhs-thumb__mini--boardsky { background: #6ec1de; color: #111; }
.xhs-thumb__mini--papercraft { background: #efe6d5; color: #1a1a1a; }
.xhs-thumb__mini--customref { background: #334155; color: #fff; }
.xhs-thumb__mini--dark { background: #151820; color: #f3f4f6; }

.xhs-thumb em {
  display: block;
  margin-top: 0.35rem;
  font-style: normal;
  font-size: 0.7rem;
  text-align: center;
}

.xhs-thumb.is-on .xhs-thumb__mini {
  border-color: var(--accent);
  transform: translateY(-2px);
}

.xhs-thumb.is-on em { color: var(--accent-secondary); }

.xhs-caption {
  border-radius: 14px;
  border: 1px solid rgba(108, 76, 246, 0.28);
  background: linear-gradient(165deg, rgba(108, 76, 246, 0.08), rgba(12, 15, 25, 0.85));
  padding: 1rem 1.1rem 1.15rem;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.03);
}

.xhs-caption.is-empty {
  border-color: var(--border-subtle);
  background: rgba(12, 15, 25, 0.55);
}

.xhs-caption__bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.7rem;
}

.xhs-caption__bar h3 {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 750;
  letter-spacing: -0.01em;
}

.xhs-caption__hint {
  margin: 0.25rem 0 0;
  font-size: 0.72rem;
  color: var(--accent-secondary);
  line-height: 1.4;
}

.xhs-caption__copy {
  padding: 0.4rem 0.85rem;
  font-size: 0.8rem;
  flex-shrink: 0;
}

.xhs-caption__text {
  margin: 0;
  padding: 0.85rem 0.95rem;
  border-radius: 10px;
  background: rgba(5, 5, 8, 0.45);
  border: 1px solid var(--border-subtle);
  font-family: inherit;
  font-size: 0.92rem;
  line-height: 1.7;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 280px;
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.28) transparent;
}

.xhs-caption__text::-webkit-scrollbar {
  width: 5px;
}

.xhs-caption__text::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.28);
  border-radius: 999px;
}

.xhs-caption__placeholder {
  margin: 0;
  font-size: 0.86rem;
  color: var(--text-muted);
  line-height: 1.5;
}

.xhs-history {
  border-radius: 14px;
  border: 1px solid var(--border-subtle);
  background: rgba(12, 15, 25, 0.55);
  padding: 0.9rem 1rem 1rem;
}

.xhs-history__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.65rem;
}

.xhs-history__bar h3 {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 700;
}

.xhs-history__refresh {
  width: 1.8rem;
  height: 1.8rem;
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.xhs-history__empty {
  font-size: 0.82rem;
  color: var(--text-muted);
  padding: 0.5rem 0;
}

.xhs-history__grid {
  display: flex;
  gap: 0.55rem;
  overflow-x: auto;
  padding-bottom: 0.2rem;
}

.xhs-history__card {
  flex: 0 0 auto;
  width: 180px;
  display: flex;
  gap: 0.55rem;
  align-items: center;
  text-align: left;
  padding: 0.55rem;
  border-radius: 12px;
  border: 1px solid var(--border-subtle);
  background: rgba(5, 5, 8, 0.45);
  color: var(--text);
  cursor: pointer;
  transition: border-color var(--transition), background var(--transition);
}

.xhs-history__card:hover,
.xhs-history__card.is-on {
  border-color: rgba(108, 76, 246, 0.5);
  background: rgba(108, 76, 246, 0.08);
}

.xhs-history__card.is-failed {
  opacity: 0.65;
}

.xhs-history__swatch {
  width: 36px;
  height: 48px;
  border-radius: 6px;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.1);
}

.xhs-history__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.xhs-history__body strong {
  font-size: 0.78rem;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.xhs-history__body em {
  font-style: normal;
  font-size: 0.68rem;
  color: var(--text-muted);
}

.xhs-export {
  position: fixed;
  left: -10000px;
  top: 0;
  pointer-events: none;
  opacity: 0;
}


.xhs-card {
  width: 1080px;
  height: 1440px;
  box-sizing: border-box;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 56px;
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans SC', sans-serif;
}

.xhs-card .xhs-sheet {
  padding: 72px 64px 56px;
  border-radius: 12px;
}

.xhs-card--family-bubble .xhs-sheet {
  border-radius: 48px;
  padding: 64px 68px 60px;
}

.xhs-card--family-flat .xhs-sheet {
  padding: 40px 28px;
}

.xhs-card .xhs-sheet__label {
  font-size: 42px;
  letter-spacing: 0.22em;
}

.xhs-card .xhs-sheet__rule {
  height: 3px;
  margin-top: 8px;
}

.xhs-card .xhs-sheet__head {
  margin-bottom: 48px;
  gap: 18px;
}

.xhs-card .xhs-sheet__chrome {
  margin-bottom: 56px;
}

.xhs-card .xhs-sheet__dots {
  gap: 14px;
}

.xhs-card .xhs-sheet__dots i {
  width: 18px;
  height: 18px;
}

.xhs-card .xhs-sheet__tag {
  font-size: 32px;
}

.xhs-card .xhs-deco--clip {
  top: 28px;
  left: 92px;
  width: 52px;
  height: 110px;
  border-width: 8px;
}

.xhs-card .xhs-deco--clip::after {
  border-width: 7px;
}

.xhs-card .xhs-deco--sticky {
  right: 48px;
  bottom: 48px;
  width: 140px;
  height: 140px;
  font-size: 52px;
  border-radius: 6px;
}

.xhs-card .xhs-deco--sticky::after {
  border-width: 0 0 28px 28px;
}

.xhs-card__eyebrow {
  font-size: 34px;
  letter-spacing: 0.18em;
  margin: 0 0 40px;
  opacity: 0.62;
  font-weight: 700;
  text-transform: uppercase;
}

.xhs-card__title {
  font-size: 88px;
  line-height: 1.25;
  font-weight: 850;
  margin: 0;
  letter-spacing: -0.03em;
  word-break: break-word;
}

.xhs-card__subtitle {
  margin: 40px 0 0;
  font-size: 40px;
  line-height: 1.45;
  opacity: 0.82;
}

.xhs-card__ornament {
  margin-top: 64px;
  width: 120px;
  height: 10px;
  border-radius: 99px;
  background: currentColor;
  opacity: 0.28;
}

.xhs-card__index {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.14em;
  opacity: 0.4;
  margin-bottom: 28px;
}

.xhs-card__heading {
  font-size: 64px;
  line-height: 1.22;
  font-weight: 850;
  margin: 0 0 44px;
  letter-spacing: -0.02em;
}

.xhs-card__body {
  font-size: 38px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.xhs-card__cta {
  margin-top: 48px;
  font-size: 38px;
  font-weight: 750;
}

.xhs-card__footer {
  margin-top: auto;
  padding-top: 36px;
  border-top: 2px solid rgba(0, 0, 0, 0.1);
  opacity: 0.5;
}

.xhs-card--family-flat .xhs-card__footer {
  border-top-color: currentColor;
}

.xhs-card__logo {
  font-size: 30px;
  font-weight: 750;
  letter-spacing: 0.04em;
}

.xhs-card .xhs-hand__binder {
  gap: 18px;
  margin-bottom: 36px;
}

.xhs-card .xhs-hand__binder i {
  width: 28px;
  height: 28px;
}

.xhs-card .xhs-hand__check {
  width: 56px;
  height: 56px;
  font-size: 30px;
  margin-top: 8px;
}

.xhs-card .xhs-hand__question {
  font-size: 48px;
  line-height: 1.3;
}

.xhs-card .xhs-hand__a {
  border-radius: 24px;
  padding: 36px 40px;
}

.xhs-card .xhs-hand__a .xhs-card__body {
  font-size: 36px;
}

.xhs-card .xhs-hand__title {
  font-size: 78px;
  -webkit-text-stroke: 10px #2f6fb5;
}

.xhs-card .xhs-hand__hl {
  height: 46%;
  top: 44%;
}

.xhs-card--family-handbook .xhs-sheet {
  padding: 64px 56px 80px;
}

.xhs-card--family-ref .xhs-sheet {
  margin: 48px;
  padding: 64px 56px;
  border-radius: 28px;
}

.xhs-toast {
  position: fixed;
  left: 50%;
  bottom: 1.6rem;
  transform: translateX(-50%);
  z-index: 80;
  padding: 0.7rem 1.15rem;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.92);
  border: 1px solid var(--border-subtle);
  color: #fff;
  font-size: 0.86rem;
  backdrop-filter: blur(10px);
}

.xhs-toast--success { border-color: rgba(16, 185, 129, 0.45); }
.xhs-toast--error { border-color: rgba(244, 63, 94, 0.5); }

.xhs-flip-enter-active,
.xhs-flip-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.xhs-flip-enter-from { opacity: 0; transform: translateY(10px) scale(0.98); }
.xhs-flip-leave-to { opacity: 0; transform: translateY(-8px) scale(0.99); }

.xhs-toast-enter-active,
.xhs-toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.xhs-toast-enter-from,
.xhs-toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}

@media (max-width: 960px) {
  .xhs-layout { grid-template-columns: 1fr; }
  .xhs-composer { position: static; max-height: none; }
  .xhs-focus { min-height: 440px; }
  .xhs-swatches { grid-template-columns: repeat(4, 1fr); }
}

@media (max-width: 560px) {
  .xhs { padding: 1.25rem 1rem 2.5rem; }
  .xhs-field-row { grid-template-columns: 1fr; }
  .xhs-stage__head { flex-direction: column; align-items: flex-start; }
  .xhs-focus__frame { width: min(100%, 260px); }
  .xhs-swatches { grid-template-columns: repeat(3, 1fr); }
}
</style>
