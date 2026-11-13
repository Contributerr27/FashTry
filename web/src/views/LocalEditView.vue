<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MaskEditor from '../components/MaskEditor.vue';
import WorkbenchShell from '../components/workbench/WorkbenchShell.vue';
import GenerateButton from '../components/workbench/GenerateButton.vue';
import UploadBox from '../components/ui/UploadBox.vue';
import ChipGroup from '../components/ui/ChipGroup.vue';
import ResultTile from '../components/ui/ResultTile.vue';
import CompareSlider from '../components/ui/CompareSlider.vue';
import ImageLightbox from '../components/ui/ImageLightbox.vue';
import FavButton from '../components/ui/FavButton.vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';
import { downloadUrl, downloadZip, stamp } from '../lib/download.js';
import { pollGeminiTask, estimateTaskProgress } from '../lib/geminiTaskPoll.js';
import { refreshCredits } from '../lib/userStore.js';

const { t } = useT();
const router = useRouter();

const FABRIC_OPTIONS = [
  { id: 'cotton', labelKey: 'localEditFabricCotton', en: 'cotton' },
  { id: 'silk', labelKey: 'localEditFabricSilk', en: 'silk' },
  { id: 'leather', labelKey: 'localEditFabricLeather', en: 'leather' },
  { id: 'denim', labelKey: 'localEditFabricDenim', en: 'denim' },
  { id: 'wool', labelKey: 'localEditFabricWool', en: 'wool tweed' },
];

const EDIT_MODES = [
  {
    id: 'color',
    icon: 'palette',
    i18nKey: 'localEditModeColor',
    prefixZh: '仅在蒙版标记区域内调整服装颜色，保持纹理、褶皱、光影与版型不变。具体要求：',
    prefixEn:
      'Adjust garment color only within the masked region; keep texture, folds, lighting, and fit unchanged. Details: ',
    phKey: 'localEditModeColorPh',
  },
  {
    id: 'accessory',
    icon: 'sparkles',
    i18nKey: 'localEditModeAccessory',
    prefixZh: '仅在蒙版标记区域内替换纽扣或配饰，保持位置自然、与服装协调。具体要求：',
    prefixEn: 'Replace buttons or accessories only within the masked region; keep placement natural. Details: ',
    phKey: 'localEditModeAccessoryPh',
  },
  {
    id: 'fabric',
    icon: 'layers',
    i18nKey: 'localEditModeFabric',
    prefixZh: '仅在蒙版标记区域内调整面料材质与质感，不改变服装版型与轮廓。具体要求：',
    prefixEn:
      'Adjust fabric material and texture only within the masked region; do not change silhouette. Details: ',
    phKey: 'localEditModeFabricPh',
  },
];

const SWATCHES = ['#ef4444', '#f97316', '#facc15', '#22c55e', '#6c4cf6', '#3b82f6', '#a855f7', '#ec4899', '#f5f5f4', '#111827'];

const sourceFile = ref(null);
const sourcePreviewUrl = ref('');
const referenceFile = ref(null);
const referencePreviewUrl = ref('');

const editMode = ref('color');
const brushSize = ref(28);
const tool = ref('brush');
const targetColor = ref('#22c55e');
const fabricMaterial = ref('cotton');
const instruction = ref('');
const variantCount = ref(1);
const consumeCredits = ref(1);

const maskEditorRef = ref(null);
const hasMask = ref(false);
const view = ref('mask');

const generating = ref(false);
/** @type {import('vue').Ref<{ taskId?: string, status: 'loading'|'done'|'failed', url: string, text: string }[]>} */
const runResults = ref([]);
const compareSrc = ref('');
const activeIdx = ref(0);
const history = ref([]);
const activeHistoryId = ref('');
const lbIndex = ref(-1);
const zipping = ref(false);

let genSession = 0;
let abortFlag = false;

