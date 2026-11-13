<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import WorkbenchShell from '../components/workbench/WorkbenchShell.vue';
import GenerateButton from '../components/workbench/GenerateButton.vue';
import GenProgress from '../components/workbench/GenProgress.vue';
import EcomTabs from '../components/workbench/EcomTabs.vue';
import UploadBox from '../components/ui/UploadBox.vue';
import ChipGroup from '../components/ui/ChipGroup.vue';
import ImageLightbox from '../components/ui/ImageLightbox.vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';
import { downloadZip, stamp } from '../lib/download.js';
import { suiteImageLabel } from '../lib/geminiTaskPoll.js';
import { refreshCredits } from '../lib/userStore.js';
import { useGeminiJob } from '../composables/useGeminiJob.js';

const { t, locale } = useT();
const router = useRouter();

const OUTPUT_LANGS = ['zh', 'en', 'ja', 'ko', 'es', 'fr', 'de', 'pt', 'ru', 'ar', 'th', 'vi', 'id', 'it'];
const PLAN = [
  'ECOM_WHITE_MAIN',
  'ECOM_PREMIUM_MAIN',
  'SCENE_1',
  'SCENE_2',
  'DETAIL_1',
  'DETAIL_2',
  'DETAIL_3',
  'SELL_1',
  'SELL_2',
  'DETAIL_LONG_STRIP',
];
const sourceFile = ref(null);
const sourcePreview = ref('');
const outputLanguage = ref(i18n.getLocale() === 'en' ? 'en' : 'zh');
const modelGender = ref('female');
const aspectRatio = ref('3:4');
const marketplace = ref('taobao');
const productBrief = ref('');
const briefing = ref(false);
const consumeCredits = ref(10);

const MARKETPLACES = [
  { value: 'taobao', key: 'suitePlatformTaobao' },
  { value: 'amazon', key: 'suitePlatformAmazon' },
  { value: 'tiktok', key: 'suitePlatformTiktok' },
  { value: 'dtc', key: 'suitePlatformDtc' },
];
const STRUCTURE = [
  { count: 2, key: 'suiteRowMain' },
  { count: 2, key: 'suiteGroupSells' },
  { count: 2, key: 'suiteGroupScenes' },
  { count: 3, key: 'suiteGroupDetails' },
  { count: 1, key: 'suiteGroupLongStrip' },
];

const urls = ref([]);
const labels = ref([]);
const parentTaskId = ref(null);
const regenSlot = ref(null);
const history = ref([]);
const lbIndex = ref(-1);
const zipping = ref(false);

const job = useGeminiJob({ getTask: (id) => api.getClothingProductSuiteTask(id), estMs: 600000, busyKey: 'suiteSlowHint' });

const canSubmit = computed(() => !!sourceFile.value && !job.running.value && !briefing.value);
const genderOptions = computed(() => [
  { value: 'female', label: t('packshotGenderFemale') },
  { value: 'male', label: t('packshotGenderMale') },
  { value: 'neutral', label: t('packshotGenderNeutral') },
]);
const aspectOptions = ['1:1', '3:4', '4:3', '9:16'].map((v) => ({ value: v, label: v }));

/** 已出图：label → { url, slotIndex } */
const bySlot = computed(() => {
  const m = {};
  urls.value.forEach((url, i) => {
    if (!url) return;
    const lab = labels.value[i] || PLAN[i] || `SLOT_${i}`;
    m[lab] = { url, slotIndex: i };
  });
  return m;
});

const mosaicItems = computed(() => {
  void locale.value;
  const running = job.running.value && regenSlot.value == null;
  return PLAN.map((label, i) => {
    const hit = bySlot.value[label];
    return {
      index: i,
      label,
      text: suiteImageLabel(label, i18n),
      url: hit?.url || '',
      slotIndex: hit?.slotIndex ?? i,
      status: hit?.url ? 'done' : running ? 'loading' : 'idle',
    };
  });
});

