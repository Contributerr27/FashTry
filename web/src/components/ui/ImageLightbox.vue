<script setup>
import { computed, onUnmounted, watch, ref } from 'vue';
import Icon from './Icon.vue';
import FavButton from './FavButton.vue';
import { useT } from '../../lib/useT.js';
import { downloadUrl } from '../../lib/download.js';
import { toast } from '../../lib/toast.js';

/**
 * items: [{ url, label?, sourceType?, sourceTaskId?, thumbUrl? }]
 * v-model:index — 打开的下标，-1 为关闭
 */
const props = defineProps({
  items: { type: Array, default: () => [] },
  index: { type: Number, default: -1 },
  filenamePrefix: { type: String, default: 'fashtry' },
});
const emit = defineEmits(['update:index']);
const { t } = useT();

const open = computed(() => props.index >= 0 && props.index < props.items.length);
const cur = computed(() => (open.value ? props.items[props.index] : null));
const downloading = ref(false);

function close() {
  emit('update:index', -1);
}
function prev() {
  if (props.items.length < 2) return;
  emit('update:index', (props.index - 1 + props.items.length) % props.items.length);
}
function next() {
  if (props.items.length < 2) return;
  emit('update:index', (props.index + 1) % props.items.length);
}
async function download() {
  if (!cur.value?.url || downloading.value) return;
  downloading.value = true;
  try {
    const r = await downloadUrl(cur.value.url, `${props.filenamePrefix}-${props.index + 1}`);
    toast(r === 'downloaded' ? t('toastDownloadSuccess') : t('toastOpenInNewTabSave'), r === 'downloaded' ? 'success' : 'info');
  } finally {
    downloading.value = false;
  }
}

function onKey(e) {
  if (!open.value) return;
  if (e.key === 'Escape') close();
  else if (e.key === 'ArrowLeft') prev();
  else if (e.key === 'ArrowRight') next();
}

watch(open, (v) => {
  if (v) {
    window.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';
  } else {
    window.removeEventListener('keydown', onKey);
    document.body.style.overflow = '';
  }
});
onUnmounted(() => {
  window.removeEventListener('keydown', onKey);
  if (open.value) document.body.style.overflow = '';
});
</script>

<template>
  <Teleport to="body">
    <Transition name="lb-fade">
      <div v-if="open" class="lb" role="dialog" aria-modal="true" @click.self="close">
        <div class="lb__bar">
          <span class="lb__count">
            <template v-if="items.length > 1">{{ index + 1 }} / {{ items.length }}</template>
            <template v-if="cur?.label"> · {{ cur.label }}</template>
          </span>
          <div class="lb__actions">
            <FavButton
              :url="cur.url"
              :thumb-url="cur.thumbUrl"
              :source-type="cur.sourceType"
              :source-task-id="cur.sourceTaskId"
              :title="cur.label"
              variant="lightbox"
            />
            <button type="button" class="lb__btn" :disabled="downloading" @click="download">
              <Icon name="download" :size="18" /><span>{{ t('uiDownload') }}</span>
            </button>
            <button type="button" class="lb__btn lb__btn--icon" :aria-label="t('close')" @click="close">
              <Icon name="x" :size="20" />
            </button>
          </div>
        </div>

        <button v-if="items.length > 1" type="button" class="lb__nav lb__nav--prev" :aria-label="t('uiPrev')" @click="prev">
          <Icon name="chevronLeft" :size="26" />
        </button>
        <img :key="cur.url" class="lb__img" :src="cur.url" alt="" @click.stop />
        <button v-if="items.length > 1" type="button" class="lb__nav lb__nav--next" :aria-label="t('uiNext')" @click="next">
          <Icon name="chevronRight" :size="26" />
        </button>

        <div v-if="items.length > 1" class="lb__thumbs">
          <button
            v-for="(it, i) in items"
            :key="i + it.url"
            type="button"
            class="lb__thumb"
            :class="{ 'lb__thumb--active': i === index }"
            @click="emit('update:index', i)"
          >
            <img :src="it.thumbUrl || it.url" alt="" loading="lazy" />
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.lb {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px 72px 96px;
  background: rgba(5, 6, 8, 0.94);
}
.lb__bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 22px;
}
.lb__count {
  font-size: 0.85rem;
  color: var(--text-2);
}
.lb__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lb__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}
.lb__btn:hover {
  background: rgba(255, 255, 255, 0.12);
}
.lb__btn--icon {
  width: 36px;
  padding: 0;
  justify-content: center;
}
.lb__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-3);
  animation: lb-in 0.18s ease;
}
@keyframes lb-in {
  from {
    opacity: 0.4;
    transform: scale(0.985);
  }
}
.lb__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  cursor: pointer;
}
.lb__nav:hover {
  background: rgba(255, 255, 255, 0.14);
}
.lb__nav--prev {
  left: 14px;
}
.lb__nav--next {
  right: 14px;
}
.lb__thumbs {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  max-width: calc(100vw - 32px);
  overflow-x: auto;
  padding: 4px;
}
.lb__thumb {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  padding: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid transparent;
  background: var(--panel);
  opacity: 0.55;
  cursor: pointer;
}
.lb__thumb--active {
  border-color: var(--brand);
  opacity: 1;
}
.lb__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.lb-fade-enter-active,
.lb-fade-leave-active {
  transition: opacity 0.16s ease;
}
.lb-fade-enter-from,
.lb-fade-leave-to {
  opacity: 0;
}
@media (max-width: 768px) {
  .lb {
    padding: 60px 8px 84px;
  }
  .lb__nav {
    display: none;
  }
  .lb__btn span {
    display: none;
  }
  .lb__btn {
    width: 36px;
    padding: 0;
    justify-content: center;
  }
}
</style>
