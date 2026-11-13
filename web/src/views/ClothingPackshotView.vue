<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
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
import { packshotImageLabel } from '../lib/geminiTaskPoll.js';
import { refreshCredits } from '../lib/userStore.js';
import { useGeminiJob } from '../composables/useGeminiJob.js';

const { t, locale } = useT();
const router = useRouter();

const ART = [
  { value: 'minimal_studio', key: 'packshotArtMinimal', thumb: '/static/styles/art-minimal.jpg' },
  { value: 'indoor_leisure', key: 'packshotArtLuxury', thumb: '/static/styles/art-luxury.jpg' },
  { value: 'urban_street', key: 'packshotArtStreet', thumb: '/static/styles/art-street.jpg' },
];

const sourceFile = ref(null);
const sourcePreview = ref('');
const faceFile = ref(null);
const facePreview = ref('');

const artDirection = ref('minimal_studio');
const modelGender = ref('female');
const ageGroup = ref('ADULT');
const bodyType = ref('AVERAGE');
const framingMode = ref('FULL_LENGTH');
const faceMode = ref('PRESET');
const facePreset = ref('ASIAN_ECOM');
const sceneCount = ref(4);
const creditsPerStep = ref(1);

const urls = ref([]);
const labels = ref([]);
const parentTaskId = ref(null);
const regenSlot = ref(null);
const history = ref([]);
const lbIndex = ref(-1);
const zipping = ref(false);

const job = useGeminiJob({ getTask: (id) => api.getPackshotTask(id), estMs: 300000, busyKey: 'packshotSlowHint' });

const cost = computed(() => (sceneCount.value + 2) * creditsPerStep.value);
const canSubmit = computed(
  () => !!sourceFile.value && !job.running.value && (faceMode.value !== 'UPLOAD' || !!faceFile.value)
);

const genderOptions = computed(() => [
  { value: 'female', label: t('packshotGenderFemale') },
  { value: 'male', label: t('packshotGenderMale') },
  { value: 'neutral', label: t('packshotGenderNeutral') },
]);
const ageOptions = computed(() => [
  { value: 'CHILD', label: t('packshotAgeChild') },
  { value: 'ADULT', label: t('packshotAgeAdult') },
  { value: 'SENIOR', label: t('packshotAgeSenior') },
]);
const bodyOptions = computed(() => [
  { value: 'SLIM', label: t('packshotBodySlim') },
  { value: 'AVERAGE', label: t('packshotBodyAverage') },
  { value: 'FULL', label: t('packshotBodyFull') },
]);
const framingOptions = computed(() => [
  { value: 'FULL_LENGTH', label: t('packshotFramingFull') },
  { value: 'HALF_LENGTH', label: t('packshotFramingHalf') },
  { value: 'AUTO', label: t('packshotFramingAuto') },
]);
const faceModeOptions = computed(() => [
  { value: 'PRESET', label: t('packshotFaceModePreset') },
  { value: 'UPLOAD', label: t('packshotFaceModeUpload') },
]);
const facePresetOptions = computed(() => [
  { value: 'ASIAN_ECOM', label: t('packshotFaceAsianEcom') },
  { value: 'WESTERN_FASHION', label: t('packshotFaceWesternFashion') },
  { value: 'MIXED_LUXE', label: t('packshotFaceMixedLuxe') },
  { value: 'EDITORIAL_SUPERMODEL', label: t('packshotFaceEditorial') },
]);

