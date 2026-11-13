<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import WorkbenchShell from '../components/workbench/WorkbenchShell.vue';
import GenerateButton from '../components/workbench/GenerateButton.vue';
import GenProgress from '../components/workbench/GenProgress.vue';
import GarmentLibrary from '../components/workbench/GarmentLibrary.vue';
import UploadBox from '../components/ui/UploadBox.vue';
import ChipGroup from '../components/ui/ChipGroup.vue';
import ResultTile from '../components/ui/ResultTile.vue';
import ImageLightbox from '../components/ui/ImageLightbox.vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';
import { downloadZip, stamp } from '../lib/download.js';
import { refreshCredits } from '../lib/userStore.js';

const MAX = 6;
const RES_COST = { '1K': 1, '2K': 2, '4K': 4 };
const RETURN = '/login?return=' + encodeURIComponent('/batch-tryon');

const { t } = useT();
const router = useRouter();

const modelUrl = ref('');
const modelUploading = ref(false);
/** @type {import('vue').Ref<{ id: string, url: string, preview: string, uploading: boolean }[]>} */
const garments = ref([]);
const category = ref('auto');
const resolution = ref('1K');
const libOpen = ref(true);

const generating = ref(false);
/** @type {import('vue').Ref<{ garment: string, status: 'loading'|'done'|'failed', url: string, taskId: string, text: string }[]>} */
const results = ref([]);
const doneCount = ref(0);
const history = ref([]);
const lbIndex = ref(-1);
const zipping = ref(false);

let session = 0;
let alive = true;

const readyGarments = computed(() => garments.value.filter((g) => g.url && !g.uploading));
const cost = computed(() => readyGarments.value.length * (RES_COST[resolution.value] || 1));
const canGenerate = computed(
  () =>
    !!modelUrl.value &&
    !modelUploading.value &&
    garments.value.length > 0 &&
    garments.value.every((g) => g.url && !g.uploading) &&
    !generating.value
);
const genLabel = computed(() =>
  readyGarments.value.length ? t('generateTryonBatch', { n: readyGarments.value.length }) : t('generateTryon')
);
const progressText = computed(() =>
  generating.value && results.value.length
    ? t('batchGeneratingProgress', { current: doneCount.value, total: results.value.length })
    : ''
);
const progressPct = computed(() =>
  generating.value && results.value.length ? Math.round((doneCount.value / results.value.length) * 100) : null
);

const categoryOptions = computed(() => [
  { value: 'auto', label: t('autoDetect') },
  { value: 'tops', label: t('tops') },
  { value: 'bottoms', label: t('bottoms') },
  { value: 'one-pieces', label: t('onePieces') },
]);
const resOptions = computed(() => [
  { value: '1K', label: '1K', sub: t('uiCostPer', { n: 1 }) },
  { value: '2K', label: '2K', sub: t('uiCostPer', { n: 2 }) },
  { value: '4K', label: '4K', sub: t('uiCostPer', { n: 4 }) },
]);

const uploadItems = computed(() =>
  garments.value.map((g) => ({ id: g.id, preview: g.preview || g.url, loading: g.uploading }))
);
const doneResults = computed(() => results.value.filter((r) => r.status === 'done' && r.url));
const lbItems = computed(() =>
  doneResults.value.map((r, i) => ({ url: r.url, label: `#${i + 1}`, sourceType: 'tryon', sourceTaskId: r.taskId }))
);
const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.thumbUrl || h.resultUrl || h.garmentUrl || '',
    status: h.status === 2 ? 'done' : h.status === 3 ? 'failed' : 'loading',
    badge: h.status === 3 ? t('historyStatusFailed') : h.status !== 2 ? t('historyStatusProcessing') : '',
    active: false,
    raw: h,
  }))
);

function statusLabel(code) {
  const keys = ['queuing', 'generating', 'historyStatusDone', 'historyStatusFailed'];
  return typeof code === 'number' && keys[code] ? t(keys[code]).replace(/[.…]+\s*$/u, '') : '';
}

function requireLogin() {
  if (auth.isLoggedIn()) return true;
  router.push(RETURN);
  return false;
}

async function uploadModel(file) {
  if (!requireLogin()) return;
  const local = URL.createObjectURL(file);
  const prev = modelUrl.value;
  modelUrl.value = local;
  modelUploading.value = true;
  try {
    modelUrl.value = await api.uploadBusiness(file);
  } catch (e) {
    modelUrl.value = prev;
    toast(i18n.formatApiError(e.message) || t('errUploadFail'), 'error');
  } finally {
    modelUploading.value = false;
    URL.revokeObjectURL(local);
  }
}

function newId() {
  return 'g' + Date.now() + Math.random().toString(36).slice(2);
}

