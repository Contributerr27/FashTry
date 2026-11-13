<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import WorkbenchShell from '../components/workbench/WorkbenchShell.vue';
import GenerateButton from '../components/workbench/GenerateButton.vue';
import GenProgress from '../components/workbench/GenProgress.vue';
import GarmentLibrary from '../components/workbench/GarmentLibrary.vue';
import UploadBox from '../components/ui/UploadBox.vue';
import ChipGroup from '../components/ui/ChipGroup.vue';
import CompareSlider from '../components/ui/CompareSlider.vue';
import ImageLightbox from '../components/ui/ImageLightbox.vue';
import FavButton from '../components/ui/FavButton.vue';
import Icon from '../components/ui/Icon.vue';
import OnboardingTryonCoach from '../components/OnboardingTryonCoach.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { useT } from '../lib/useT.js';
import { i18n } from '../lib/i18n.js';
import { toast } from '../lib/toast.js';
import { downloadUrl } from '../lib/download.js';
import { refreshCredits } from '../lib/userStore.js';
import {
  ONBOARDING_ASSETS,
  completeOnboardingTryon,
  shouldShowOnboardingTryon,
} from '../lib/onboardingTryonGate.js';

const { t } = useT();
const router = useRouter();

const RES_COST = { '1K': 1, '2K': 2, '4K': 4 };

const modelUrl = ref('');
const garmentUrl = ref('');
const modelUploading = ref(false);
const garmentUploading = ref(false);
const category = ref('auto');
const resolution = ref('1K');

const generating = ref(false);
const progressText = ref('');
const progressPct = ref(null);
const result = ref(null); // { taskId, url, modelUrl, garmentUrl }
const compare = ref(true);
const lbIndex = ref(-1);

const history = ref([]);
const libOpen = ref(false);

/** model | garment | generate | result */
const obStep = ref('model');
const obActive = ref(shouldShowOnboardingTryon());

let pollTimer = null;
let demoTimer = null;
let alive = true;

const cost = computed(() => RES_COST[resolution.value] || 1);
const canSubmit = computed(() => !!modelUrl.value && !!garmentUrl.value && !generating.value);

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
const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.thumbUrl || h.resultUrl || h.garmentUrl || '',
    status: h.status === 2 ? 'done' : h.status === 3 ? 'failed' : 'loading',
    badge: h.status === 2 ? '' : h.status === 3 ? t('historyStatusFailed') : t('historyStatusProcessing'),
    active: result.value && String(result.value.taskId) === String(h.taskId),
    raw: h,
  }))
);

const lbItems = computed(() =>
  result.value?.url
    ? [{ url: result.value.url, label: t('uiResult'), sourceType: 'tryon', sourceTaskId: result.value.taskId }]
    : []
);

function statusLabel(code) {
  const keys = ['queuing', 'generating', 'historyStatusDone', 'historyStatusFailed'];
  return typeof code === 'number' && keys[code] ? t(keys[code]) : t('historyStatusProcessing');
}

function requireLogin() {
  if (auth.isLoggedIn()) return true;
  toast(t('pleaseLogin'), 'error');
  router.push('/login?return=' + encodeURIComponent('/tryon'));
  return false;
}

async function upload(file, kind) {
  if (obActive.value) return;
  if (!requireLogin()) return;
  const busy = kind === 'model' ? modelUploading : garmentUploading;
  const target = kind === 'model' ? modelUrl : garmentUrl;
  const local = URL.createObjectURL(file);
  const prev = target.value;
  target.value = local;
  busy.value = true;
  try {
    target.value = await api.uploadBusiness(file);
  } catch (e) {
    target.value = prev;
    toast(i18n.formatApiError(e.message) || t('errUploadFail'), 'error');
  } finally {
    busy.value = false;
    URL.revokeObjectURL(local);
  }
}

function applyDemoUpload(kind) {
  const busy = kind === 'model' ? modelUploading : garmentUploading;
  const target = kind === 'model' ? modelUrl : garmentUrl;
  const src = kind === 'model' ? ONBOARDING_ASSETS.model : ONBOARDING_ASSETS.garment;
  busy.value = true;
  target.value = '';
  window.setTimeout(() => {
    if (!alive) return;
    target.value = src;
    busy.value = false;
    if (kind === 'model') obStep.value = 'garment';
    else obStep.value = 'generate';
  }, 450);
}