const groups = computed(() => {
  void locale.value;
  const g = { model: [], scenes: [], white: [], other: [] };
  urls.value.forEach((url, i) => {
    if (!url) return;
    const lab = labels.value[i] || '';
    const item = { url, slotIndex: i, text: packshotImageLabel(lab, i18n) || String(i + 1) };
    if (lab === 'MODEL_WEAR') g.model.push(item);
    else if (lab === 'ECOM_WHITE') g.white.push(item);
    else if (lab.startsWith('SCENE_')) g.scenes.push(item);
    else g.other.push(item);
  });
  return [
    { key: 'model', titleKey: 'packshotGroupModel', items: g.model },
    { key: 'scenes', titleKey: 'packshotGroupScenes', items: g.scenes },
    { key: 'white', titleKey: 'packshotGroupWhite', items: g.white },
    { key: 'other', titleKey: 'uiResult', items: g.other },
  ].filter((x) => x.items.length);
});
const flat = computed(() => groups.value.flatMap((g) => g.items));
const lbItems = computed(() =>
  flat.value.map((it) => ({ url: it.url, label: it.text, sourceType: 'packshot', sourceTaskId: parentTaskId.value }))
);
const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.outputUrls?.[0] || h.sourceImageUrl || '',
    status: h.status === 'COMPLETED' ? 'done' : h.status === 'FAILED' ? 'failed' : 'loading',
    badge: h.status === 'FAILED' ? t('historyStatusFailed') : h.status !== 'COMPLETED' ? t('historyStatusProcessing') : '',
    active: String(parentTaskId.value) === String(h.taskId),
    raw: h,
  }))
);

function openLb(url) {
  const i = flat.value.findIndex((x) => x.url === url);
  if (i >= 0) lbIndex.value = i;
}

function setSource(file) {
  sourceFile.value = file;
  if (sourcePreview.value) URL.revokeObjectURL(sourcePreview.value);
  sourcePreview.value = URL.createObjectURL(file);
}
function clearSource() {
  if (sourcePreview.value) URL.revokeObjectURL(sourcePreview.value);
  sourceFile.value = null;
  sourcePreview.value = '';
}
function setFace(file) {
  faceFile.value = file;
  if (facePreview.value) URL.revokeObjectURL(facePreview.value);
  facePreview.value = URL.createObjectURL(file);
}
function clearFace() {
  if (facePreview.value) URL.revokeObjectURL(facePreview.value);
  faceFile.value = null;
  facePreview.value = '';
}

async function loadPricing() {
  try {
    const p = await api.getPackshotPricing(sceneCount.value);
    creditsPerStep.value = p.creditsPerStep ?? 1;
  } catch {
    /* ignore */
  }
}
async function loadHistory() {
  if (!auth.isLoggedIn()) return;
  try {
    const data = await api.listPackshotHistory(1, 20);
    history.value = data.records || [];
  } catch {
    history.value = [];
  }
}

