<script setup>
import { computed, watch } from 'vue';
import Icon from './Icon.vue';
import { useT } from '../../lib/useT.js';
import { ensureFavChecked, isFav, isFavBusy, toggleFav } from '../../lib/favorites.js';
import { toast } from '../../lib/toast.js';

const props = defineProps({
  url: { type: String, default: '' },
  thumbUrl: { type: String, default: '' },
  sourceType: { type: String, default: '' },
  sourceTaskId: { type: [String, Number], default: '' },
  title: { type: String, default: '' },
  /** tile：悬浮圆钮；lightbox：灯箱胶囊钮 */
  variant: { type: String, default: 'tile' },
});
const { t } = useT();

watch(
  () => props.url,
  (u) => u && ensureFavChecked([u]),
  { immediate: true }
);

const on = computed(() => isFav(props.url));
const busy = computed(() => isFavBusy(props.url));

async function onClick(e) {
  e.stopPropagation();
  try {
    const v = await toggleFav({
      url: props.url,
      thumbUrl: props.thumbUrl,
      sourceType: props.sourceType,
      sourceTaskId: props.sourceTaskId,
      title: props.title,
    });
    toast(v ? t('uiFavAdded') : t('uiFavRemoved'), 'success', 1600);
  } catch (err) {
    toast(err?.message || t('uiFavFail'), 'error');
  }
}
</script>

<template>
  <button
    type="button"
    class="fav"
    :class="['fav--' + variant, { 'fav--on': on }]"
    :disabled="busy || !url"
    :title="on ? t('uiUnfav') : t('uiFav')"
    :aria-pressed="on"
    @click="onClick"
  >
    <Icon name="heart" :filled="on" :size="variant === 'lightbox' ? 18 : 16" />
    <span v-if="variant === 'lightbox'">{{ on ? t('uiUnfav') : t('uiFav') }}</span>
  </button>
</template>

<style scoped>
.fav {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  cursor: pointer;
  font: inherit;
  transition: background var(--transition), color var(--transition), transform var(--transition);
}
.fav:disabled {
  cursor: default;
  opacity: 0.7;
}
.fav--tile {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(11, 13, 16, 0.72);
  color: #fff;
  backdrop-filter: blur(6px);
}
.fav--tile:hover {
  background: rgba(11, 13, 16, 0.9);
  transform: scale(1.06);
}
.fav--tile.fav--on {
  color: #fb7185;
}
.fav--toolbar {
  width: 30px;
  height: 30px;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-2);
}
.fav--toolbar:hover {
  background: var(--panel-2);
  color: var(--text);
}
.fav--toolbar.fav--on {
  color: #fb7185;
}
.fav--lightbox {
  height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  font-size: 0.85rem;
}
.fav--lightbox:hover {
  background: rgba(255, 255, 255, 0.12);
}
.fav--lightbox.fav--on {
  color: #fb7185;
  border-color: rgba(251, 113, 133, 0.4);
}
@media (max-width: 768px) {
  .fav--lightbox span {
    display: none;
  }
  .fav--lightbox {
    width: 36px;
    padding: 0;
  }
}
</style>