const doneCount = computed(() => mosaicItems.value.filter((it) => it.status === 'done').length);
const flatDone = computed(() => mosaicItems.value.filter((it) => it.status === 'done'));
const lbItems = computed(() =>
  flatDone.value.map((it) => ({ url: it.url, label: it.text, sourceType: 'suite', sourceTaskId: parentTaskId.value }))
);

const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.outputUrls?.[0] || h.sourceImageUrl || '',
    status: h.status === 'COMPLETED' ? 'done' : h.status === 'FAILED' ? 'failed' : 'loading',
    badge:
      h.status === 'FAILED'
        ? t('historyStatusFailed')
        : h.status !== 'COMPLETED'
          ? t('historyStatusProcessing')
          : h.parentTaskId
            ? t('suiteRegenerate')
            : '',
    active: String(parentTaskId.value) === String(h.taskId),
    raw: h,
  }))
);

function openLb(url) {
  const i = flatDone.value.findIndex((x) => x.url === url);
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

async function loadPricing() {
  try {
    const p = await api.getClothingProductSuitePricing();
    consumeCredits.value = p.consumeCredits ?? (p.stepCount ?? 10) * (p.creditsPerStep ?? 1);
  } catch {
    /* ignore */
  }
}
async function loadHistory() {
  if (!auth.isLoggedIn()) return;
  try {
    const data = await api.listClothingProductSuiteHistory(1, 20);
    history.value = data.records || [];
  } catch {
    history.value = [];
  }
}

async function aiWrite() {
  if (briefing.value || job.running.value) return;
  if (!sourceFile.value) return toast(t('suiteAiWriteNeedImage'), 'error');
  if (!auth.isLoggedIn()) return router.push('/login?return=' + encodeURIComponent('/clothing-suite'));
  const fd = new FormData();
  fd.append('file', sourceFile.value);
  fd.append('outputLanguage', outputLanguage.value);
  fd.append('marketplace', marketplace.value);
  fd.append('modelGender', modelGender.value);
  briefing.value = true;
  try {
    const data = await api.writeClothingProductSuiteAiBrief(fd);
    const brief = (data && data.brief ? String(data.brief) : '').trim();
    if (!brief) throw new Error(t('suiteAiWriteFail'));
    productBrief.value = brief;
    toast(t('suiteAiWriteDone'), 'success');
  } catch (e) {
    toast(e.message || t('suiteAiWriteFail'), 'error');
  } finally {
    briefing.value = false;
  }
}

async function submit() {
  if (!sourceFile.value) return toast(t('localEditErrNoImage'), 'error');
  if (!auth.isLoggedIn()) return router.push('/login?return=' + encodeURIComponent('/clothing-suite'));
  urls.value = [];
  labels.value = [];
  parentTaskId.value = null;
  const fd = new FormData();
  fd.append('file', sourceFile.value);
  fd.append('outputLanguage', outputLanguage.value);
  fd.append('modelGender', modelGender.value);
  fd.append('aspectRatio', aspectRatio.value);
  fd.append('marketplace', marketplace.value);
  if (productBrief.value.trim()) fd.append('productBrief', productBrief.value.trim());
  try {
    const r = await job.run(() => api.submitClothingProductSuite(fd), {
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
  if (!parentTaskId.value) return toast(t('suiteRegenerateNeedParent'), 'info');
  if (regenSlot.value != null) return;
  regenSlot.value = slotIndex;
  try {
    const r = await job.run(() => api.regenerateClothingSuiteSlot(parentTaskId.value, slotIndex), {
      busyKey: 'suiteTileGenerating',
    });
    if (!r) return;
    if (r.final.status === 'COMPLETED') {
      const fresh = await api.getClothingProductSuiteTask(parentTaskId.value);
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
    toast(t('suiteLoadHistoryNeedCompleted'), 'info');
    return;
  }
  if (h.parentTaskId) {
    try {
      const parent = await api.getClothingProductSuiteTask(h.parentTaskId);
      if (parent?.urls?.length) {
        urls.value = parent.urls;
        labels.value = parent.imageLabels || [];
        parentTaskId.value = h.parentTaskId;
        return;
      }
    } catch {
      /* fallthrough */
    }
    toast(t('suiteLoadHistoryCannotRestore'), 'info');
    return;
  }
  urls.value = h.outputUrls;
  labels.value = h.imageLabels || [];
  parentTaskId.value = h.taskId;
}

async function zipAll() {
  zipping.value = true;
  try {
    const r = await downloadZip(
      flatDone.value.map((it, i) => ({ url: it.url, name: `${String(i + 1).padStart(2, '0')}-${it.text.replace(/\s+/g, '')}` })),
      `fashtry-suite-${stamp()}`
    );
    toast(r.failed ? t('uiZipPartial', { n: r.failed }) : t('uiZipDone', { n: r.ok }), r.failed ? 'info' : 'success');
  } finally {
    zipping.value = false;
  }
}

onMounted(() => {
  loadPricing();
  loadHistory();
});
onUnmounted(() => {
  if (sourcePreview.value) URL.revokeObjectURL(sourcePreview.value);
});
</script>

<template>
  <WorkbenchShell
    title-key="suiteTitle"
    lead-key="suiteLead"
    hide-params
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    works-kind="suite"
    @history-select="onHistorySelect"
  >
    <template #head-extra><EcomTabs /></template>

    <template #inputs>
      <div class="wb-step"><span class="wb-step__num">1</span>{{ t('suiteStepUpload') }}</div>
      <UploadBox
        :preview="sourcePreview"
        icon="shirt"
        :hint="t('suiteDropHint')"
        aspect="3 / 4"
        @file="setSource"
        @clear="clearSource"
      />

      <div class="wb-field">
        <div class="wb-field__label">{{ t('suiteSettings') }}</div>
        <div class="suite-settings">
          <label class="suite-select">
            <span>{{ t('suitePlatform') }}</span>
            <select v-model="marketplace">
              <option v-for="p in MARKETPLACES" :key="p.value" :value="p.value">{{ t(p.key) }}</option>
            </select>
          </label>
          <label class="suite-select">
            <span>{{ t('suiteOutputLangShort') }}</span>
            <select v-model="outputLanguage">
              <option v-for="v in OUTPUT_LANGS" :key="v" :value="v">
                {{ t('suiteLang' + v.charAt(0).toUpperCase() + v.slice(1)) }}
              </option>
            </select>
          </label>
        </div>
        <div class="wb-field__label">{{ t('packshotGender') }}</div>
        <ChipGroup v-model="modelGender" :options="genderOptions" variant="seg" />
        <div class="wb-field__label">{{ t('productRetouchAspect') }}</div>
        <ChipGroup v-model="aspectRatio" :options="aspectOptions" variant="seg" />
      </div>

      <div class="wb-field">
        <div class="wb-field__label">
          <span>{{ t('suiteBrief') }}</span>
          <button
            type="button"
            class="suite-ai-write"
            :disabled="briefing || job.running.value || !sourceFile"
            @click="aiWrite"
          >
            <Icon name="sparkles" :size="14" />
            {{ briefing ? t('suiteAiWriteLoading') : t('suiteAiWrite') }}
          </button>
        </div>
        <textarea
          v-model="productBrief"
          class="suite-brief"
          rows="5"
          :placeholder="t('suiteBriefHint')"
          :disabled="briefing"
        />
      </div>

      <div class="wb-field">
        <div class="wb-field__label">{{ t('suiteStructure') }}</div>
        <ul class="suite-plan">
          <li v-for="row in STRUCTURE" :key="row.key">
            <span>{{ row.count }}</span>{{ t(row.key) }}
          </li>
        </ul>
      </div>
    </template>

    <template #actions>
      <GenerateButton
        :cost="consumeCredits"
        :loading="job.running.value && regenSlot == null"
        :disabled="!canSubmit"
        :label="t('suiteSubmitCount', { n: PLAN.length })"
        @click="submit"
      />
    </template>

    <template #toolbar>
      <span class="suite-count">{{ t('suiteGeneratedCount', { n: doneCount, total: PLAN.length }) }}</span>
      <button
        v-if="flatDone.length > 1"
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
      <div class="suite-mosaic">
        <article
          v-for="it in mosaicItems"
          :key="it.label"
          class="suite-card"
          :class="'suite-card--' + it.index"
          @click="it.status === 'done' && openLb(it.url)"
        >
          <img v-if="it.url" :src="it.url" alt="" />
          <div v-else-if="it.status === 'loading'" class="suite-card__state">
            <span class="spinner"></span>
            <span>{{ t('suiteTileQueued') }}</span>
          </div>
          <div v-else class="suite-card__state">
            <Icon name="shirt" :size="22" />
            <span>{{ t('suiteSlotIdle') }}</span>
          </div>
          <span class="suite-card__num">{{ String(it.index + 1).padStart(2, '0') }}</span>
          <span class="suite-card__label">{{ it.text }}</span>
          <div v-if="it.status === 'done'" class="suite-card__ops">
            <button
              v-if="parentTaskId && !job.running.value"
              type="button"
              :disabled="regenSlot === it.slotIndex"
              @click.stop="regenerate(it.slotIndex)"
            >
              {{ regenSlot === it.slotIndex ? t('suiteTileGenerating') : t('suiteRegenerate') }}
            </button>
          </div>
        </article>
      </div>
    </template>
  </WorkbenchShell>
  <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-suite" />
</template>

<style scoped>
.suite-settings {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}
.suite-select {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.72rem;
  color: var(--text-2);
}
.suite-select select {
  width: 100%;
}
.suite-ai-write {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--brand);
  font: inherit;
  font-size: 0.75rem;
  font-weight: 650;
  cursor: pointer;
}
.suite-ai-write:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.suite-brief {
  width: 100%;
  resize: vertical;
  min-height: 108px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line);
  background: var(--bg-elev);
  color: inherit;
  font: inherit;
  font-size: 0.8rem;
  line-height: 1.5;
}
.suite-brief:disabled {
  opacity: 0.7;
}
.suite-plan {
  list-style: none;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.suite-plan li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: var(--radius-sm);
  background: var(--bg-elev);
  border: 1px solid var(--line);
  font-size: 0.75rem;
  color: var(--text-2);
}
.suite-plan li span {
  min-width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 700;
  font-size: 0.7rem;
}
.suite-count {
  margin-right: auto;
  font-size: 0.8rem;
  color: var(--text-2);
}
.suite-mosaic {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  grid-auto-rows: minmax(140px, 18vh);
  gap: 10px;
  min-height: 520px;
}
.suite-card {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid var(--line);
  background:
    radial-gradient(120% 80% at 100% 0%, rgba(108, 76, 246, 0.08), transparent 55%),
    var(--bg-elev);
  cursor: default;
}
.suite-card:has(img) {
  cursor: zoom-in;
}
.suite-card img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.suite-card--0 {
  grid-column: 1;
  grid-row: 1 / 3;
}
.suite-card--9 {
  grid-row: span 2;
}
.suite-card__state {
  height: 100%;
  min-height: 140px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-3);
  font-size: 0.75rem;
}
.suite-card__num {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(5, 5, 8, 0.72);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}
.suite-card__label {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 10px;
  z-index: 1;
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(5, 5, 8, 0.62);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 650;
}
.suite-card__ops {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  opacity: 0;
  transition: opacity 0.15s;
}
.suite-card:hover .suite-card__ops {
  opacity: 1;
}
.suite-card__ops button {
  border: 0;
  border-radius: 8px;
  padding: 5px 8px;
  background: rgba(20, 23, 28, 0.88);
  color: #fff;
  font: inherit;
  font-size: 0.72rem;
  cursor: pointer;
}
@media (max-width: 960px) {
  .suite-mosaic {
    grid-template-columns: 1fr 1fr;
    grid-auto-rows: minmax(160px, 28vw);
  }
  .suite-card--0 {
    grid-column: 1 / -1;
    grid-row: auto;
    min-height: 240px;
  }
  .suite-card--9 {
    grid-column: 1 / -1;
    grid-row: auto;
    min-height: 280px;
  }
}
</style>