function onModelZoneClick(e) {
  if (!obActive.value) return;
  e.preventDefault();
  e.stopPropagation();
  if (obStep.value !== 'model' || modelUploading.value) return;
  applyDemoUpload('model');
}

function onGarmentZoneClick(e) {
  if (!obActive.value) return;
  e.preventDefault();
  e.stopPropagation();
  if (obStep.value !== 'garment' || garmentUploading.value) return;
  applyDemoUpload('garment');
}

function clearModel() {
  if (obActive.value) return;
  modelUrl.value = '';
}

function clearGarment() {
  if (obActive.value) return;
  garmentUrl.value = '';
}

function toggleLibrary() {
  if (obActive.value) return;
  libOpen.value = !libOpen.value;
}

function pickGarment(g) {
  if (obActive.value) return;
  garmentUrl.value = g.imageUrl;
  if (['tops', 'bottoms', 'one-pieces'].includes(g.garmentType)) category.value = g.garmentType;
  toast(t('uiTryonPickedFromLib'), 'success', 1600);
}

async function loadHistory() {
  if (!auth.isLoggedIn()) {
    history.value = [];
    return;
  }
  try {
    const res = await api.getMyTasks(1, 30);
    history.value = res?.records ?? res?.list ?? [];
  } catch {
    history.value = [];
  }
}

function stopPoll() {
  if (pollTimer) clearTimeout(pollTimer);
  pollTimer = null;
}

function poll(taskId, snapshot) {
  stopPoll();
  pollTimer = setTimeout(async () => {
    if (!alive) return;
    try {
      const s = await api.getTaskStatus(taskId);
      progressText.value = statusLabel(s.status);
      progressPct.value = typeof s.progress === 'number' ? s.progress : null;
      if (s.status === 2) {
        generating.value = false;
        progressText.value = '';
        result.value = { taskId, url: s.resultUrl || s.thumbUrl, ...snapshot };
        compare.value = true;
        toast(t('toastGenerateSuccess'), 'success');
        refreshCredits();
        loadHistory();
        return;
      }
      if (s.status === 3) {
        generating.value = false;
        progressText.value = '';
        toast(s.errorMsg || t('errGenerateFail'), 'error');
        loadHistory();
        return;
      }
    } catch {
      progressText.value = t('fetchingStatus');
    }
    poll(taskId, snapshot);
  }, 2000);
}

function clearDemoTimer() {
  if (demoTimer) {
    clearInterval(demoTimer);
    demoTimer = null;
  }
}

function runDemoGenerate() {
  if (generating.value) return;
  generating.value = true;
  progressText.value = t('onboardingTryonGenerating');
  progressPct.value = 6;
  result.value = null;
  const started = Date.now();
  const duration = 3200;
  clearDemoTimer();
  demoTimer = setInterval(() => {
    if (!alive) return;
    const elapsed = Date.now() - started;
    progressPct.value = Math.max(6, Math.min(96, Math.round((elapsed / duration) * 100)));
    if (elapsed >= duration) {
      clearDemoTimer();
      progressPct.value = 100;
      generating.value = false;
      progressText.value = '';
      result.value = {
        taskId: 'onboarding-demo',
        url: ONBOARDING_ASSETS.result,
        modelUrl: modelUrl.value,
        garmentUrl: garmentUrl.value,
      };
      compare.value = true;
      obStep.value = 'result';
      toast(t('toastGenerateSuccess'), 'success');
    }
  }, 80);
}

function finishOnboarding() {
  completeOnboardingTryon();
  obActive.value = false;
  toast(t('onboardingTryonFinishToast'), 'success');
  window.dispatchEvent(new Event('fashtry-onboarding-done'));
}

async function submit() {
  if (!canSubmit.value || !requireLogin()) return;
  if (obActive.value) {
    if (obStep.value !== 'generate') return;
    runDemoGenerate();
    return;
  }
  generating.value = true;
  progressText.value = t('tryonSubmitting');
  progressPct.value = null;
  const snapshot = { modelUrl: modelUrl.value, garmentUrl: garmentUrl.value };
  try {
    const { taskId } = await api.submitTryon({
      modelUrl: modelUrl.value,
      garmentUrl: garmentUrl.value,
      category: category.value,
      outputResolution: resolution.value,
    });
    progressText.value = t('queuing');
    loadHistory();
    poll(taskId, snapshot);
  } catch (e) {
    generating.value = false;
    progressText.value = '';
    toast(i18n.formatApiError(e.message) || t('errSubmitFail'), 'error');
  }
}

