<script setup>
import { computed, onMounted, ref } from 'vue';
import Icon from '../ui/Icon.vue';
import { api } from '../../lib/api.js';
import { useT } from '../../lib/useT.js';

const props = defineProps({
  selected: { type: Array, default: () => [] },
  pageSize: { type: Number, default: 6 },
  cols: { type: Number, default: 3 },
});
const emit = defineEmits(['pick']);
const { t } = useT();

const scene = ref('');
const gender = ref('');
const page = ref(1);
const totalPages = ref(0);
const items = ref([]);
const loading = ref(false);
const failed = ref(false);

const sceneOptions = computed(() => [
  { value: '', label: t('filterAllScene') },
  { value: 'wedding', label: t('sceneWedding') },
  { value: 'workplace', label: t('sceneWorkplace') },
  { value: 'campus', label: t('sceneCampus') },
  { value: 'gu_feng', label: t('sceneGuFeng') },
  { value: 'trendy', label: t('sceneTrendy') },
  { value: 'western', label: t('sceneWestern') },
]);
const genderOptions = computed(() => [
  { value: '', label: t('filterAllGender') },
  { value: 'female', label: t('female') },
  { value: 'male', label: t('male') },
  { value: 'unisex', label: t('unisex') },
]);

async function load(reset) {
  if (reset) page.value = 1;
  loading.value = true;
  failed.value = false;
  try {
    const params = { page: page.value, size: props.pageSize };
    if (scene.value) params.sceneCategory = scene.value;
    if (gender.value) params.gender = gender.value;
    const data = await api.getPublicGarments(params);
    const total = Number(data?.total) || 0;
    const ps = Number(data?.pageSize) || props.pageSize;
    totalPages.value = total === 0 ? 0 : Math.ceil(total / ps);
    items.value = data?.records || [];
  } catch {
    items.value = [];
    totalPages.value = 0;
    failed.value = true;
  } finally {
    loading.value = false;
  }
}

function go(delta) {
  const n = page.value + delta;
  if (n < 1 || n > totalPages.value) return;
  page.value = n;
  load(false);
}

onMounted(() => load(true));
</script>

<template>
  <div class="glib">
    <div class="glib__filters">
      <select v-model="scene" @change="load(true)">
        <option v-for="o in sceneOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
      <select v-model="gender" @change="load(true)">
        <option v-for="o in genderOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </div>
    <div
      class="glib__grid"
      :class="{ 'is-loading': loading }"
      :style="{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }"
    >
      <button
        v-for="g in items"
        :key="g.id || g.imageUrl"
        type="button"
        class="glib__item"
        :class="{ on: selected.includes(g.imageUrl) }"
        :title="g.name"
        @click="emit('pick', g)"
      >
        <img :src="g.thumbUrl || g.imageUrl" :alt="g.name || ''" loading="lazy" />
        <span v-if="selected.includes(g.imageUrl)" class="glib__check"><Icon name="check" :size="11" :stroke-width="3" /></span>
      </button>
      <p v-if="!loading && !items.length" class="glib__empty">{{ failed ? t('loadGarmentsFail') : t('noGarments') }}</p>
    </div>
    <div v-if="totalPages > 1" class="glib__pager">
      <button type="button" class="btn btn-ghost btn-sm btn-icon" :disabled="page <= 1" @click="go(-1)">
        <Icon name="chevronLeft" :size="16" />
      </button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button type="button" class="btn btn-ghost btn-sm btn-icon" :disabled="page >= totalPages" @click="go(1)">
        <Icon name="chevronRight" :size="16" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.glib__filters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  margin-bottom: 10px;
}
.glib__filters select {
  height: 32px;
  min-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  font-size: 0.78rem;
}
.glib__grid {
  display: grid;
  gap: 6px;
  min-height: 80px;
  transition: opacity 0.15s;
}
.glib__grid.is-loading {
  opacity: 0.5;
}
.glib__item {
  position: relative;
  aspect-ratio: 1;
  padding: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  border: 1.5px solid var(--line);
  background: var(--panel-2);
  cursor: pointer;
}
.glib__item:hover {
  border-color: var(--line-strong);
}
.glib__item.on {
  border-color: var(--brand);
}
.glib__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.glib__check {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--brand);
  color: var(--brand-ink);
}
.glib__empty {
  grid-column: 1 / -1;
  padding: 16px 0;
  text-align: center;
  font-size: 0.75rem;
  color: var(--text-3);
}
.glib__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 0.75rem;
  color: var(--text-3);
}
</style>