async function addGarmentFiles(files) {
  if (!requireLogin()) return;
  const list = Array.from(files || []).filter((f) => f.type?.startsWith('image/'));
  const room = MAX - garments.value.length;
  if (room <= 0) return toast(t('tryonBatchMaxToast', { max: MAX }), 'error');
  if (list.length > room) toast(t('tryonBatchMaxToast', { max: MAX }), 'info');
  await Promise.all(
    list.slice(0, room).map(async (file) => {
      const id = newId();
      const preview = URL.createObjectURL(file);
      garments.value.push({ id, url: '', preview, uploading: true });
      try {
        const url = await api.uploadBusiness(file);
        const g = garments.value.find((x) => x.id === id);
        if (g) Object.assign(g, { url, uploading: false });
      } catch (e) {
        garments.value = garments.value.filter((x) => x.id !== id);
        toast(i18n.formatApiError(e.message) || t('errUploadFail'), 'error');
      } finally {
        URL.revokeObjectURL(preview);
        const g = garments.value.find((x) => x.id === id);
        if (g) g.preview = '';
      }
    })
  );
}

function removeGarment(id) {
  garments.value = garments.value.filter((g) => g.id !== id);
}

function pickFromLibrary(g) {
  const existing = garments.value.find((x) => x.url === g.imageUrl);
  if (existing) return removeGarment(existing.id);
  if (garments.value.length >= MAX) return toast(t('tryonBatchMaxToast', { max: MAX }), 'info');
  garments.value.push({ id: newId(), url: g.imageUrl, preview: g.thumbUrl || '', uploading: false });
  if (['tops', 'bottoms', 'one-pieces'].includes(g.garmentType)) category.value = g.garmentType;
}

async function loadHistory() {
  if (!auth.isLoggedIn()) return;
  try {
    const res = await api.getMyTasks(1, 30);
    history.value = res?.records ?? res?.list ?? [];
  } catch {
    history.value = [];
  }
}

async function waitAll(taskIds, sid) {
  let first = true;
  for (;;) {
    if (!alive || sid !== session) return null;
    if (!first) await new Promise((r) => setTimeout(r, 2000));
    first = false;
    if (!alive || sid !== session) return null;
    const list = await api.getTryonTasksStatus(taskIds);
    list.forEach((s, i) => {
      const r = results.value[i];
      if (!r) return;
      if (s.status === 2) Object.assign(r, { status: 'done', url: s.resultUrl || s.thumbUrl || '', text: '' });
      else if (s.status === 3) Object.assign(r, { status: 'failed', text: t('batchResultFailed') });
      else
        Object.assign(r, {
          status: 'loading',
          text: statusLabel(s.status) + (typeof s.progress === 'number' ? ` ${s.progress}%` : ''),
        });
    });
    doneCount.value = list.filter((s) => s.status === 2 || s.status === 3).length;
    if (list.every((s) => s.status === 2 || s.status === 3)) return list;
  }
}

async function runBatch() {
  if (!canGenerate.value || !requireLogin()) return;
  const sid = ++session;
  const items = readyGarments.value.slice();
  generating.value = true;
  doneCount.value = 0;
  results.value = items.map((g) => ({
    garment: g.url,
    status: 'loading',
    url: '',
    taskId: '',
    text: t('batchResultPending'),
  }));
  try {
    const res = await api.submitTryonBatch({
      modelUrl: modelUrl.value,
      garmentUrls: items.map((g) => g.url),
      category: category.value,
      outputResolution: resolution.value,
    });
    const ids = (res?.taskIds ?? []).map(String);
    if (ids.length !== items.length) throw new Error('batch submit response mismatch');
    ids.forEach((id, i) => (results.value[i].taskId = id));
    loadHistory();
    const final = await waitAll(ids, sid);
    if (final && final.some((s) => s.status === 2)) toast(t('toastGenerateSuccess'), 'success');
  } catch (e) {
    const msg = i18n.formatApiError(e.message) || t('errSubmitFail');
    results.value.forEach((r) => Object.assign(r, { status: 'failed', text: msg }));
    toast(msg, 'error');
  } finally {
    if (sid === session) {
      generating.value = false;
      refreshCredits();
      loadHistory();
    }
  }
}

function openLb(url) {
  const i = doneResults.value.findIndex((r) => r.url === url);
  if (i >= 0) lbIndex.value = i;
}

function onHistorySelect(it) {
  const h = it.raw;
  if (h.status === 2 && (h.resultUrl || h.thumbUrl)) {
    results.value = [
      { garment: h.garmentUrl || '', status: 'done', url: h.resultUrl || h.thumbUrl, taskId: String(h.taskId), text: '' },
    ];
  } else {
    toast(h.status === 3 ? t('errGenerateFail') : t('historyStatusProcessing'), 'info');
  }
}

async function zipAll() {
  zipping.value = true;
  try {
    const r = await downloadZip(
      doneResults.value.map((x, i) => ({ url: x.url, name: `tryon-${String(i + 1).padStart(2, '0')}` })),
      `fashtry-batch-tryon-${stamp()}`
    );
    toast(r.failed ? t('uiZipPartial', { n: r.failed }) : t('uiZipDone', { n: r.ok }), r.failed ? 'info' : 'success');
  } finally {
    zipping.value = false;
  }
}

onMounted(loadHistory);
onUnmounted(() => {
  alive = false;
  session += 1;
});
</script>

