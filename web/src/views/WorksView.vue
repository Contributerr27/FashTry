<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import Icon from '../components/ui/Icon.vue';
import ChipGroup from '../components/ui/ChipGroup.vue';
import ResultTile from '../components/ui/ResultTile.vue';
import ImageLightbox from '../components/ui/ImageLightbox.vue';
import { api } from '../lib/api.js';
import { useT } from '../lib/useT.js';
import { userState } from '../lib/userStore.js';
import { WORK_KINDS, flattenWorks, kindLabel, kindPath, formatDay } from '../lib/works.js';
import { markFav } from '../lib/favorites.js';
import { downloadZip, stamp } from '../lib/download.js';
import { toast } from '../lib/toast.js';

const { t } = useT();
const route = useRoute();
const router = useRouter();

const PAGE_SIZE = 30;
const validKinds = WORK_KINDS.map((k) => k.value);

const tab = ref(route.query.tab === 'fav' ? 'fav' : 'all');
const kind = ref(validKinds.includes(String(route.query.kind)) ? String(route.query.kind) : 'all');

const items = ref([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const error = ref('');

const selecting = ref(false);
const selected = ref(new Set());
const zipping = ref(false);
const zipProgress = ref('');
const lbIndex = ref(-1);

let reqSeq = 0;

const tabOptions = computed(() => [
  { value: 'all', label: t('uiWorksAll') },
  { value: 'fav', label: t('uiWorksFav') },
]);
const kindOptions = computed(() => WORK_KINDS.map((k) => ({ value: k.value, label: t(k.labelKey) })));

const hasMore = computed(() => {
  if (tab.value === 'all' && kind.value === 'all') return false;
  return items.value.length < total.value && page.value * PAGE_SIZE < total.value;
});

const lbItems = computed(() =>
  items.value.map((w) => ({
    url: w.url,
    thumbUrl: w.thumb,
    label: w.label,
    sourceType: w.kind,
    sourceTaskId: w.taskId,
  }))
);

const selectedList = computed(() => items.value.filter((w) => selected.value.has(w.key)));

function favToWork(f) {
  return {
    key: 'fav-' + f.id,
    favId: f.id,
    kind: f.sourceType || '',
    taskId: f.sourceTaskId || '',
    createdAt: f.createdAt ? Date.parse(String(f.createdAt).replace(' ', 'T')) : 0,
    url: f.imageUrl,
    thumb: f.thumbUrl || f.imageUrl,
    label: f.title || (f.sourceType ? kindLabel(f.sourceType) : ''),
  };
}

async function load(reset = true) {
  if (!userState.loggedIn) {
    items.value = [];
    total.value = 0;
    return;
  }
  const seq = ++reqSeq;
  if (reset) {
    page.value = 1;
    selected.value = new Set();
  }
  loading.value = true;
  error.value = '';
  try {
    if (tab.value === 'fav') {
      const data = await api.listFavorites(page.value, PAGE_SIZE, kind.value === 'all' ? '' : kind.value);
      if (seq !== reqSeq) return;
      const rows = (data?.records || []).map(favToWork);
      markFav(rows.map((r) => r.url));
      items.value = reset ? rows : [...items.value, ...rows];
      total.value = Number(data?.total) || rows.length;
    } else {
      const size = kind.value === 'all' ? 50 : PAGE_SIZE;
      const data = await api.getUnifiedTaskHistory(kind.value, page.value, size);
      if (seq !== reqSeq) return;
      const rows = flattenWorks(data?.records || []);
      items.value = reset ? rows : [...items.value, ...rows];
      total.value = Number(data?.total) || 0;
    }
  } catch (e) {
    if (seq !== reqSeq) return;
    error.value = e?.message || t('loadFail');
    if (reset) items.value = [];
  } finally {
    if (seq === reqSeq) loading.value = false;
  }
}

function loadMore() {
  if (loading.value || !hasMore.value) return;
  page.value += 1;
  load(false);
}

function toggleSelect(w) {
  const s = new Set(selected.value);
  if (s.has(w.key)) s.delete(w.key);
  else s.add(w.key);
  selected.value = s;
}
function selectAll() {
  if (selected.value.size === items.value.length) selected.value = new Set();
  else selected.value = new Set(items.value.map((w) => w.key));
}
function exitSelect() {
  selecting.value = false;
  selected.value = new Set();
}

async function zip(list) {
  if (!list.length) {
    toast(t('uiZipNothing'), 'info');
    return;
  }
  zipping.value = true;
  zipProgress.value = t('uiZipping', { done: 0, total: list.length });
  try {
    const named = list.map((w, i) => ({
      url: w.url,
      name: `${String(i + 1).padStart(3, '0')}-${w.kind || 'image'}-${(w.label || '').replace(/\s+/g, '')}`,
    }));
    const r = await downloadZip(named, `fashtry-works-${stamp()}`, (done, all) => {
      zipProgress.value = t('uiZipping', { done, total: all });
    });
    if (r.ok === 0) toast(t('uiZipFail'), 'error');
    else if (r.failed) toast(t('uiZipPartial', { n: r.failed }), 'info', 4000);
    else toast(t('uiZipDone', { n: r.ok }), 'success');
    if (r.ok) exitSelect();
  } catch {
    toast(t('uiZipFail'), 'error');
  } finally {
    zipping.value = false;
    zipProgress.value = '';
  }
}

function onFavChanged(e) {
  if (tab.value !== 'fav' || e.detail?.value !== false) return;
  const url = e.detail.url;
  items.value = items.value.filter((w) => w.url !== url);
  total.value = Math.max(0, total.value - 1);
}

watch([tab, kind], () => {
  router.replace({
    query: {
      ...(tab.value === 'fav' ? { tab: 'fav' } : {}),
      ...(kind.value !== 'all' ? { kind: kind.value } : {}),
    },
  });
  exitSelect();
  load(true);
});
watch(
  () => userState.loggedIn,
  () => load(true)
);

onMounted(() => {
  load(true);
  window.addEventListener('fashtry:favorites-changed', onFavChanged);
});
onUnmounted(() => window.removeEventListener('fashtry:favorites-changed', onFavChanged));
</script>

<template>
  <div class="page works">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ t('uiWorksTitle') }}</h1>
        <p class="page-lead">{{ t('uiWorksLead') }}</p>
      </div>
      <ChipGroup v-if="userState.loggedIn" v-model="tab" :options="tabOptions" variant="seg" class="works__tabs" />
    </header>

    <div v-if="!userState.loggedIn" class="works-empty">
      <span class="works-empty__icon"><Icon name="images" :size="26" /></span>
      <strong>{{ t('uiNeedLogin') }}</strong>
      <RouterLink :to="'/login?return=' + encodeURIComponent('/works')" class="btn btn-primary">
        {{ t('uiLoginRegister') }}
      </RouterLink>
    </div>

    <template v-else>
      <div class="works__bar">
        <ChipGroup v-model="kind" :options="kindOptions" class="works__kinds" />
        <div class="works__actions">
          <template v-if="!selecting">
            <button type="button" class="btn btn-secondary btn-sm" :disabled="!items.length" @click="selecting = true">
              <Icon name="check" :size="15" />{{ t('uiSelect') }}
            </button>
            <button
              type="button"
              class="btn btn-secondary btn-sm"
              :disabled="!items.length || zipping"
              @click="zip(items)"
            >
              <Icon name="archive" :size="15" />{{ t('uiDownloadAllZip') }}
            </button>
          </template>
          <template v-else>
            <span class="works__count">{{ t('uiSelectedN', { n: selected.size }) }}</span>
            <button type="button" class="btn btn-ghost btn-sm" @click="selectAll">{{ t('uiSelectAll') }}</button>
            <button type="button" class="btn btn-ghost btn-sm" @click="exitSelect">{{ t('uiCancel') }}</button>
            <button
              type="button"
              class="btn btn-primary btn-sm"
              :disabled="!selected.size || zipping"
              @click="zip(selectedList)"
            >
              <Icon name="archive" :size="15" />{{ t('uiDownloadZip') }}
            </button>
          </template>
        </div>
      </div>

      <div v-if="zipping" class="wb-progress works__zip">
        <span class="spinner" style="width: 16px; height: 16px"></span>
        <span>{{ zipProgress }}</span>
      </div>

      <div v-if="loading && !items.length" class="works__grid">
        <div v-for="i in 12" :key="i" class="works__sk skeleton"></div>
      </div>
      <p v-else-if="error && !items.length" class="empty-hint">{{ error }}</p>
      <div v-else-if="!items.length" class="works-empty">
        <span class="works-empty__icon"><Icon :name="tab === 'fav' ? 'heart' : 'sparkles'" :size="26" /></span>
        <strong>{{ tab === 'fav' ? t('uiFavEmpty') : t('uiWorksEmpty') }}</strong>
        <RouterLink v-if="tab !== 'fav'" :to="kind === 'all' ? '/tryon' : kindPath(kind)" class="btn btn-primary">
          {{ t('uiOpenTool') }}
        </RouterLink>
      </div>
      <div v-else class="works__grid">
        <ResultTile
          v-for="(w, i) in items"
          :key="w.key"
          :url="w.url"
          :thumb-url="w.thumb"
          :label="w.kind ? kindLabel(w.kind) : w.label"
          :source-type="w.kind"
          :source-task-id="w.taskId"
          :filename="`fashtry-${w.kind || 'image'}-${w.taskId || i}`"
          :selectable="selecting"
          :selected="selected.has(w.key)"
          @zoom="lbIndex = i"
          @toggle-select="toggleSelect(w)"
        >
          <template #meta>
            <small class="works__date">{{ formatDay(w.createdAt) }}</small>
          </template>
        </ResultTile>
      </div>

      <div v-if="items.length" class="works__more">
        <button v-if="hasMore" type="button" class="btn btn-secondary" :disabled="loading" @click="loadMore">
          <span v-if="loading" class="spinner" style="width: 14px; height: 14px"></span>
          {{ t('uiLoadMore') }}
        </button>
        <span v-else class="muted">{{ t('uiNoMore') }}</span>
      </div>
    </template>

    <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-work" />
  </div>