const variantOptions = [1, 2, 4].map((n) => ({ value: n, label: String(n) }));
const fabricOptions = computed(() => FABRIC_OPTIONS.map((f) => ({ value: f.id, label: t(f.labelKey) })));
const instructionPlaceholder = computed(() => {
  const mode = EDIT_MODES.find((m) => m.id === editMode.value);
  return mode ? t(mode.phKey) : t('localEditInstructionPlaceholder');
});
const totalCost = computed(() => consumeCredits.value * variantCount.value);
const canGenerate = computed(() => {
  if (!sourceFile.value || generating.value) return false;
  const text = instruction.value.trim();
  if (editMode.value === 'color') return !!(targetColor.value || text);
  if (editMode.value === 'fabric') return !!(fabricMaterial.value || text);
  if (editMode.value === 'accessory') return !!(text || referenceFile.value);
  return !!text;
});
const doneResults = computed(() => runResults.value.filter((r) => r.status === 'done' && r.url));
const activeResult = computed(() => doneResults.value[activeIdx.value] || doneResults.value[0] || null);
const lbItems = computed(() =>
  doneResults.value.map((r, i) => ({
    url: r.url,
    label: `${t('uiResult')} ${i + 1}`,
    sourceType: 'image_edit',
    sourceTaskId: r.taskId,
  }))
);
const historyItems = computed(() =>
  history.value.map((h) => ({
    key: String(h.taskId),
    thumb: h.outputUrls?.[0] || h.sourceImageUrl || '',
    status: h.status === 'COMPLETED' ? 'done' : h.status === 'FAILED' ? 'failed' : 'loading',
    badge: h.status === 'FAILED' ? t('historyStatusFailed') : h.status !== 'COMPLETED' ? t('historyStatusProcessing') : '',
    active: activeHistoryId.value === String(h.taskId),
    raw: h,
  }))
);

/** 从用户中文/英文描述中解析目标色，与 Color Picker 合并。 */
function resolveColorTarget(userText) {
  const s = userText.trim();
  const sl = s.toLowerCase();
  const fromText = (en, zh) => ({ en, zh, fromUser: true });
  if (/变红|染红|涂红|成红|改红|红色|大红|鲜红/.test(s) || /\bred\b/.test(sl)) return fromText('red', '红色');
  if (/变绿|绿色|翠绿/.test(s) || /\bgreen\b/.test(sl)) return fromText('green', '绿色');
  if (/变蓝|蓝色|天蓝|深蓝/.test(s) || /\bblue\b/.test(sl)) return fromText('blue', '蓝色');
  if (/变白|白色|米白|乳白/.test(s) || /\bwhite\b/.test(sl)) return fromText('white', '白色');
  if (/变黑|黑色/.test(s) || /\bblack\b/.test(sl)) return fromText('black', '黑色');
  if (/变黄|黄色|金黄/.test(s) || /\byellow\b/.test(sl)) return fromText('yellow', '黄色');
  if (/变紫|紫色/.test(s) || /\bpurple\b/.test(sl)) return fromText('purple', '紫色');
  const hex = targetColor.value;
  return { en: hex, zh: hex, fromUser: false };
}

function buildFullInstruction() {
  const userText = instruction.value.trim();
  const loc = i18n.getLocale?.() || 'zh';

  if (editMode.value === 'color') {
    const color = resolveColorTarget(userText);
    const extra = userText ? (loc === 'en' ? ` User note: ${userText}.` : ` 用户补充：${userText}。`) : '';
    if (loc === 'en') {
      return (
        `Inpaint: change ONLY the brush-painted regions (white mask pixels) to ${color.en}. ` +
        'Every black-masked pixel must stay identical to the source — same color, texture, embroidery, and lighting. ' +
        'Do NOT recolor the whole garment or background.' +
        extra
      );
    }
    return (
      `局部重绘：仅将画笔涂抹过的区域（蒙版白色部分）改为${color.zh}；` +
      '蒙版黑色区域必须与原图完全一致，禁止整件衣服、刺绣或背景变色。' +
      extra
    );
  }

  if (editMode.value === 'fabric') {
    const opt = FABRIC_OPTIONS.find((f) => f.id === fabricMaterial.value);
    if (opt) {
      if (loc === 'en') {
        return `Modify ONLY the white-masked regions to ${opt.en} fabric texture, photorealistic, high quality. Black-masked areas must remain identical to the source.`;
      }
      return `仅将蒙版白色区域内的面料质感改为${i18n.t(opt.labelKey)}，写实高清；黑色蒙版区域保持与原图一致。`;
    }
    return userText;
  }

  if (editMode.value === 'accessory') {
    const refHint = loc === 'en' ? 'the item shown in the reference image' : '参考图中的物品';
    const desc = userText || (referenceFile.value ? refHint : '');
    if (!desc) return userText;
    if (loc === 'en') {
      return `Replace ONLY within the white-masked regions with ${desc}. Black-masked pixels must stay identical to the source.`;
    }
    return `仅在蒙版白色区域内替换为：${desc}；黑色蒙版区域保持与原图一致。`;
  }

  const mode = EDIT_MODES.find((m) => m.id === editMode.value);
  if (!mode) return userText;
  return (loc === 'en' ? mode.prefixEn : mode.prefixZh) + userText;
}

