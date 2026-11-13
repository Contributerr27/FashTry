<script setup>
import { ref, computed } from 'vue';
import Icon from './Icon.vue';
import { useT } from '../../lib/useT.js';

/**
 * 单图：传 preview，emit('file', File)
 * 多图：multiple + items[{ id, preview }]，emit('files', File[]) / emit('remove', id)
 */
const props = defineProps({
  preview: { type: String, default: '' },
  items: { type: Array, default: () => [] },
  multiple: { type: Boolean, default: false },
  max: { type: Number, default: 1 },
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
  icon: { type: String, default: 'upload' },
  aspect: { type: String, default: '4 / 5' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  numbered: { type: Boolean, default: false },
});
const emit = defineEmits(['file', 'files', 'remove', 'clear']);
const { t } = useT();

const input = ref(null);
const drag = ref(false);
const room = computed(() => (props.multiple ? Math.max(0, props.max - props.items.length) : 1));

function pick() {
  if (props.disabled || props.loading || room.value <= 0) return;
  input.value?.click();
}
function accept(list) {
  const imgs = Array.from(list || []).filter((f) => f.type?.startsWith('image/'));
  if (!imgs.length) return;
  if (props.multiple) emit('files', imgs.slice(0, room.value));
  else emit('file', imgs[0]);
}
function onInput(e) {
  accept(e.target.files);
  e.target.value = '';
}
function onDrop(e) {
  drag.value = false;
  if (props.disabled || props.loading) return;
  accept(e.dataTransfer?.files);
}
</script>

<template>
  <div class="ub" :class="{ 'ub--multi': multiple, 'ub--compact': compact }">
    <!-- 单图 -->
    <div
      v-if="!multiple"
      class="ub__zone"
      :class="{ 'ub__zone--drag': drag, 'ub__zone--filled': !!preview, 'ub__zone--disabled': disabled }"
      :style="{ aspectRatio: aspect }"
      role="button"
      tabindex="0"
      @click="pick"
      @keydown.enter.prevent="pick"
      @dragenter.prevent="drag = true"
      @dragover.prevent="drag = true"
      @dragleave.prevent="drag = false"
      @drop.prevent="onDrop"
    >
      <template v-if="preview">
        <img class="ub__preview" :src="preview" alt="" />
        <div class="ub__over">
          <button type="button" class="ub__chip" @click.stop="pick">
            <Icon name="refresh" :size="14" />{{ t('uiReplace') }}
          </button>
          <button type="button" class="ub__chip" @click.stop="emit('clear')">
            <Icon name="trash" :size="14" />{{ t('uiRemove') }}
          </button>
        </div>
      </template>
      <div v-else class="ub__empty">
        <span class="ub__icon"><Icon :name="icon" :size="22" /></span>
        <strong>{{ title || t('uiUploadHint') }}</strong>
        <span v-if="hint">{{ hint }}</span>
        <small>{{ t('uiUploadFormats') }}</small>
      </div>
      <div v-if="loading" class="ub__loading"><span class="spinner"></span></div>
    </div>

    <!-- 多图 -->
    <template v-else>
      <div class="ub__grid">
        <div v-for="(it, idx) in items" :key="it.id" class="ub__thumb">
          <img :src="it.preview" alt="" />
          <span v-if="numbered" class="ub__num">{{ idx + 1 }}</span>
          <span v-if="it.loading" class="ub__thumb-busy"><span class="spinner" style="width: 16px; height: 16px"></span></span>
          <button type="button" class="ub__x" :aria-label="t('uiRemove')" @click="emit('remove', it.id)">
            <Icon name="x" :size="12" :stroke-width="2.5" />
          </button>
        </div>
        <div
          v-if="room > 0"
          class="ub__add"
          :class="{ 'ub__zone--drag': drag, 'ub__add--wide': !items.length }"
          role="button"
          tabindex="0"
          @click="pick"
          @keydown.enter.prevent="pick"
          @dragenter.prevent="drag = true"
          @dragover.prevent="drag = true"
          @dragleave.prevent="drag = false"
          @drop.prevent="onDrop"
        >
          <template v-if="!items.length">
            <span class="ub__icon"><Icon :name="icon" :size="22" /></span>
            <strong>{{ title || t('uiUploadHint') }}</strong>
            <span v-if="hint">{{ hint }}</span>
            <small>{{ t('uiUploadFormats') }}</small>
          </template>
          <template v-else>
            <Icon name="plus" :size="20" />
            <small>{{ items.length }}/{{ max }}</small>
          </template>
        </div>
      </div>
      <div v-if="loading" class="ub__loading ub__loading--multi"><span class="spinner"></span></div>
    </template>

    <input ref="input" type="file" accept="image/*" :multiple="multiple" hidden @change="onInput" />
  </div>
</template>

<style scoped>
.ub {
  position: relative;
}
.ub__zone,
.ub__add {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  border: 1.5px dashed var(--line-strong);
  background: var(--bg-elev);
  cursor: pointer;
  overflow: hidden;
  transition: border-color var(--transition), background var(--transition);
  outline: none;
}
.ub__zone:hover,
.ub__add:hover,
.ub__zone:focus-visible,
.ub__zone--drag {
  border-color: var(--brand);
  background: var(--brand-soft);
}
.ub__zone--filled {
  border-style: solid;
  border-color: var(--line);
  background: var(--panel-2);
}
.ub__zone--filled:hover {
  background: var(--panel-2);
}
.ub__zone--disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ub__empty,
.ub__add {
  flex-direction: column;
  gap: 4px;
  padding: 16px;
  text-align: center;
  color: var(--text-3);
  font-size: 0.75rem;
}
.ub__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}
.ub__empty strong,
.ub__empty span,
.ub__empty small {
  display: block;
  max-width: 100%;
  line-height: 1.45;
}
.ub__empty strong,
.ub__add strong {
  color: var(--text);
  font-size: 0.875rem;
  font-weight: 600;
}
.ub__empty span,
.ub__add span:not(.ub__icon) {
  color: var(--text-2);
}
.ub__empty small,
.ub__add small {
  color: var(--text-3);
  font-size: 0.7rem;
}
.ub__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  margin-bottom: 6px;
  border-radius: 12px;
  background: var(--panel-2);
  color: var(--brand);
}
.ub__preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.ub__over {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.65));
  opacity: 0;
  transition: opacity var(--transition);
}
.ub__zone:hover .ub__over {
  opacity: 1;
}
.ub__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: none;
  background: rgba(11, 13, 16, 0.8);
  color: #fff;
  font: inherit;
  font-size: 0.75rem;
  cursor: pointer;
}
.ub__chip:hover {
  background: rgba(11, 13, 16, 0.95);
}
.ub__loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(11, 13, 16, 0.6);
  border-radius: var(--radius);
}

.ub__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.ub__thumb {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1px solid var(--line);
  background: var(--panel-2);
}
.ub__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.ub__num {
  position: absolute;
  left: 4px;
  top: 4px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  font-size: 0.68rem;
  font-weight: 700;
  background: rgba(11, 13, 16, 0.78);
  color: #fff;
}
.ub__thumb-busy {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(11, 13, 16, 0.55);
}
.ub__x {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: none;
  background: rgba(11, 13, 16, 0.8);
  color: #fff;
  cursor: pointer;
}
.ub__x:hover {
  background: var(--error);
}
.ub__add {
  aspect-ratio: 1;
  border-radius: var(--radius-sm);
  padding: 8px;
}
.ub__add--wide {
  grid-column: 1 / -1;
  aspect-ratio: auto;
  min-height: 168px;
  border-radius: var(--radius);
}
.ub--compact .ub__add--wide {
  min-height: 132px;
}
@media (hover: none) {
  .ub__over {
    opacity: 1;
  }
}
</style>