function onHistorySelect(it) {
  const h = it.raw;
  if (h.status === 2 && (h.resultUrl || h.thumbUrl)) {
    result.value = {
      taskId: h.taskId,
      url: h.resultUrl || h.thumbUrl,
      modelUrl: h.modelUrl || '',
      garmentUrl: h.garmentUrl || '',
    };
    compare.value = !!h.modelUrl;
  } else if (h.status === 3) {
    toast(t('errGenerateFail'), 'error');
  } else {
    toast(statusLabel(h.status), 'info');
  }
}

function reuseInputs() {
  if (!result.value) return;
  if (result.value.modelUrl) modelUrl.value = result.value.modelUrl;
  if (result.value.garmentUrl) garmentUrl.value = result.value.garmentUrl;
}

async function download() {
  if (!result.value?.url) return;
  const r = await downloadUrl(result.value.url, `fashtry-tryon-${result.value.taskId}`);
  toast(r === 'downloaded' ? t('toastDownloadSuccess') : t('toastOpenInNewTabSave'), r === 'downloaded' ? 'success' : 'info');
}

onMounted(() => {
  if (obActive.value) {
    if (modelUrl.value && garmentUrl.value) obStep.value = 'generate';
    else if (modelUrl.value) obStep.value = 'garment';
    else obStep.value = 'model';
  }
  loadHistory();
});
onUnmounted(() => {
  alive = false;
  stopPoll();
  clearDemoTimer();
});
</script>

<template>
  <WorkbenchShell
    title-key="uiTryonTitle"
    lead-key="uiTryonLead"
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    works-kind="tryon"
    @history-select="onHistorySelect"
  >
    <template #head-extra>
      <RouterLink v-if="!obActive" to="/batch-tryon" class="btn btn-secondary btn-sm">
        <Icon name="layers" :size="15" />{{ t('navBatchTryon') }}
      </RouterLink>
    </template>

    <template #inputs>
      <div class="wb-step"><span class="wb-step__num">1</span>{{ t('uiTryonPerson') }}</div>
      <div
        class="ob-target"
        :class="{ 'ob-target--active': obActive && obStep === 'model' }"
        @click.capture="onModelZoneClick"
      >
        <UploadBox
          :preview="modelUrl"
          :loading="modelUploading"
          :disabled="obActive && obStep !== 'model'"
          icon="user"
          :hint="t('uiTryonPersonHint')"
          aspect="3 / 4"
          @file="(f) => upload(f, 'model')"
          @clear="clearModel"
        />
      </div>

      <div class="wb-step tryon-step2">
        <span class="wb-step__num">2</span>{{ t('uiTryonGarment') }}
        <button
          type="button"
          class="tryon-lib-toggle"
          :class="{ on: libOpen }"
          :disabled="obActive"
          @click="toggleLibrary"
        >
          <Icon name="hanger" :size="14" />{{ t('uiTryonLibrary') }}
        </button>
      </div>
      <div
        class="ob-target"
        :class="{ 'ob-target--active': obActive && obStep === 'garment' }"
        @click.capture="onGarmentZoneClick"
      >
        <UploadBox
          :preview="garmentUrl"
          :loading="garmentUploading"
          :disabled="obActive && obStep !== 'garment'"
          icon="shirt"
          :hint="t('uiTryonGarmentHint')"
          aspect="1 / 1"
          @file="(f) => upload(f, 'garment')"
          @clear="clearGarment"
        />
      </div>

      <div v-if="libOpen && !obActive" class="tryon-lib">
        <p class="wb-field__hint">{{ t('uiTryonLibraryHint') }}</p>
        <GarmentLibrary :selected="garmentUrl ? [garmentUrl] : []" @pick="pickGarment" />
      </div>
    </template>

    <template #params>
      <div class="wb-field" :class="{ 'ob-dim': obActive }">
        <div class="wb-field__label">{{ t('garmentType') }}</div>
        <ChipGroup v-model="category" :options="categoryOptions" :cols="2" />
      </div>
      <div class="wb-field" :class="{ 'ob-dim': obActive }">
        <div class="wb-field__label">{{ t('tryonOutputResolution') }}</div>
        <ChipGroup v-model="resolution" :options="resOptions" variant="cards" />
      </div>
    </template>

    <template #actions>
      <div class="ob-target" :class="{ 'ob-target--active': obActive && obStep === 'generate' }">
        <GenerateButton
          :cost="obActive ? null : cost"
          :loading="generating"
          :disabled="!canSubmit || (obActive && obStep !== 'generate')"
          :label="obActive ? t('onboardingTryonStart') : ''"
          @click="submit"
        />
      </div>
    </template>

    <template #toolbar>
      <template v-if="result?.url">
        <button
          v-if="result.modelUrl"
          type="button"
          class="btn btn-sm"
          :class="compare ? 'btn-secondary' : 'btn-ghost'"
          @click="compare = !compare"
        >
          <Icon name="compare" :size="15" />{{ t('uiCompare') }}
        </button>
        <button type="button" class="btn btn-ghost btn-sm" :title="t('uiReplace')" @click="reuseInputs">
          <Icon name="refresh" :size="15" />
        </button>
        <FavButton :url="result.url" source-type="tryon" :source-task-id="String(result.taskId)" variant="toolbar" />
        <button type="button" class="btn btn-ghost btn-sm" @click="lbIndex = 0">
          <Icon name="zoomIn" :size="15" />
        </button>
        <button type="button" class="btn btn-primary btn-sm" @click="download">
          <Icon name="download" :size="15" />{{ t('uiDownload') }}
        </button>
      </template>
    </template>

    <template #main>
      <GenProgress :text="generating ? progressText : ''" :pct="progressPct" />
      <div v-if="result?.url" class="tryon-stage">
        <CompareSlider v-if="compare && result.modelUrl" :before="result.modelUrl" :after="result.url" />
        <img v-else class="tryon-stage__img" :src="result.url" alt="" @click="lbIndex = 0" />
      </div>
      <div v-else-if="generating" class="tryon-stage tryon-stage--loading">
        <img v-if="modelUrl" :src="modelUrl" alt="" class="tryon-stage__ghost" />
        <span class="spinner"></span>
      </div>
      <div v-else class="wb-empty">
        <span class="wb-empty__icon"><Icon name="shirt" :size="26" /></span>
        <strong>{{ t('uiCanvasEmpty') }}</strong>
        <span>{{ t('uiCanvasEmptyHint') }}</span>
      </div>
    </template>
  </WorkbenchShell>
  <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-tryon" />
  <OnboardingTryonCoach v-if="obActive" :step="obStep" @finish="finishOnboarding" />
