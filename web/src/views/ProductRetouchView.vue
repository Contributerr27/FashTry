<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import WorkbenchShell from '../components/workbench/WorkbenchShell.vue';
import GenerateButton from '../components/workbench/GenerateButton.vue';
import GenProgress from '../components/workbench/GenProgress.vue';
import EcomTabs from '../components/workbench/EcomTabs.vue';
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
import { useGeminiJob } from '../composables/useGeminiJob.js';

const { t } = useT();
const router = useRouter();

const BG_SWATCHES = ['#ffffff', '#f3f4f6', '#f5efe6', '#e8eef5', '#111111'];

const files = ref([]);
const enabled = ref(true);
const maxFiles = ref(12);
let fileId = 0;

const backgroundColor = ref('#ffffff');
const aspectRatio = ref('1:1');
const garmentScope = ref('FULL');
const shootHint = ref('auto');
const cropPreference = ref('balanced');
const creditsPerImage = ref(1);

const outputs = ref([]);
const sources = ref([]);
const parentTaskId = ref(null);
const regenSlot = ref(null);
const history = ref([]);
const lbIndex = ref(-1);
const zipping = ref(false);

const job = useGeminiJob({ getTask: (id) => api.getProductRetouchTask(id), estMs: 180000, busyKey: 'productRetouchAnalyzing' });

const cost = computed(() => Math.max(1, files.value.length) * creditsPerImage.value);
const canSubmit = computed(() => files.value.length > 0 && enabled.value && !job.running.value);

const aspectOptions = ['1:1', '3:4', '4:3', '9:16'].map((v) => ({ value: v, label: v }));
const scopeOptions = computed(() => [
  { value: 'FULL', label: t('productRetouchScopeFull') },
  { value: 'UPPER', label: t('productRetouchScopeUpper') },
  { value: 'LOWER', label: t('productRetouchScopeLower') },
]);
const shootOptions = computed(() => [
  { value: 'auto', label: t('productRetouchShootAuto') },
  { value: 'flat_lay', label: t('productRetouchShootFlat') },
  { value: 'hanging', label: t('productRetouchShootHang') },
  { value: 'on_model', label: t('productRetouchShootModel') },
]);
const cropOptions = computed(() => [
  { value: 'balanced', label: t('productRetouchCropBalanced') },
  { value: 'tight_on_garment', label: t('productRetouchCropTight') },
  { value: 'more_margin', label: t('productRetouchCropMargin') },
]);

const rows = computed(() =>
  outputs.value.map((url, i) => ({ url, source: sources.value[i] || '', index: i }))
);
const lbItems = computed(() =>
  outputs.value.map((url, i) => ({
    url,
    label: t('productRetouchOutItem', { n: i + 1 }),
    sourceType: 'retouch',
    sourceTaskId: parentTaskId.value,
  }))
);
const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.outputUrls?.[0] || h.sourceImageUrl || h.sourceImageUrls?.[0] || '',
    status: h.status === 'COMPLETED' ? 'done' : h.status === 'FAILED' ? 'failed' : 'loading',
    badge:
      h.status === 'FAILED'
        ? t('historyStatusFailed')
        : h.status !== 'COMPLETED'
          ? t('historyStatusProcessing')
          : (h.outputUrls?.length || 0) > 1
            ? String(h.outputUrls.length)
            : '',
    active: String(parentTaskId.value) === String(h.taskId),
    raw: h,
  }))
);

function addFiles(list) {
  const room = maxFiles.value - files.value.length;
  list.slice(0, room).forEach((file) => {
    files.value.push({ id: ++fileId, file, preview: URL.createObjectURL(file) });
  });
}
function removeFile(id) {
  const i = files.value.findIndex((f) => f.id === id);
  if (i >= 0) {
    URL.revokeObjectURL(files.value[i].preview);
    files.value.splice(i, 1);
  }
}
function clearFiles() {
  files.value.forEach((f) => URL.revokeObjectURL(f.preview));
  files.value = [];
}

async function loadConfig() {
  try {
    const c = await api.getProductRetouchConfig();
    enabled.value = c.enabled !== false;
    maxFiles.value = c.maxFiles ?? 12;
  } catch {
    /* ignore */
  }
  try {
    const p = await api.getProductRetouchPricing();
    creditsPerImage.value = p.creditsPerImage ?? 1;
  } catch {
    /* ignore */
  }
}

async function loadHistory() {
  if (!auth.isLoggedIn()) return;
  try {
    const data = await api.listProductRetouchHistory(1, 20);
    history.value = data.records || [];
  } catch {
    history.value = [];
  }
}