</template>

<style scoped>
.works {
  max-width: 1440px;
}
.works__tabs {
  min-width: 240px;
}
.works__bar {
  position: sticky;
  top: var(--topbar-h);
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 0;
  margin-bottom: 8px;
  background: var(--bg);
}
.works__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.works__count {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--brand);
}
.works__zip {
  margin-bottom: 12px;
}
.works__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 12px;
}
.works__sk {
  aspect-ratio: 3 / 4;
  border-radius: var(--radius);
}
.works__date {
  flex-shrink: 0;
  color: var(--text-3);
  font-size: 0.7rem;
}
.works__more {
  display: flex;
  justify-content: center;
  padding: 24px 0 8px;
  font-size: 0.8rem;
}
.works-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 16px;
  border-radius: var(--radius-lg);
  border: 1px dashed var(--line-strong);
  background: var(--bg-elev);
  text-align: center;
  color: var(--text-2);
}
.works-empty__icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: var(--panel-2);
  color: var(--brand);
}
.works-empty strong {
  color: var(--text);
  font-size: 0.95rem;
  font-weight: 600;
  max-width: 28rem;
}
@media (max-width: 768px) {
  .works__tabs {
    width: 100%;
  }
  .works__bar {
    position: static;
  }
  .works__kinds {
    flex-wrap: nowrap;
    overflow-x: auto;
    width: 100%;
    padding-bottom: 2px;
  }
  .works__kinds :deep(.chip) {
    flex-shrink: 0;
  }
  .works__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
}
</style>
