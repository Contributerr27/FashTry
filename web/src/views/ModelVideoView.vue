<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import WorkbenchShell from '../components/workbench/WorkbenchShell.vue';
import GenerateButton from '../components/workbench/GenerateButton.vue';
import GenProgress from '../components/workbench/GenProgress.vue';
import UploadBox from '../components/ui/UploadBox.vue';
import ChipGroup from '../components/ui/ChipGroup.vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';
import { downloadUrl } from '../lib/download.js';
import { refreshCredits } from '../lib/userStore.js';
import { WORK_KINDS, flattenWorks } from '../lib/works.js';

const { t } = useT();
const router = useRouter();

const preview = ref('');
const file = ref(null);
const picked = ref(null);
const allWorks = ref([]);
const workKind = ref('all');
const worksLoading = ref(false);
const prompt = ref('');
const duration = ref(8);
const resolution = ref('720p');
const aspect = ref('9:16');
const matrix = ref([]);
const allowedDurations = ref([4, 6, 8]);
const allowedResolutions = ref(['720p', '1080p']);
const allowedAspects = ref(['9:16', '16:9']);

const running = ref(false);
const progressText = ref('');
const progressPct = ref(null);
const videoUrl = ref('');
const canvasPoster = ref('');
const taskId = ref(null);
const history = ref([]);
const downloading = ref(false);

let gen = 0;
let disposed = false;

const durationOptions = computed(() =>
  allowedDurations.value.map((v) => ({
    value: v,
    label: t('modelVideoSeconds', { n: v }),
    disabled: resolution.value === '1080p' && v !== 8,
  }))
);
const resolutionOptions = computed(() => allowedResolutions.value.map((v) => ({ value: v, label: v })));
const aspectOptions = computed(() =>
  allowedAspects.value.map((v) => ({
    value: v,
    label: v === '9:16' ? t('modelVideoAspectPortrait') : v === '16:9' ? t('modelVideoAspectLandscape') : v,
  }))
);

const cost = computed(() => {
  const cell = matrix.value.find(
    (c) => Number(c.durationSeconds) === Number(duration.value) && c.resolution === resolution.value
  );
  if (cell?.credits != null) return cell.credits;
  const blocks = Math.ceil(Number(duration.value) / 2);
  const per = resolution.value === '1080p' ? 5 : 3;
  return Math.max(6, blocks * per);
});
const workKindOptions = computed(() => WORK_KINDS.map((k) => ({ value: k.value, label: t(k.labelKey) })));
const works = computed(() => {
  const list =
    workKind.value === 'all' ? allWorks.value : allWorks.value.filter((w) => w.kind === workKind.value);
  return list.slice(0, 40);
});
const hasFrame = computed(() => !!file.value || !!picked.value?.url);
const canSubmit = computed(() => hasFrame.value && !!prompt.value.trim() && !running.value);

const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.sourceImageUrl || '',
    status: h.status === 2 ? 'done' : h.status === 3 ? 'failed' : 'loading',
    badge: h.status === 3 ? t('historyStatusFailed') : h.durationSeconds ? `${h.durationSeconds}s` : '',
    active: String(taskId.value) === String(h.taskId),
    raw: h,
  }))
);

watch(resolution, (v) => {
  if (v === '1080p') duration.value = 8;
});

function onFile(f) {
  if (preview.value.startsWith('blob:')) URL.revokeObjectURL(preview.value);
  picked.value = null;
  file.value = f;
  preview.value = URL.createObjectURL(f);
}

function onFileClear() {
  if (preview.value.startsWith('blob:')) URL.revokeObjectURL(preview.value);
  file.value = null;
  picked.value = null;
  preview.value = '';
}

