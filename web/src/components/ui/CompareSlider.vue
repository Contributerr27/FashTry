<script setup>
import { ref } from 'vue';
import Icon from './Icon.vue';
import { useT } from '../../lib/useT.js';

defineProps({
  before: { type: String, required: true },
  after: { type: String, required: true },
  aspect: { type: String, default: '' },
});
const { t } = useT();

const pos = ref(50);
const root = ref(null);
let dragging = false;

function setFromEvent(e) {
  const el = root.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const x = (e.touches?.[0]?.clientX ?? e.clientX) - rect.left;
  pos.value = Math.max(0, Math.min(100, (x / rect.width) * 100));
}
function down(e) {
  dragging = true;
  setFromEvent(e);
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', up, { once: true });
}
function move(e) {
  if (dragging) setFromEvent(e);
}
function up() {
  dragging = false;
  window.removeEventListener('pointermove', move);
}
function onKey(e) {
  if (e.key === 'ArrowLeft') pos.value = Math.max(0, pos.value - 4);
  else if (e.key === 'ArrowRight') pos.value = Math.min(100, pos.value + 4);
}
</script>

<template>
  <div
    ref="root"
    class="cmp"
    :style="aspect ? { aspectRatio: aspect } : null"
    tabindex="0"
    role="slider"
    :aria-valuenow="Math.round(pos)"
    aria-valuemin="0"
    aria-valuemax="100"
    @pointerdown.prevent="down"
    @keydown="onKey"
  >
    <img class="cmp__img" :src="after" alt="" draggable="false" />
    <div class="cmp__before" :style="{ clipPath: `inset(0 ${100 - pos}% 0 0)` }">
      <img class="cmp__img" :src="before" alt="" draggable="false" />
    </div>
    <span class="cmp__tag cmp__tag--l">{{ t('uiBefore') }}</span>
    <span class="cmp__tag cmp__tag--r">{{ t('uiAfter') }}</span>
    <div class="cmp__handle" :style="{ left: pos + '%' }">
      <span class="cmp__knob"><Icon name="compare" :size="16" /></span>
    </div>
  </div>
</template>

<style scoped>
.cmp {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: var(--radius);
  background: var(--bg-elev);
  cursor: ew-resize;
  user-select: none;
  touch-action: none;
  outline: none;
}
.cmp__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
}
.cmp__before {
  position: absolute;
  inset: 0;
}
.cmp__handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  margin-left: -1px;
  background: var(--brand);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.25);
  pointer-events: none;
}
.cmp__knob {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--brand);
  color: var(--brand-ink);
  box-shadow: var(--shadow-2);
}
.cmp__tag {
  position: absolute;
  top: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 600;
  background: rgba(11, 13, 16, 0.7);
  color: #fff;
  pointer-events: none;
}
.cmp__tag--l {
  left: 12px;
}
.cmp__tag--r {
  right: 12px;
  background: var(--brand);
  color: var(--brand-ink);
}
</style>