<template>
  <WorkbenchShell
    title-key="tryonBatchPageTitle"
    lead-key="batchPageLead"
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    works-kind="tryon"
    @history-select="onHistorySelect"
  >
    <template #head-extra>
      <RouterLink to="/tryon" class="btn btn-secondary btn-sm">
        <Icon name="shirt" :size="15" />{{ t('uiNavTryon') }}
      </RouterLink>
    </template>

    <template #inputs>
      <div class="wb-step"><span class="wb-step__num">1</span>{{ t('batchWsModelLabel') }}</div>
      <UploadBox
        :preview="modelUrl"
        :loading="modelUploading"
        icon="user"
        :hint="t('batchDropModelHint')"
        aspect="3 / 4"
        compact
        @file="uploadModel"
        @clear="modelUrl = ''"
      />

      <div class="wb-step bt-step">
        <span class="wb-step__num">2</span>{{ t('batchWsGarmentsLabel') }}
        <small class="bt-count">{{ garments.length }}/{{ MAX }}</small>
      </div>
      <UploadBox
        multiple
        numbered
        :items="uploadItems"
        :max="MAX"
        icon="shirt"
        :hint="t('batchGarmentDropHint')"
        @files="addGarmentFiles"
        @remove="removeGarment"
      />
      <button v-if="garments.length" type="button" class="bt-clear" @click="garments = []">
        <Icon name="trash" :size="13" />{{ t('batchClearGarments') }}
      </button>

      <div class="bt-lib">
        <button type="button" class="bt-lib__head" @click="libOpen = !libOpen">
          <Icon name="hanger" :size="15" />
          <span>{{ t('batchCatalogTitle') }}</span>
          <Icon :name="libOpen ? 'chevronDown' : 'chevronRight'" :size="15" class="bt-lib__chev" />
        </button>
        <GarmentLibrary
          v-if="libOpen"
          :selected="garments.map((g) => g.url)"
          :page-size="9"
          @pick="pickFromLibrary"
        />
      </div>
    </template>

    <template #params>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('garmentType') }}</div>
        <ChipGroup v-model="category" :options="categoryOptions" :cols="2" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('tryonOutputResolution') }}</div>
        <ChipGroup v-model="resolution" :options="resOptions" variant="cards" />
      </div>
      <p v-if="readyGarments.length" class="wb-field__hint">
        {{
          t('tryonBatchEstimateCredits', {
            total: cost,
            n: readyGarments.length,
            each: RES_COST[resolution] || 1,
          })
        }}
      </p>
    </template>

    <template #actions>
      <GenerateButton :cost="cost" :loading="generating" :disabled="!canGenerate" :label="genLabel" @click="runBatch" />
    </template>

    <template #toolbar>
      <button
        v-if="doneResults.length > 1"
        type="button"
        class="btn btn-secondary btn-sm"
        :disabled="zipping"
        @click="zipAll"
      >
        <Icon name="archive" :size="15" />{{ t('uiDownloadAllZip') }}
      </button>
    </template>

    <template #main>
      <GenProgress :text="progressText" :pct="progressPct" />
      <div v-if="results.length" class="wb-results wb-results--lg">
        <ResultTile
          v-for="(r, i) in results"
          :key="i + r.garment"
          :url="r.url"
          :label="`#${i + 1}`"
          :status="r.status"
          :status-text="r.text"
          source-type="tryon"
          :source-task-id="r.taskId"
          :filename="`fashtry-batch-tryon-${i + 1}`"
          @zoom="openLb(r.url)"
        >
          <template #overlay>
            <img v-if="r.garment" :src="r.garment" alt="" class="bt-inset" />
          </template>
        </ResultTile>
      </div>
      <div v-else class="wb-empty">
        <span class="wb-empty__icon"><Icon name="layers" :size="26" /></span>
        <strong>{{ t('uiCanvasEmpty') }}</strong>
        <span>{{ t('batchResultHint') }}</span>
      </div>
    </template>
  </WorkbenchShell>
  <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-batch-tryon" />
</template>

<style scoped>
.bt-step {
  margin-top: 20px;
}
.bt-count {
  margin-left: auto;
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--text-3);
}
.bt-clear {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 0;
  border: none;
  background: none;
  color: var(--text-3);
  font: inherit;
  font-size: 0.75rem;
  cursor: pointer;
}
.bt-clear:hover {
  color: var(--error);
}
.bt-lib {
  margin-top: 16px;
  padding: 10px 12px 12px;
  border-radius: var(--radius);
  background: var(--bg-elev);
  border: 1px solid var(--line);
}
.bt-lib__head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  padding: 0;
  border: none;
  background: none;
  color: var(--text);
  font: inherit;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
}
.bt-lib__chev {
  margin-left: auto;
  color: var(--text-3);
}
.bt-inset {
  position: absolute;
  left: 8px;
  bottom: 8px;
  width: 30%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 8px;
  border: 2px solid rgba(255, 255, 255, 0.85);
  box-shadow: var(--shadow-2);
  pointer-events: none;
}
</style>
