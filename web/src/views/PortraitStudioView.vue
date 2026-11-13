<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import WorkbenchShell from '../components/workbench/WorkbenchShell.vue';
import GenerateButton from '../components/workbench/GenerateButton.vue';
import GenProgress from '../components/workbench/GenProgress.vue';
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

const STYLE_META = {
  KOREAN: { key: 'portraitStyleKorean', thumb: '/static/styles/style-korean.jpg' },
  JAPANESE: { key: 'portraitStyleJapanese', thumb: '/static/styles/style-japanese.jpg' },
  EDITORIAL: { key: 'portraitStyleEditorial', thumb: '/static/styles/style-editorial.jpg' },
  TRAVEL: { key: 'portraitStyleTravel', thumb: '/static/styles/style-travel.jpg' },
  ID_PHOTO: { key: 'portraitStyleIdPhoto' },
};
const MAKEUP_KEYS = { ORIGINAL: 'portraitMakeupOriginal', LIGHT: 'portraitMakeupLight', HEAVY: 'portraitMakeupHeavy' };

const files = ref([]);
let fileId = 0;
const maxSources = ref(3);
const maxOutputs = ref(4);
const styleKeys = ref(['KOREAN', 'JAPANESE', 'EDITORIAL', 'TRAVEL']);

const stylePreset = ref('KOREAN');
const makeupPreset = ref('ORIGINAL');
const outfitMode = ref('STYLE');
const outputCount = ref(2);
const aspectRatio = ref('3:4');
const creditsPerImage = ref(1);

const outputs = ref([]);
const parentTaskId = ref(null);
const regenSlot = ref(null);
const history = ref([]);
const lbIndex = ref(-1);
const zipping = ref(false);

const job = useGeminiJob({ getTask: (id) => api.getPortraitStudioTask(id), estMs: 240000 });

const cost = computed(() => outputCount.value * creditsPerImage.value);
const canSubmit = computed(() => files.value.length > 0 && !job.running.value);

const makeupOptions = computed(() => Object.entries(MAKEUP_KEYS).map(([v, k]) => ({ value: v, label: t(k) })));
const outfitOptions = computed(() => [
  { value: 'STYLE', label: t('portraitStudioOutfitStyle') },
  { value: 'KEEP_REFERENCE', label: t('portraitStudioOutfitKeep') },
]);
const countOptions = computed(() =>
  Array.from({ length: maxOutputs.value }, (_, i) => ({ value: i + 1, label: String(i + 1) }))
);
const aspectOptions = ['1:1', '3:4', '4:3', '9:16'].map((v) => ({ value: v, label: v }));
const tileAspect = computed(() => aspectRatio.value.replace(':', ' / '));

const lbItems = computed(() =>
  outputs.value.map((url, i) => ({
    url,
    label: t('portraitStudioOutItem', { n: i + 1 }),
    sourceType: 'portrait',
    sourceTaskId: parentTaskId.value,
  }))
);
const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.outputUrls?.[0] || h.sourceImageUrls?.[0] || h.sourceImageUrl || '',
    status: h.status === 'COMPLETED' ? 'done' : h.status === 'FAILED' ? 'failed' : 'loading',
    badge: h.status === 'FAILED' ? t('historyStatusFailed') : h.status !== 'COMPLETED' ? t('historyStatusProcessing') : '',
    active: String(parentTaskId.value) === String(h.taskId),
    raw: h,
  }))
);

function addFiles(list) {
  const room = maxSources.value - files.value.length;
  list.slice(0, room).forEach((file) => files.value.push({ id: ++fileId, file, preview: URL.createObjectURL(file) }));
}
function removeFile(id) {
  const i = files.value.findIndex((f) => f.id === id);
  if (i >= 0) {
    URL.revokeObjectURL(files.value[i].preview);
    files.value.splice(i, 1);
  }
}

async function loadConfig() {
  try {
    const c = await api.getPortraitStudioConfig();
    maxSources.value = c.maxSourceImages ?? 3;
    maxOutputs.value = c.maxOutputs ?? 4;
    if (c.stylePresets?.length) styleKeys.value = c.stylePresets;
    if (!styleKeys.value.includes(stylePreset.value)) stylePreset.value = styleKeys.value[0];
  } catch {
    /* ignore */
  }
}
async function loadPricing() {
  try {
    const p = await api.getPortraitStudioPricing(outputCount.value);
    creditsPerImage.value = p.creditsPerImage ?? 1;
  } catch {
    /* ignore */
  }
}
async function loadHistory() {
  if (!auth.isLoggedIn()) return;
  try {
    const data = await api.listPortraitStudioHistory(1, 20);
    history.value = data.records || [];
  } catch {
    history.value = [];
  }
}