function pickWork(w) {
  if (!w?.url) return;
  if (preview.value.startsWith('blob:')) URL.revokeObjectURL(preview.value);
  file.value = null;
  picked.value = w;
  preview.value = w.url;
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function loadPricing() {
  try {
    const p = await api.getModelVideoPricing();
    matrix.value = p.matrix || [];
    if (p.allowedDurationSeconds?.length) allowedDurations.value = p.allowedDurationSeconds;
    if (p.allowedResolutions?.length) allowedResolutions.value = p.allowedResolutions;
    if (p.allowedAspectRatios?.length) allowedAspects.value = p.allowedAspectRatios;
    if (p.defaultDurationSeconds) duration.value = p.defaultDurationSeconds;
    if (p.defaultResolution) resolution.value = p.defaultResolution;
    if (p.defaultAspectRatio) aspect.value = p.defaultAspectRatio;
  } catch {
    /* 用本地默认档位 */
  }
}

async function loadWorks() {
  if (!auth.isLoggedIn()) {
    allWorks.value = [];
    return;
  }
  worksLoading.value = true;
  try {
    const data = await api.getUnifiedTaskHistory('all', 1, 30);
    allWorks.value = flattenWorks(data?.records || []).filter((w) => w.url);
  } catch {
    allWorks.value = [];
  } finally {
    worksLoading.value = false;
  }
}

async function loadHistory() {
  if (!auth.isLoggedIn()) return;
  try {
    const data = await api.listModelVideoTasks(1, 20);
    history.value = data.records || [];
  } catch {
    history.value = [];
  }
}

function showTask(task, { play } = { play: true }) {
  taskId.value = task.taskId;
  canvasPoster.value = task.sourceImageUrl || canvasPoster.value;
  videoUrl.value = play && task.status === 2 ? task.resultVideoUrl || '' : '';
}

async function watchTask(id) {
  const mine = ++gen;
  running.value = true;
  videoUrl.value = '';
  progressText.value = t('modelVideoQueued');
  progressPct.value = 3;
  try {
    while (!disposed && mine === gen) {
      const task = await api.getModelVideoTask(id);
      if (disposed || mine !== gen) return;
      showTask(task, { play: task.status === 2 });
      progressPct.value = task.progressPercent ?? progressPct.value;
      if (task.status === 2) {
        toast(t('toastGenerateSuccess'), 'success');
        refreshCredits();
        loadHistory();
        return;
      }
      if (task.status === 3) {
        toast(task.errorMsg || t('modelVideoFailed'), 'error');
        loadHistory();
        return;
      }
      progressText.value = task.status === 0 ? t('modelVideoQueued') : t('modelVideoGenerating');
      await sleep(4000);
    }
  } catch (e) {
    if (mine === gen) toast(i18n.formatApiError(e.message) || t('errRequestFail'), 'error');
  } finally {
    if (mine === gen) {
      running.value = false;
      progressText.value = '';
      progressPct.value = null;
    }
  }
}

async function submit() {
  if (!hasFrame.value) return toast(t('modelVideoUploadTitle'), 'error');
  if (!prompt.value.trim()) return toast(t('modelVideoPrompt'), 'error');
  if (!auth.isLoggedIn()) return router.push('/login?return=' + encodeURIComponent('/model-video'));

  canvasPoster.value = preview.value;
  videoUrl.value = '';
  running.value = true;
  progressText.value = t('localEditSubmitting');
  progressPct.value = null;
  try {
    const url = picked.value?.url || (await api.uploadBusiness(file.value));
    const r = await api.submitModelVideo({
      sourceType: 'UPLOAD',
      sourceImageUrl: url,
      prompt: prompt.value.trim(),
      durationSeconds: duration.value,
      resolution: resolution.value,
      aspectRatio: aspect.value,
    });
    taskId.value = r.taskId;
    await watchTask(r.taskId);
  } catch (e) {
    running.value = false;
    progressText.value = '';
    toast(i18n.formatApiError(e.message) || t('errRequestFail'), 'error');
  }
}

function onHistorySelect(it) {
  const h = it.raw;
  if (h.status === 2 && h.resultVideoUrl) {
    gen++;
    running.value = false;
    progressText.value = '';
    showTask(h);
    return;
  }
  if (h.status === 3) {
    showTask(h, { play: false });
    toast(h.errorMsg || t('modelVideoFailed'), 'error');
    return;
  }
  showTask(h, { play: false });
  watchTask(h.taskId);
}

async function download() {
  if (!videoUrl.value || downloading.value) return;
  downloading.value = true;
  try {
    await downloadUrl(videoUrl.value, 'fashtry-outfit.mp4');
  } finally {
    downloading.value = false;
  }
}

onMounted(() => {
  prompt.value = t('modelVideoPromptDefault');
  loadPricing();
  loadHistory();
  loadWorks();
});
onUnmounted(() => {
  disposed = true;
  gen++;
  if (preview.value.startsWith('blob:')) URL.revokeObjectURL(preview.value);
});
</script>

<template>
  <WorkbenchShell
    title-key="modelVideoTitle"
    lead-key="modelVideoLead"
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    history-title-key="modelVideoHistory"
    @history-select="onHistorySelect"
  >
    <template #inputs>
      <div class="wb-step"><span class="wb-step__num">1</span>{{ t('modelVideoStepImage') }}</div>
      <UploadBox
        :preview="preview"
        icon="video"
        :title="t('modelVideoUploadTitle')"
        :hint="t('modelVideoUploadHint')"
        aspect="3 / 4"
        @file="onFile"
        @clear="onFileClear"
      />
      <div class="mv-works-head">{{ t('modelVideoOrWorks') }}</div>
      <template v-if="!auth.isLoggedIn()">
        <p class="wb-field__hint">
          <RouterLink :to="'/login?return=' + encodeURIComponent('/model-video')">{{ t('login') }}</RouterLink>
          · {{ t('modelVideoWorksLogin') }}
        </p>
      </template>
      <template v-else>
        <ChipGroup v-if="allWorks.length" v-model="workKind" :options="workKindOptions" />
        <div v-if="works.length" class="mv-works">
          <button
            v-for="w in works"
            :key="w.key"
            type="button"
            class="mv-work"
            :class="{ on: picked?.key === w.key }"
            :title="w.label"
            @click="pickWork(w)"
          >
            <img :src="w.thumb || w.url" alt="" />
          </button>
        </div>
        <p v-else-if="!worksLoading" class="wb-field__hint">{{ t('modelVideoWorksEmpty') }}</p>
      </template>
      <div class="wb-step mv-step"><span class="wb-step__num">2</span>{{ t('modelVideoPrompt') }}</div>
      <textarea v-model="prompt" rows="5" maxlength="1800" />
    </template>

    <template #params>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('modelVideoDuration') }}</div>
        <ChipGroup v-model="duration" :options="durationOptions" variant="seg" />
        <p v-if="resolution === '1080p'" class="wb-field__hint">{{ t('modelVideo1080Hint') }}</p>
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('modelVideoResolution') }}</div>
        <ChipGroup v-model="resolution" :options="resolutionOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('modelVideoAspect') }}</div>
        <ChipGroup v-model="aspect" :options="aspectOptions" variant="seg" />
      </div>
    </template>

    <template #actions>
      <GenerateButton
        :cost="cost"
        :loading="running"
        :disabled="!canSubmit"
        :label="t('modelVideoRun')"
        @click="submit"
      />
    </template>

    <template #toolbar>
      <button v-if="videoUrl" type="button" class="btn btn-secondary btn-sm" :disabled="downloading" @click="download">
        <Icon name="download" :size="15" />{{ t('modelVideoDownload') }}
      </button>
    </template>

    <template #main>
      <GenProgress :text="progressText" :pct="progressPct" />
      <div v-if="videoUrl" class="mv-stage">
        <video :src="videoUrl" :poster="canvasPoster" controls playsinline autoplay />
      </div>
      <div v-else-if="canvasPoster" class="mv-stage">
        <img :src="canvasPoster" alt="" />
      </div>
      <div v-else class="wb-empty">
        <span class="wb-empty__icon"><Icon name="video" :size="26" /></span>
        <strong>{{ t('uiCanvasEmpty') }}</strong>
        <span>{{ t('modelVideoEmpty') }}</span>
      </div>
    </template>
  </WorkbenchShell>
</template>

<style scoped>
.mv-step {
  margin-top: 20px;
}
.mv-works-head {
  margin: 14px 0 8px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-2);
}
.mv-works {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  max-height: 220px;
  overflow: auto;
  margin-top: 8px;
}
.mv-work {
  position: relative;
  padding: 0;
  border: 1.5px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--panel-3);
  cursor: pointer;
  aspect-ratio: 3 / 4;
}
.mv-work img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.mv-work.on {
  border-color: var(--brand);
  box-shadow: 0 0 0 1px var(--brand);
}
.mv-stage {
  flex: 1;
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  background: #0b0d10;
  overflow: hidden;
}
.mv-stage video,
.mv-stage img {
  max-width: 100%;
  max-height: min(72vh, 720px);
  object-fit: contain;
}
</style>