function setSourceFile(file) {
  sourceFile.value = file;
  if (sourcePreviewUrl.value) URL.revokeObjectURL(sourcePreviewUrl.value);
  sourcePreviewUrl.value = URL.createObjectURL(file);
  hasMask.value = false;
  view.value = 'mask';
}

function clearSource() {
  sourceFile.value = null;
  if (sourcePreviewUrl.value) URL.revokeObjectURL(sourcePreviewUrl.value);
  sourcePreviewUrl.value = '';
  hasMask.value = false;
  view.value = doneResults.value.length ? 'compare' : 'mask';
}

function setReference(file) {
  referenceFile.value = file;
  if (referencePreviewUrl.value) URL.revokeObjectURL(referencePreviewUrl.value);
  referencePreviewUrl.value = URL.createObjectURL(file);
}

function clearReference() {
  referenceFile.value = null;
  if (referencePreviewUrl.value) URL.revokeObjectURL(referencePreviewUrl.value);
  referencePreviewUrl.value = '';
}

function onMaskChange() {
  hasMask.value = Boolean(maskEditorRef.value?.hasMaskContent?.());
}

async function useResultAsSource(url) {
  try {
    const res = await fetch(url, { mode: 'cors' });
    if (!res.ok) throw new Error(String(res.status));
    const blob = await res.blob();
    setSourceFile(new File([blob], `local-edit-${Date.now()}.png`, { type: blob.type || 'image/png' }));
  } catch {
    toast(t('errRequestFail'), 'error');
  }
}

async function exportWorkspace() {
  if (!sourceFile.value) return toast(t('localEditErrNoImage'), 'error');
  const base = sourceFile.value.name?.replace(/\.[^.]+$/, '') || 'local-edit';
  const saveBlob = (blob, name) => {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  };
  saveBlob(sourceFile.value, `${base}-source.png`);
  const maskBlob = await maskEditorRef.value?.exportMaskBlob();
  if (maskBlob) saveBlob(maskBlob, `${base}-mask.png`);
  toast(t('localEditExportDone'), 'success');
}

async function zipAll() {
  zipping.value = true;
  try {
    const r = await downloadZip(
      doneResults.value.map((x, i) => ({ url: x.url, name: `local-edit-${i + 1}` })),
      `fashtry-local-edit-${stamp()}`
    );
    toast(r.failed ? t('uiZipPartial', { n: r.failed }) : t('uiZipDone', { n: r.ok }), r.failed ? 'info' : 'success');
  } finally {
    zipping.value = false;
  }
}

async function downloadActive() {
  if (!activeResult.value) return;
  const r = await downloadUrl(activeResult.value.url, `fashtry-local-edit-${activeIdx.value + 1}`);
  toast(r === 'downloaded' ? t('toastDownloadSuccess') : t('toastOpenInNewTabSave'), r === 'downloaded' ? 'success' : 'info');
}

function openLb(url) {
  const i = doneResults.value.findIndex((r) => r.url === url);
  if (i >= 0) lbIndex.value = i;
}

function selectResult(url) {
  const i = doneResults.value.findIndex((r) => r.url === url);
  if (i < 0) return;
  activeIdx.value = i;
  if (compareSrc.value) view.value = 'compare';
  else openLb(url);
}