async function submit() {
  if (!files.value.length) return toast(t('portraitStudioErrNoFile'), 'error');
  if (!auth.isLoggedIn()) return router.push('/login?return=' + encodeURIComponent('/portrait-studio'));

  outputs.value = [];
  parentTaskId.value = null;
  const fd = new FormData();
  files.value.forEach((f) => fd.append('files', f.file));
  fd.append('stylePreset', stylePreset.value);
  fd.append('makeupPreset', makeupPreset.value);
  fd.append('outfitMode', outfitMode.value);
  fd.append('outputCount', String(outputCount.value));
  fd.append('aspectRatio', aspectRatio.value);

  try {
    const r = await job.run(() => api.submitPortraitStudio(fd), {
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
  if (!parentTaskId.value) return toast(t('portraitStudioRegenerateNeedParent'), 'info');
  if (regenSlot.value != null) return;
  regenSlot.value = i;
  try {
    const r = await job.run(() => api.regeneratePortraitStudioSlot(parentTaskId.value, i), {
      busyKey: 'suiteTileGenerating',
    });
    if (!r) return;
    if (r.final.status === 'COMPLETED') {
      const fresh = await api.getPortraitStudioTask(parentTaskId.value);
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
  parentTaskId.value = h.taskId;
}

async function zipAll() {
  zipping.value = true;
  try {
    const r = await downloadZip(
      outputs.value.map((url, i) => ({ url, name: `portrait-${i + 1}` })),
      `fashtry-portrait-${stamp()}`
    );
    toast(r.failed ? t('uiZipPartial', { n: r.failed }) : t('uiZipDone', { n: r.ok }), r.failed ? 'info' : 'success');
  } finally {
    zipping.value = false;
  }
}

watch(outputCount, () => loadPricing());

onMounted(() => {
  loadConfig();
  loadPricing();
  loadHistory();
});
onUnmounted(() => files.value.forEach((f) => URL.revokeObjectURL(f.preview)));
</script>

<template>
  <WorkbenchShell
    title-key="portraitStudioTitle"
    lead-key="portraitStudioLead"
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    history-title-key="portraitStudioHistoryTitle"
    works-kind="portrait"
    @history-select="onHistorySelect"
  >
    <template #inputs>
      <div class="wb-step"><span class="wb-step__num">1</span>{{ t('portraitStudioStepUpload') }}</div>
      <UploadBox
        multiple
        :items="files"
        :max="maxSources"
        icon="portrait"
        :title="t('portraitStudioUploadTitle')"
        :hint="t('portraitStudioMaxSelfies', { n: maxSources })"
        @files="addFiles"
        @remove="removeFile"
      />
      <div class="wb-step ps-step"><span class="wb-step__num">2</span>{{ t('portraitStudioPickStyle') }}</div>
      <div class="styles">
        <button
          v-for="k in styleKeys"
          :key="k"
          type="button"
          class="style-card"
          :class="{ on: stylePreset === k }"
          @click="stylePreset = k"
        >
          <span class="style-card__art">
            <img v-if="STYLE_META[k]?.thumb" :src="STYLE_META[k].thumb" :alt="t(STYLE_META[k].key)" />
            <Icon v-else name="portrait" :size="22" />
          </span>
          <span class="style-card__name">{{ t(STYLE_META[k]?.key || k) }}</span>
          <span v-if="stylePreset === k" class="style-card__check"><Icon name="check" :size="12" :stroke-width="3" /></span>
        </button>
      </div>
    </template>

    <template #params>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('portraitStudioPickMakeup') }}</div>
        <ChipGroup v-model="makeupPreset" :options="makeupOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('portraitStudioOutfitMode') }}</div>
        <ChipGroup v-model="outfitMode" :options="outfitOptions" variant="seg" />
        <p class="wb-field__hint">{{ t('portraitStudioOutfitHint') }}</p>
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('portraitStudioOutputCount') }}</div>
        <ChipGroup v-model="outputCount" :options="countOptions" variant="seg" />
      </div>
      <div class="wb-field">
        <div class="wb-field__label">{{ t('portraitStudioAspect') }}</div>
        <ChipGroup v-model="aspectRatio" :options="aspectOptions" variant="seg" />
      </div>
    </template>

    <template #actions>
      <GenerateButton
        :cost="cost"
        :loading="job.running.value && regenSlot == null"
        :disabled="!canSubmit"
        :label="t('portraitStudioRun')"
        @click="submit"
      />
    </template>

    <template #toolbar>
      <button v-if="outputs.length > 1" type="button" class="btn btn-secondary btn-sm" :disabled="zipping" @click="zipAll">
        <Icon name="archive" :size="15" />{{ t('uiDownloadAllZip') }}
      </button>
    </template>

    <template #main>
      <GenProgress :text="job.progressText.value" :pct="job.progressPct.value" />
      <div v-if="outputs.length" class="wb-results wb-results--lg">
        <ResultTile
          v-for="(url, i) in outputs"
          :key="i + url"
          :url="url"
          :aspect="tileAspect"
          :label="t('portraitStudioOutItem', { n: i + 1 })"
          source-type="portrait"
          :source-task-id="String(parentTaskId || '')"
          :filename="`fashtry-portrait-${i + 1}`"
          :regenerable="!!parentTaskId"
          :regenerating="regenSlot === i"
          @zoom="lbIndex = i"
          @regenerate="regenerate(i)"
        />
      </div>
      <div v-else-if="job.running.value" class="wb-results wb-results--lg">
        <ResultTile v-for="i in outputCount" :key="i" status="loading" :aspect="tileAspect" :label="String(i)" />
      </div>
      <div v-else class="wb-empty">
        <span class="wb-empty__icon"><Icon name="portrait" :size="26" /></span>
        <strong>{{ t('uiCanvasEmpty') }}</strong>
        <span>{{ t('portraitStudioResultEmpty') }}</span>
      </div>
    </template>
  </WorkbenchShell>
  <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-portrait" />
</template>

<style scoped>
.ps-step {
  margin-top: 20px;
}
.styles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.style-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px 6px 8px;
  border-radius: var(--radius);
  border: 1.5px solid var(--line);
  background: var(--bg-elev);
  color: var(--text-2);
  font: inherit;
  cursor: pointer;
  transition: border-color var(--transition), color var(--transition);
}
.style-card:hover {
  border-color: var(--line-strong);
  color: var(--text);
}
.style-card.on {
  border-color: var(--brand);
  color: var(--text);
}
.style-card__art {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 3 / 4;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--panel-3);
  color: var(--text-3);
}
.style-card__art img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 18%;
  display: block;
}
.style-card__name {
  font-size: 0.8rem;
  font-weight: 600;
  text-align: center;
}
.style-card__check {
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
</style>