async function submit() {
  if (!files.value.length) return toast(t('productRetouchErrNoFile'), 'error');
  if (!auth.isLoggedIn()) return router.push('/login?return=' + encodeURIComponent('/product-retouch'));
  if (!enabled.value) return toast(t('productRetouchNotEnabled'), 'error');

  outputs.value = [];
  sources.value = files.value.map((f) => f.preview);
  parentTaskId.value = null;

  const fd = new FormData();
  files.value.forEach((f) => fd.append('files', f.file));
  fd.append('backgroundColor', backgroundColor.value);
  fd.append('aspectRatio', aspectRatio.value);
  fd.append('garmentScope', garmentScope.value);
  fd.append('shootHint', shootHint.value);
  fd.append('cropPreference', cropPreference.value);

  try {
    const r = await job.run(() => api.submitProductRetouch(fd), {
      onTick: (task, id) => {
        parentTaskId.value = id;
        if (task.urls?.length) outputs.value = [...task.urls];
      },
    });
    if (!r) return;
    parentTaskId.value = r.taskId;
    if (r.final.status === 'COMPLETED' && r.final.urls?.length) {
      outputs.value = r.final.urls;
      toast(t('toastGenerateSuccess'), 'success');
      refreshCredits();
    } else {
      toast(r.final.errorMsg || t('localEditStFailed'), 'error');
    }
    loadHistory();
  } catch (e) {
    toast(i18n.formatApiError(e.message) || t('errRequestFail'), 'error');
  }
}

async function regenerate(i) {
  if (!parentTaskId.value || regenSlot.value != null) return;
  regenSlot.value = i;
  try {
    const r = await job.run(() => api.regenerateProductRetouchSlot(parentTaskId.value, i), {
      busyKey: 'suiteTileGenerating',
    });
    if (!r) return;
    if (r.final.status === 'COMPLETED') {
      const fresh = await api.getProductRetouchTask(parentTaskId.value);
      if (fresh.urls?.length) outputs.value = fresh.urls;
      refreshCredits();
      loadHistory();
    } else {
      toast(r.final.errorMsg || t('localEditStFailed'), 'error');
    }
  } catch (e) {
    toast(i18n.formatApiError(e.message) || t('errRequestFail'), 'error');
  } finally {
    regenSlot.value = null;
  }
}

function onHistorySelect(it) {
  const h = it.raw;
  if (h.status !== 'COMPLETED' || !h.outputUrls?.length) {
    toast(h.status === 'FAILED' ? t('localEditStFailed') : t('historyStatusProcessing'), 'info');
    return;
  }
  outputs.value = h.outputUrls;
  sources.value = h.sourceImageUrls?.length ? h.sourceImageUrls : h.sourceImageUrl ? [h.sourceImageUrl] : [];
  parentTaskId.value = h.taskId;
}

async function zipAll() {
  if (!outputs.value.length) return;
  zipping.value = true;
  try {
    const r = await downloadZip(
      outputs.value.map((url, i) => ({ url, name: `retouch-${i + 1}` })),
      `fashtry-retouch-${stamp()}`
    );
    toast(r.failed ? t('uiZipPartial', { n: r.failed }) : t('uiZipDone', { n: r.ok }), r.failed ? 'info' : 'success');
  } finally {
    zipping.value = false;
  }
}

onMounted(() => {
  loadConfig();
  loadHistory();
});
onUnmounted(() => files.value.forEach((f) => URL.revokeObjectURL(f.preview)));
</script>