async function runGenerate() {
  if (!sourceFile.value) return toast(t('localEditErrNoImage'), 'error');
  if (!canGenerate.value) return toast(t('localEditErrNoInstruction'), 'error');
  if (!maskEditorRef.value?.hasMaskContent?.()) {
    view.value = 'mask';
    return toast(t('localEditErrNoMask'), 'error');
  }
  if (!auth.isLoggedIn()) return router.push('/login?return=' + encodeURIComponent('/local-edit'));

  const maskBlob = await maskEditorRef.value.exportMaskBlob();
  if (!maskBlob) return toast(t('localEditErrNoMask'), 'error');

  const session = ++genSession;
  abortFlag = false;
  generating.value = true;
  activeIdx.value = 0;
  activeHistoryId.value = '';
  compareSrc.value = sourcePreviewUrl.value;
  const n = variantCount.value;
  runResults.value = Array.from({ length: n }, () => ({ status: 'loading', url: '', text: t('localEditStPending') }));

  const instructionText = buildFullInstruction();
  const buildForm = () => {
    const fd = new FormData();
    fd.append('file', sourceFile.value);
    fd.append('instruction', instructionText);
    if (editMode.value === 'accessory' && referenceFile.value) fd.append('reference', referenceFile.value);
    fd.append('mask', maskBlob, 'mask.png');
    return fd;
  };

  try {
    const taskIds = [];
    for (let i = 0; i < n; i++) {
      if (session !== genSession) return;
      const res = await api.submitImageEdit(buildForm());
      taskIds.push(String(res.taskId));
      Object.assign(runResults.value[i], { taskId: String(res.taskId), text: t('localEditPolling') });
    }
    await Promise.all(
      taskIds.map(async (id, idx) => {
        const final = await pollGeminiTask(
          (tid) => api.getImageEditTask(tid),
          id,
          (task, sec) => {
            if (session !== genSession) return;
            const pct = estimateTaskProgress(task, 120000);
            runResults.value[idx].text =
              task.status === 'PENDING'
                ? t('localEditQueueing', { sec })
                : pct != null
                  ? t('localEditProgress', { pct })
                  : t('localEditPolling');
          },
          () => abortFlag || session !== genSession
        );
        if (!final || session !== genSession) return;
        if (final.status === 'COMPLETED' && final.urls?.length) {
          Object.assign(runResults.value[idx], { status: 'done', url: final.urls[0], text: '' });
          if (view.value === 'mask' && doneResults.value.length === 1) view.value = 'compare';
        } else {
          Object.assign(runResults.value[idx], { status: 'failed', text: final.errorMsg || t('localEditStFailed') });
        }
      })
    );
    if (session === genSession) {
      if (doneResults.value.length) toast(t('toastGenerateSuccess'), 'success');
      loadHistory();
      refreshCredits();
    }
  } catch (e) {
    toast(i18n.formatApiError(e.message) || t('errRequestFail'), 'error');
    runResults.value.forEach((r) => {
      if (r.status === 'loading') Object.assign(r, { status: 'failed', text: t('localEditStFailed') });
    });
  } finally {
    if (session === genSession) generating.value = false;
  }
}

async function loadPricing() {
  try {
    if (!auth.isLoggedIn()) return;
    const p = await api.getImageEditPricing();
    if (p?.consumeCredits != null) consumeCredits.value = p.consumeCredits;
  } catch {
    /* ignore */
  }
}

async function loadHistory() {
  if (!auth.isLoggedIn()) return;
  try {
    const data = await api.listImageEditHistory(1, 20);
    history.value = data.records || [];
  } catch {
    history.value = [];
  }
}

function onHistorySelect(it) {
  const h = it.raw;
  if (h.status !== 'COMPLETED' || !h.outputUrls?.length) {
    toast(h.status === 'FAILED' ? h.errorMsg || t('localEditStFailed') : t('historyStatusProcessing'), 'info');
    return;
  }
  activeHistoryId.value = String(h.taskId);
  runResults.value = h.outputUrls.map((url) => ({ taskId: String(h.taskId), status: 'done', url, text: '' }));
  compareSrc.value = h.sourceImageUrl || '';
  activeIdx.value = 0;
  view.value = compareSrc.value ? 'compare' : view.value;
  if (!compareSrc.value) lbIndex.value = 0;
}

onMounted(() => {
  loadPricing();
  loadHistory();
});

onUnmounted(() => {
  abortFlag = true;
  genSession += 1;
  if (sourcePreviewUrl.value) URL.revokeObjectURL(sourcePreviewUrl.value);
  if (referencePreviewUrl.value) URL.revokeObjectURL(referencePreviewUrl.value);
});
</script>

