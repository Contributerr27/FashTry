<script setup>
import { ref } from 'vue';
import Icon from './Icon.vue';
import FavButton from './FavButton.vue';
import { useT } from '../../lib/useT.js';
import { downloadUrl } from '../../lib/download.js';
import { toast } from '../../lib/toast.js';

const props = defineProps({
  url: { type: String, default: '' },
  thumbUrl: { type: String, default: '' },
  label: { type: String, default: '' },
  /** CSS aspect-ratio，如 '3 / 4' */
  aspect: { type: String, default: '3 / 4' },
  /** done | loading | failed */
  status: { type: String, default: 'done' },
  statusText: { type: String, default: '' },
  sourceType: { type: String, default: '' },
  sourceTaskId: { type: [String, Number], default: '' },
  filename: { type: String, default: '' },
  regenerable: { type: Boolean, default: false },
  regenerating: { type: Boolean, default: false },
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false },
  contain: { type: Boolean, default: false },
  hideFav: { type: Boolean, default: false },
});
const emit = defineEmits(['zoom', 'regenerate', 'toggle-select']);
const { t } = useT();
const downloading = ref(false);

async function onDownload(e) {
  e.stopPropagation();
  if (!props.url || downloading.value) return;
  downloading.value = true;
  try {
    const r = await downloadUrl(props.url, props.filename || 'fashtry-' + Date.now());
    toast(r === 'downloaded' ? t('toastDownloadSuccess') : t('toastOpenInNewTabSave'), r === 'downloaded' ? 'success' : 'info');
  } finally {
    downloading.value = false;
  }
}

function onClick() {
  if (props.selectable) emit('toggle-select');
  else if (props.status === 'done' && props.url) emit('zoom');
}
</script>

<template>
  <div
    class="rt"
    :class="{
      'rt--loading': status === 'loading' || regenerating,
      'rt--failed': status === 'failed',
      'rt--selectable': selectable,
      'rt--selected': selected,
    }"
    @click="onClick"
  >
    <div class="rt__media" :style="{ aspectRatio: aspect }">
      <img
        v-if="status === 'done' && (thumbUrl || url)"
        :src="thumbUrl || url"
        alt=""
        loading="lazy"
        :class="{ 'rt__img--contain': contain }"
      />
      <div v-else-if="status === 'failed'" class="rt__state">
        <Icon name="alert" :size="22" />
        <span>{{ statusText || t('historyStatusFailed') }}</span>
      </div>
      <div v-else-if="status === 'loading'" class="rt__state">
        <span class="spinner"></span>
        <span v-if="statusText">{{ statusText }}</span>
      </div>
      <div v-if="regenerating && status === 'done'" class="rt__veil"><span class="spinner"></span></div>

      <span v-if="selectable" class="rt__check" aria-hidden="true">
        <Icon v-if="selected" name="check" :size="14" :stroke-width="2.5" />
      </span>

      <div v-if="status === 'done' && url && !selectable" class="rt__actions">
        <FavButton
          v-if="!hideFav"
          :url="url"
          :thumb-url="thumbUrl"
          :source-type="sourceType"
          :source-task-id="sourceTaskId"
          :title="label"
        />
        <button type="button" class="rt__act" :title="t('uiZoom')" @click.stop="emit('zoom')">
          <Icon name="zoomIn" :size="16" />
        </button>
        <button type="button" class="rt__act" :title="t('uiDownload')" :disabled="downloading" @click="onDownload">
          <Icon name="download" :size="16" />
        </button>
        <button
          v-if="regenerable"
          type="button"
          class="rt__act"
          :title="t('uiRegenerate')"
          :disabled="regenerating"
          @click.stop="emit('regenerate')"
        >
          <Icon name="refresh" :size="16" />
        </button>
      </div>
      <slot name="overlay" />
    </div>
    <div v-if="label || $slots.meta" class="rt__label">
      <span>{{ label }}</span>
      <slot name="meta" />
    </div>
  </div>
</template>

<style scoped>
.rt {
  position: relative;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--panel-2);
  border: 1px solid var(--line);
  cursor: zoom-in;
  transition: border-color var(--transition), transform var(--transition);
}
.rt:hover {
  border-color: var(--line-strong);
}
.rt--loading,
.rt--failed {
  cursor: default;
}
.rt--selectable {
  cursor: pointer;
}
.rt--selected {
  border-color: var(--brand);
  box-shadow: 0 0 0 1px var(--brand);
}
.rt__media {
  position: relative;
  width: 100%;
  background: var(--bg-elev);
  overflow: hidden;
}
.rt__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.35s ease;
}
.rt__media img.rt__img--contain {
  object-fit: contain;
}
.rt:not(.rt--selectable):hover .rt__media img {
  transform: scale(1.02);
}
.rt__state {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px;
  font-size: 0.75rem;
  color: var(--text-3);
  text-align: center;
}
.rt--failed .rt__state {
  color: var(--error);
}
.rt--loading .rt__media {
  background: linear-gradient(110deg, var(--bg-elev) 30%, var(--panel-2) 50%, var(--bg-elev) 70%);
  background-size: 200% 100%;
  animation: rt-shimmer 1.4s linear infinite;
}
@keyframes rt-shimmer {
  to {
    background-position: -200% 0;
  }
}
.rt__veil {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(11, 13, 16, 0.55);
}
.rt__actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  opacity: 0;
  transform: translateX(4px);
  transition: opacity var(--transition), transform var(--transition);
}
.rt:hover .rt__actions,
.rt__actions:focus-within {
  opacity: 1;
  transform: none;
}
.rt__act {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: none;
  background: rgba(11, 13, 16, 0.72);
  color: #fff;
  backdrop-filter: blur(6px);
  cursor: pointer;
  transition: background var(--transition), transform var(--transition);
}
.rt__act:hover {
  background: rgba(11, 13, 16, 0.92);
  transform: scale(1.06);
}
.rt__act:disabled {
  opacity: 0.6;
  cursor: default;
}
.rt__check {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: 1.5px solid rgba(255, 255, 255, 0.7);
  background: rgba(11, 13, 16, 0.5);
  color: var(--brand-ink);
}
.rt--selected .rt__check {
  background: var(--brand);
  border-color: var(--brand);
}
.rt__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 10px;
  font-size: 0.75rem;
  color: var(--text-2);
  border-top: 1px solid var(--line);
  min-height: 34px;
}
.rt__label span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (hover: none) {
  .rt__actions {
    opacity: 1;
    transform: none;
  }
}
</style>