<template>
  <WorkbenchShell
    title-key="productRetouchTitle"
    lead-key="productRetouchLead"
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    history-title-key="productRetouchHistoryTitle"
    works-kind="retouch"
    @history-select="onHistorySelect"
  >
    <template #head-extra><EcomTabs /></template>

    <template #inputs-extra>
      <button v-if="files.length" type="button" class="btn btn-ghost btn-sm" @click="clearFiles">
        {{ t('productRetouchClearList') }}
      </button>
    </template>
    <template #inputs>
      <UploadBox
        multiple
        :items="files"
        :max="maxFiles"
        icon="retouch"
        :title="t('productRetouchPanelUpload')"
        :hint="t('productRetouchMaxFiles', { n: maxFiles })"
        @files="addFiles"
        @remove="removeFile"
      />
    </template>

    <template #params>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('productRetouchBg') }}</div>
        <div class="bg-swatches">
          <button
            v-for="c in BG_SWATCHES"
            :key="c"
            type="button"
            class="bg-swatch"
            :class="{ on: backgroundColor.toLowerCase() === c }"
            :style="{ background: c }"
            :title="c"
            @click="backgroundColor = c"
          ></button>
          <label class="bg-swatch bg-swatch--custom" :title="backgroundColor">
            <input v-model="backgroundColor" type="color" />
            <Icon name="palette" :size="15" />
          </label>
        </div>
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('productRetouchAspect') }}</div>
        <ChipGroup v-model="aspectRatio" :options="aspectOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('productRetouchScope') }}</div>
        <ChipGroup v-model="garmentScope" :options="scopeOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('productRetouchShootHint') }}</div>
        <ChipGroup v-model="shootHint" :options="shootOptions" :cols="2" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('productRetouchCrop') }}</div>
        <ChipGroup v-model="cropPreference" :options="cropOptions" />
      </div>
    </template>

    <template #actions>
      <GenerateButton
        :cost="cost"
        :loading="job.running.value && regenSlot == null"
        :disabled="!canSubmit"
        :label="t('productRetouchRun')"
        @click="submit"
      />
    </template>

    <template #toolbar>
      <button
        v-if="outputs.length > 1"
        type="button"
        class="btn btn-secondary btn-sm"
        :disabled="zipping"
        @click="zipAll"
      >
        <Icon name="archive" :size="15" />{{ t('uiDownloadAllZip') }}
      </button>
    </template>

    <template #main>
      <GenProgress :text="job.progressText.value" :pct="job.progressPct.value" />
      <div v-if="rows.length" class="pairs">
        <div v-for="row in rows" :key="row.index + row.url" class="pair">
          <div v-if="row.source" class="pair__src">
            <img :src="row.source" alt="" loading="lazy" />
            <span class="pair__tag">{{ t('uiOriginal') }}</span>
          </div>
          <Icon v-if="row.source" class="pair__arrow" name="arrowRight" :size="18" />
          <ResultTile
            class="pair__out"
            :url="row.url"
            :label="t('productRetouchOutItem', { n: row.index + 1 })"
            aspect="1 / 1"
            contain
            source-type="retouch"
            :source-task-id="String(parentTaskId || '')"
            :filename="`fashtry-retouch-${row.index + 1}`"
            :regenerable="!!parentTaskId"
            :regenerating="regenSlot === row.index"
            @zoom="lbIndex = row.index"
            @regenerate="regenerate(row.index)"
          />
        </div>
      </div>
      <div v-else-if="job.running.value" class="wb-results">
        <ResultTile v-for="(f, i) in files" :key="f.id" status="loading" aspect="1 / 1" :label="String(i + 1)" />
      </div>
      <div v-else class="wb-empty">
        <span class="wb-empty__icon"><Icon name="retouch" :size="26" /></span>
        <strong>{{ t('uiCanvasEmpty') }}</strong>
        <span>{{ t('productRetouchResultEmpty') }}</span>
      </div>
    </template>
  </WorkbenchShell>
  <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-retouch" />
</template>

<style scoped>
.bg-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.bg-swatch {
  position: relative;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border-radius: 50%;
  border: 1.5px solid var(--line-strong);
  cursor: pointer;
  transition: transform var(--transition);
}
.bg-swatch:hover {
  transform: scale(1.06);
}
.bg-swatch.on {
  box-shadow: 0 0 0 2px var(--panel), 0 0 0 4px var(--brand);
}
.bg-swatch--custom {
  display: flex;
  align-items: center;
  justify-content: center;
  background: conic-gradient(#f87171, #fbbf24, #34d399, #60a5fa, #a78bfa, #f87171);
  color: #fff;
  overflow: hidden;
}
.bg-swatch--custom input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.pairs {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 14px;
}
.pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: var(--radius-lg);
  background: var(--bg-elev);
  border: 1px solid var(--line);
}
.pair:not(:has(.pair__src)) {
  grid-template-columns: minmax(0, 1fr);
}
.pair__src {
  position: relative;
  aspect-ratio: 1;
  border-radius: var(--radius);
  overflow: hidden;
  background: var(--panel-2);
  border: 1px solid var(--line);
}
.pair__src img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.pair__tag {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.68rem;
  font-weight: 600;
  background: rgba(11, 13, 16, 0.72);
  color: #fff;
}
.pair__arrow {
  color: var(--brand);
}
.pair__out {
  align-self: stretch;
}
@media (max-width: 768px) {
  .pairs {
    grid-template-columns: 1fr;
  }
  .pair {
    gap: 6px;
    padding: 6px;
  }
}
</style>