<template>
  <WorkbenchShell
    title-key="localEditTitle"
    lead-key="localEditLead"
    history-title-key="localEditHistory"
    :history-items="historyItems"
    :history-logged-out="!auth.isLoggedIn()"
    works-kind="image_edit"
    @history-select="onHistorySelect"
  >
    <template #inputs>
      <div class="wb-step"><span class="wb-step__num">1</span>{{ t('localEditSource') }}</div>
      <UploadBox
        :preview="sourcePreviewUrl"
        icon="images"
        :hint="t('localEditPick')"
        aspect="4 / 5"
        compact
        @file="setSourceFile"
        @clear="clearSource"
      />
      <div class="wb-step le-step"><span class="wb-step__num">2</span>{{ t('uiLeMode') }}</div>
      <div class="le-modes">
        <button
          v-for="m in EDIT_MODES"
          :key="m.id"
          type="button"
          class="le-mode"
          :class="{ on: editMode === m.id }"
          @click="editMode = m.id"
        >
          <span class="le-mode__icon"><Icon :name="m.icon" :size="18" /></span>
          <span>{{ t(m.i18nKey) }}</span>
          <Icon v-if="editMode === m.id" name="check" :size="15" class="le-mode__check" />
        </button>
      </div>
    </template>

    <template #params>
      <div v-if="editMode === 'color'" class="wb-field">
        <div class="wb-field__label">{{ t('localEditColorPick') }}<small>{{ targetColor }}</small></div>
        <div class="le-swatches">
          <button
            v-for="c in SWATCHES"
            :key="c"
            type="button"
            class="le-swatch"
            :class="{ on: targetColor.toLowerCase() === c }"
            :style="{ background: c }"
            :title="c"
            @click="targetColor = c"
          ></button>
          <label class="le-swatch le-swatch--custom" :title="t('localEditColorPick')">
            <input v-model="targetColor" type="color" />
            <Icon name="plus" :size="14" />
          </label>
        </div>
        <p class="wb-field__hint">{{ t('localEditColorHint') }}</p>
      </div>

      <div v-if="editMode === 'fabric'" class="wb-field">
        <div class="wb-field__label">{{ t('localEditFabricMaterial') }}</div>
        <ChipGroup v-model="fabricMaterial" :options="fabricOptions" :cols="3" />
        <p class="wb-field__hint">{{ t('localEditFabricHint') }}</p>
      </div>

      <div class="wb-field">
        <div class="wb-field__label">{{ t('uiLeDescribe') }}</div>
        <textarea v-model="instruction" rows="3" :placeholder="instructionPlaceholder"></textarea>
      </div>

      <div v-if="editMode === 'accessory'" class="wb-field">
        <div class="wb-field__label">{{ t('localEditReference') }}</div>
        <div class="le-ref">
          <UploadBox
            :preview="referencePreviewUrl"
            icon="images"
            compact
            aspect="1 / 1"
            :hint="t('localEditReferencePick')"
            @file="setReference"
            @clear="clearReference"
          />
        </div>
        <p class="wb-field__hint">{{ t('localEditReferenceHint') }}</p>
      </div>

      <div class="wb-field">
        <div class="wb-field__label">{{ t('uiLeVariants') }}</div>
        <ChipGroup v-model="variantCount" :options="variantOptions" variant="seg" />
      </div>
    </template>

    <template #actions>
      <GenerateButton
        :cost="totalCost"
        :loading="generating"
        :disabled="!canGenerate"
        :label="t('localEditSubmit')"
        @click="runGenerate"
      />
    </template>

    <template #toolbar>
      <div v-if="sourcePreviewUrl || doneResults.length" class="chip-group chip-group--seg le-tabs">
        <button
          type="button"
          class="chip"
          :class="{ 'chip--active': view === 'mask' }"
          :disabled="!sourcePreviewUrl"
          @click="view = 'mask'"
        >
          <Icon name="brush" :size="14" />{{ t('uiLeTabMask') }}
        </button>
        <button
          type="button"
          class="chip"
          :class="{ 'chip--active': view === 'compare' }"
          :disabled="!activeResult || !compareSrc"
          @click="view = 'compare'"
        >
          <Icon name="compare" :size="14" />{{ t('uiLeTabCompare') }}
        </button>
      </div>
      <template v-if="view === 'mask' && sourcePreviewUrl">
        <button type="button" class="btn btn-ghost btn-sm" :title="t('uiLeExportMask')" @click="exportWorkspace">
          <Icon name="download" :size="15" />{{ t('localEditExport') }}
        </button>
      </template>
      <template v-else-if="view === 'compare' && activeResult">
        <FavButton
          :url="activeResult.url"
          source-type="image_edit"
          :source-task-id="activeResult.taskId"
          variant="toolbar"
        />
        <button type="button" class="btn btn-ghost btn-sm" :title="t('uiLeUseAsSource')" @click="useResultAsSource(activeResult.url)">
          <Icon name="brush" :size="15" />
        </button>
        <button type="button" class="btn btn-ghost btn-sm" @click="openLb(activeResult.url)">
          <Icon name="zoomIn" :size="15" />
        </button>
        <button type="button" class="btn btn-primary btn-sm" @click="downloadActive">
          <Icon name="download" :size="15" />{{ t('uiDownload') }}
        </button>
      </template>
    </template>

    <template #main>
      <div class="le-stage">
        <MaskEditor
          v-if="sourcePreviewUrl"
          v-show="view === 'mask'"
          ref="maskEditorRef"
          v-model:tool="tool"
          v-model:brush-size="brushSize"
          :image-src="sourcePreviewUrl"
          mask-tint="mint"
          @mask-change="onMaskChange"
        />
        <div v-if="view === 'compare' && activeResult && compareSrc" class="le-compare">
          <CompareSlider :before="compareSrc" :after="activeResult.url" aspect="" />
        </div>
        <div v-else-if="!sourcePreviewUrl" class="wb-empty">
          <span class="wb-empty__icon"><Icon name="brush" :size="26" /></span>
          <strong>{{ t('uiLeEmptyTitle') }}</strong>
          <span>{{ t('uiLeEmptyHint') }}</span>
        </div>
      </div>

      <div v-if="runResults.length" class="le-results">
        <div class="le-results__head">
          <span>{{ t('localEditResult') }}</span>
          <button
            v-if="doneResults.length > 1"
            type="button"
            class="btn btn-ghost btn-sm"
            :disabled="zipping"
            @click="zipAll"
          >
            <Icon name="archive" :size="14" />{{ t('uiDownloadAllZip') }}
          </button>
        </div>
        <div class="le-results__row">
          <ResultTile
            v-for="(r, i) in runResults"
            :key="i + r.url"
            class="le-result"
            :class="{ 'le-result--active': view === 'compare' && activeResult && activeResult.url === r.url }"
            :url="r.url"
            :status="r.status"
            :status-text="r.text"
            :label="String(i + 1)"
            aspect="1 / 1"
            source-type="image_edit"
            :source-task-id="r.taskId"
            :filename="`fashtry-local-edit-${i + 1}`"
            @zoom="selectResult(r.url)"
          />
        </div>
      </div>
    </template>
  </WorkbenchShell>
  <ImageLightbox v-model:index="lbIndex" :items="lbItems" filename-prefix="fashtry-local-edit" />