</template>

<style scoped>
.ob-target {
  position: relative;
  border-radius: 14px;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}
.ob-target--active {
  z-index: 2;
  box-shadow: 0 0 0 2px var(--brand, #6c4cf6), 0 0 0 8px color-mix(in srgb, var(--brand, #6c4cf6) 22%, transparent);
  animation: ob-pulse 1.6s ease-in-out infinite;
}
.ob-dim {
  opacity: 0.45;
  pointer-events: none;
}
@keyframes ob-pulse {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-1px);
  }
}
.tryon-step2 {
  margin-top: 20px;
}
.tryon-lib-toggle {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-2);
  font: inherit;
  font-size: 0.72rem;
  font-weight: 500;
  cursor: pointer;
}
.tryon-lib-toggle:hover,
.tryon-lib-toggle.on {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--brand-soft);
}
.tryon-lib {
  margin-top: 12px;
  padding: 12px;
  border-radius: var(--radius);
  background: var(--bg-elev);
  border: 1px solid var(--line);
}
.tryon-lib .wb-field__hint {
  margin: 0 0 10px;
}
.tryon-stage {
  flex: 1;
  position: relative;
  min-height: 420px;
  height: calc(100vh - var(--topbar-h) - 220px);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  background: var(--bg-elev);
  overflow: hidden;
}
/* 对比滑块内部图片是绝对定位，在 flex 居中容器里高度会塌成 0 */
.tryon-stage :deep(.cmp) {
  position: absolute;
  inset: 0;
}
.tryon-stage__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  cursor: zoom-in;
}
.tryon-stage--loading .spinner {
  position: absolute;
  width: 34px;
  height: 34px;
}
.tryon-stage__ghost {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  opacity: 0.25;
  filter: blur(2px) grayscale(0.4);
}
@media (max-width: 960px) {
  .tryon-stage {
    height: auto;
    min-height: 0;
    aspect-ratio: 3 / 4;
  }
}
</style>