async function submit() {
  if (!sourceFile.value) return toast(t('localEditErrNoImage'), 'error');
  if (!auth.isLoggedIn()) return router.push('/login?return=' + encodeURIComponent('/model-shots'));
  if (faceMode.value === 'UPLOAD' && !faceFile.value) return toast(t('packshotFacePick'), 'error');

  urls.value = [];
  labels.value = [];
  parentTaskId.value = null;
  const fd = new FormData();
  fd.append('file', sourceFile.value);
  fd.append('artDirection', artDirection.value);
  fd.append('modelGender', modelGender.value);
  fd.append('ageGroup', ageGroup.value);
  fd.append('bodyType', bodyType.value);
  fd.append('framingMode', framingMode.value);
  fd.append('sceneVariantCount', String(sceneCount.value));
  if (faceMode.value === 'UPLOAD' && faceFile.value) {
    fd.append('faceReference', faceFile.value);
  } else {
    fd.append('faceMode', 'PRESET');
    fd.append('facePreset', facePreset.value);
  }
  try {
    const r = await job.run(() => api.submitClothingPackshot(fd), {
      onTick: (task, id) => {
        parentTaskId.value = id;
        if (task.urls?.length) {
          urls.value = [...task.urls];
          labels.value = task.imageLabels ? [...task.imageLabels] : [];
        }
      },
    });
    if (!r) return;
    parentTaskId.value = r.taskId;
    if (r.final.status === 'COMPLETED' && r.final.urls?.length) {
      urls.value = r.final.urls;
      labels.value = r.final.imageLabels || [];
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

async function regenerate(slotIndex) {
  if (!parentTaskId.value || regenSlot.value != null) return;
  regenSlot.value = slotIndex;
  try {
    const r = await job.run(() => api.regeneratePackshotSlot(parentTaskId.value, slotIndex), {
      busyKey: 'suiteTileGenerating',
    });
    if (!r) return;
    if (r.final.status === 'COMPLETED') {
      const fresh = await api.getPackshotTask(parentTaskId.value);
      if (fresh.urls?.length) {
        urls.value = fresh.urls;
        labels.value = fresh.imageLabels || [];
      }
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

async function onHistorySelect(it) {
  const h = it.raw;
  if (h.status !== 'COMPLETED' || !h.outputUrls?.length) {
    toast(h.status === 'FAILED' ? t('localEditStFailed') : t('historyStatusProcessing'), 'info');
    return;
  }
  if (h.parentTaskId) {
    try {
      const parent = await api.getPackshotTask(h.parentTaskId);
      if (parent?.urls?.length) {
        urls.value = parent.urls;
        labels.value = parent.imageLabels || [];
        parentTaskId.value = h.parentTaskId;
        return;
      }
    } catch {
      /* fallthrough */
    }
  }
  urls.value = h.outputUrls;
  labels.value = h.imageLabels || [];
  parentTaskId.value = h.parentTaskId ? null : h.taskId;
}

async function zipAll() {
  zipping.value = true;
  try {
    const r = await downloadZip(
      flat.value.map((it, i) => ({ url: it.url, name: `${String(i + 1).padStart(2, '0')}-${it.text.replace(/\s+/g, '')}` })),
      `fashtry-model-shots-${stamp()}`
    );
    toast(r.failed ? t('uiZipPartial', { n: r.failed }) : t('uiZipDone', { n: r.ok }), r.failed ? 'info' : 'success');
  } finally {
    zipping.value = false;
  }
}

watch(sceneCount, () => loadPricing());

onMounted(() => {
  loadPricing();
  loadHistory();
});
onUnmounted(() => {
  if (sourcePreview.value) URL.revokeObjectURL(sourcePreview.value);
  if (facePreview.value) URL.revokeObjectURL(facePreview.value);
});
</script>

<template>
  <WorkbenchShell
    title-key="packshotTitle"
    lead-key="packshotLead"
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    history-title-key="packshotTabHistory"
    works-kind="packshot"
    @history-select="onHistorySelect"
  >
    <template #head-extra><EcomTabs /></template>

    <template #inputs>
      <div class="wb-step"><span class="wb-step__num">1</span>{{ t('packshotStepUpload') }}</div>
      <UploadBox
        :preview="sourcePreview"
        icon="shirt"
        :hint="t('packshotDropHint')"
        aspect="3 / 4"
        @file="setSource"
        @clear="clearSource"
      />
      <div class="wb-step ps-step"><span class="wb-step__num">2</span>{{ t('packshotArt') }}</div>
      <div class="arts">
        <button
          v-for="a in ART"
          :key="a.value"
          type="button"
          class="art"
          :class="{ on: artDirection === a.value }"
          @click="artDirection = a.value"
        >
          <span class="art__swatch">
            <img :src="a.thumb" :alt="t(a.key)" />
          </span>
          <span class="art__name">{{ t(a.key) }}</span>
          <span v-if="artDirection === a.value" class="art__check"><Icon name="check" :size="12" :stroke-width="3" /></span>
        </button>
      </div>
    </template>

    <template #params>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('packshotGender') }}</div>
        <ChipGroup v-model="modelGender" :options="genderOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('packshotAgeGroup') }}</div>
        <ChipGroup v-model="ageGroup" :options="ageOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('packshotBodyType') }}</div>
        <ChipGroup v-model="bodyType" :options="bodyOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('packshotFraming') }}</div>
        <ChipGroup v-model="framingMode" :options="framingOptions" variant="seg" />
        <p class="wb-field__hint">{{ t('packshotFramingHint') }}</p>
      </div>
      <div class="wb-field">
        <div class="wb-field__label">
          {{ t('packshotSceneCount') }}<small>{{ sceneCount }}</small>
        </div>
        <div class="wb-range">
          <input v-model.number="sceneCount" type="range" min="2" max="8" step="1" />
          <output>{{ sceneCount }}</output>
        </div>
        <p class="wb-field__hint">{{ t('packshotSceneHint') }}</p>
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('packshotFaceMode') }}</div>
        <ChipGroup v-model="faceMode" :options="faceModeOptions" variant="seg" />
        <div v-if="faceMode === 'PRESET'" class="face-presets">
          <ChipGroup v-model="facePreset" :options="facePresetOptions" :cols="2" />
        </div>
        <div v-else class="face-upload">
          <UploadBox
            :preview="facePreview"
            icon="user"
            compact
            aspect="1 / 1"
            :hint="t('packshotFaceUploadHint')"
            @file="setFace"
            @clear="clearFace"
          />
        </div>
      </div>
    </template>

    <template #actions>
      <GenerateButton
        :cost="cost"
        :loading="job.running.value && regenSlot == null"
        :disabled="!canSubmit"
        :label="t('packshotSubmit')"
        @click="submit"
      />
    </template>

    <template #toolbar>
      <button v-if="flat.length > 1" type="button" class="btn btn-secondary btn-sm" :disabled="zipping" @click="zipAll">
        <Icon name="archive" :size="15" />{{ t('uiDownloadAllZip') }}
      </button>
    </template>

    <template #main>
      <GenProgress :text="job.progressText.value" :pct="job.progressPct.value" />
      <template v-if="groups.length">
        <div v-for="g in groups" :key="g.key" class="wb-group">
          <h4 class="wb-group__title">{{ t(g.titleKey) }}</h4>
          <div class="wb-results">
            <ResultTile
              v-for="it in g.items"
              :key="it.slotIndex + it.url"
              :url="it.url"
              :label="it.text"
              source-type="packshot"
              :source-task-id="String(parentTaskId || '')"
              :filename="`fashtry-model-shot-${it.slotIndex + 1}`"
              :regenerable="!!parentTaskId && !job.running.value"
              :regenerating="regenSlot === it.slotIndex"
              @zoom="openLb(it.url)"
              @regenerate="regenerate(it.slotIndex)"
            />
          </div>
        </div>
      </template>
      <div v-else-if="job.running.value" class="wb-results">
        <ResultTile v-for="i in sceneCount + 2" :key="i" status="loading" :label="String(i)" />
      </div>
      <div v-else class="wb-empty">
        <span class="wb-empty__icon"><Icon name="model" :size="26" /></span>
        <strong>{{ t('uiCanvasEmpty') }}</strong>
        <span>{{ t('packshotEmptyRun') }}</span>
      </div>
    </template>
  </WorkbenchShell>
  <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-model-shot" />
</template>

<style scoped>
.ps-step {
  margin-top: 20px;
}
.arts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.art {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 5px 5px 7px;
  border-radius: var(--radius);
  border: 1.5px solid var(--line);
  background: var(--bg-elev);
  color: var(--text-2);
  font: inherit;
  cursor: pointer;
}
.art:hover {
  border-color: var(--line-strong);
  color: var(--text);
}
.art.on {
  border-color: var(--brand);
  color: var(--text);
}
.art__swatch {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--panel-3);
}
.art__swatch img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 15%;
  display: block;
}
.art__name {
  font-size: 0.72rem;
  font-weight: 600;
  text-align: center;
  line-height: 1.3;
}
.art__check {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--brand);
  color: var(--brand-ink);
}
.face-presets,
.face-upload {
  margin-top: 10px;
}
.face-upload {
  max-width: 160px;
}
</style>