</template>

<style scoped>
.le-step {
  margin-top: 20px;
}
.le-modes {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.le-mode {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--radius);
  border: 1.5px solid var(--line);
  background: var(--bg-elev);
  color: var(--text-2);
  font: inherit;
  font-size: 0.84rem;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.le-mode:hover {
  border-color: var(--line-strong);
  color: var(--text);
}
.le-mode.on {
  border-color: var(--brand);
  color: var(--text);
}
.le-mode__icon {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--panel-2);
  color: var(--text-2);
}
.le-mode.on .le-mode__icon {
  background: var(--brand-soft);
  color: var(--brand);
}
.le-mode__check {
  margin-left: auto;
  color: var(--brand);
}
.le-swatches {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 6px;
}
.le-swatch {
  position: relative;
  aspect-ratio: 1;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  cursor: pointer;
  padding: 0;
}
.le-swatch.on {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
.le-swatch--custom {
  display: flex;
  align-items: center;
  justify-content: center;
  background: conic-gradient(#ef4444, #facc15, #22c55e, #3b82f6, #a855f7, #ef4444);
  color: #fff;
  overflow: hidden;
}
.le-swatch--custom input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}
.le-ref {
  max-width: 140px;
}
.le-tabs {
  margin-right: auto;
}
.le-tabs .chip {
  gap: 5px;
}
.le-stage {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--topbar-h) - 280px);
  min-height: 460px;
}
.le-compare {
  position: relative;
  flex: 1;
  min-height: 0;
  border-radius: var(--radius);
  background: var(--bg-elev);
  overflow: hidden;
}
.le-compare :deep(.cmp) {
  position: absolute;
  inset: 0;
}
.le-results {
  margin-top: 14px;
}
.le-results__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-2);
}
.le-results__row {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 140px));
  gap: 10px;
}
.le-result--active {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
  border-radius: var(--radius);
}
@media (max-width: 960px) {
  .le-stage {
    height: auto;
    min-height: 0;
  }
  .le-stage :deep(.me__viewport) {
    min-height: 360px;
  }
  .le-compare {
    min-height: 360px;
  }
}
</style>
