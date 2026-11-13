<script setup>
/**
 * Create Photos — Fashion Studio 主工作台
 * 按 2026-11 设计稿实现：4 步向导 + 左配置 / 右 Before-After + 结果条。
 *
 * 当前是纯前端可交互演示：生成/下载/加入项目走本地反馈，不调用任何生成接口、
 * 不消耗点数。接入真实任务时，把 generate() 换成现有 useGeminiJob / api 调用即可。
 */
import { computed, onBeforeUnmount, ref } from 'vue';
import { RouterLink } from 'vue-router';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';
import { userState } from '../lib/userStore.js';
import StudioIcon from '../components/ui/StudioIcon.vue';

const { t } = useT();

const STEPS = [
  { n: 1, key: 'stStep1', target: 'cp-sec-1' },
  { n: 2, key: 'stStep2', target: 'cp-sec-2' },
  { n: 3, key: 'stStep3', target: 'cp-sec-3' },
  { n: 4, key: 'stStep4', target: 'cp-sec-4' },
];
const activeStep = ref(1);

const MODELS = [
  '/static/studio/model-1.jpg',
  '/static/studio/model-2.jpg',
  '/static/studio/model-3.jpg',
  '/static/studio/model-4.jpg',
];
const SCENES = [
  { key: 'stSceneStudio', img: '/static/studio/scene-studio.jpg' },
  { key: 'stSceneStreet', img: '/static/studio/scene-street.jpg' },
  { key: 'stSceneCafe', img: '/static/studio/scene-cafe.jpg' },
  { key: 'stSceneGarden', img: '/static/studio/scene-garden.jpg' },
];
const RESULTS = [
  '/static/landing/model-vest.jpg',
  '/static/onboarding/result.jpg',
  '/static/landing/lifestyle.jpg',
  '/static/landing/model-check.jpg',
  '/static/landing/detail.jpg',
  '/static/styles/style-editorial.jpg',
];

const DEFAULT_BEFORE = '/static/landing/garment-vest.png';
const beforeImg = ref(DEFAULT_BEFORE);
const uploaded = ref(false);
const dragOver = ref(false);
const fileInput = ref(null);
let objectUrl = '';

const activeModel = ref(0);
const activeScene = ref(0);
const activeResult = ref(0);
const afterImg = computed(() => RESULTS[activeResult.value]);
const generating = ref(false);

const modelRow = ref(null);
const sceneRow = ref(null);
const resultRow = ref(null);
function scrollRow(el) {
  el?.scrollBy({ left: 150, behavior: 'smooth' });
}

/* ---------- 上传 ---------- */
function acceptFile(file) {
  if (!file) return;
  const typeOk = /image\/(jpeg|png)/.test(file.type) || /\.(jpe?g|png)$/i.test(file.name || '');
  if (!typeOk || file.size > 10 * 1024 * 1024) {
    toast(t('stToastBadFile'), 'error');
    return;
  }
  if (objectUrl) URL.revokeObjectURL(objectUrl);
  objectUrl = URL.createObjectURL(file);
  beforeImg.value = objectUrl;
  uploaded.value = true;
  activeStep.value = 1;
  toast(t('stToastUploaded'), 'success');
}
function onFileChange(e) {
  acceptFile(e.target.files?.[0]);
  e.target.value = '';
}
function onDrop(e) {
  dragOver.value = false;
  acceptFile(e.dataTransfer?.files?.[0]);
}
onBeforeUnmount(() => {
  if (objectUrl) URL.revokeObjectURL(objectUrl);
});

/* ---------- Before / After 拖拽对比 ---------- */
const pos = ref(50);
const compareRoot = ref(null);
let dragging = false;
function setFromEvent(e) {
  const el = compareRoot.value;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const x = (e.touches?.[0]?.clientX ?? e.clientX) - rect.left;
  pos.value = Math.max(0, Math.min(100, (x / rect.width) * 100));
}
function onPointerDown(e) {
  dragging = true;
  setFromEvent(e);
  window.addEventListener('pointermove', onPointerMove);
  window.addEventListener('pointerup', onPointerUp, { once: true });
}
function onPointerMove(e) {
  if (dragging) setFromEvent(e);
}
function onPointerUp() {
  dragging = false;
  window.removeEventListener('pointermove', onPointerMove);
}
function toggleCompare() {
  pos.value = pos.value === 50 ? 0 : pos.value === 0 ? 100 : 50;
}
onBeforeUnmount(() => window.removeEventListener('pointermove', onPointerMove));

/* ---------- 动作 ---------- */
function goStep(step) {
  activeStep.value = step.n;
  document.getElementById(step.target)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
function pickModel(i) {
  activeModel.value = i;
  activeResult.value = Math.min(i, RESULTS.length - 1);
  activeStep.value = 2;
}
function pickScene(i) {
  activeScene.value = i;
  activeStep.value = 3;
}
function pickResult(i) {
  activeResult.value = i;
}
function generate() {
  if (generating.value) return;
  generating.value = true;
  activeStep.value = 4;
  setTimeout(() => {
    generating.value = false;
    activeResult.value = 0;
    toast(t('stToastGenerated', { n: RESULTS.length }), 'success');
  }, 1300);
}
function downloadAll() {
  toast(t('stToastDownload'));
}
function addToProject() {
  if (!userState.loggedIn) {
    toast(t('stToastLogin'));
    return;
  }
  toast(t('stToastProject'), 'success');
}
function regenerate() {
  toast(t('stToastRegenerate'), 'success');
}
</script>

<template>
  <div class="cp">
    <!-- 英雄区 -->
    <section class="cp-hero">
      <h1 class="cp-hero__title">
        <span>{{ t('stHeroLine1') }}</span>
        <span>{{ t('stHeroLine2') }} <em class="cp-hero__accent">{{ t('stHeroAccent') }}</em></span>
      </h1>
      <p class="cp-hero__lead">{{ t('stHeroLead') }}</p>
      <img class="cp-hero__photo" src="/static/landing/lifestyle.jpg" alt="" />
    </section>

    <div class="cp-card">
      <!-- 步骤条 -->
      <div class="cp-steps">
        <button
          v-for="s in STEPS"
          :key="s.n"
          type="button"
          class="cp-step"
          :class="{ 'cp-step--active': activeStep === s.n }"
          @click="goStep(s)"
        >
          <span class="cp-step__dot">{{ s.n }}</span>
          <span class="cp-step__label">{{ t(s.key) }}</span>
        </button>
      </div>

      <div class="cp-grid">
        <!-- 左：配置 -->
        <div class="cp-config">
          <!-- 1 上传 -->
          <section id="cp-sec-1">
            <div class="cp-section__head">
              <span class="cp-num">1</span>
              <h2 class="cp-section__title">{{ t('stUploadTitle') }}</h2>
              <StudioIcon name="info" :size="13" class="cp-info" />
            </div>
            <button
              type="button"
              class="cp-upload"
              :class="{ 'cp-upload--over': dragOver }"
              @click="fileInput?.click()"
              @dragover.prevent="dragOver = true"
              @dragleave.prevent="dragOver = false"
              @drop.prevent="onDrop"
            >
              <span class="cp-upload__thumb"><img :src="beforeImg" alt="" /></span>
              <span class="cp-upload__body">
                <StudioIcon name="cloud" :size="19" class="cp-upload__cloud" />
                <strong class="cp-upload__main">{{ uploaded ? t('stUploadPreview') : t('stUploadClick') }}</strong>
                <span class="cp-upload__sub">{{ t('stUploadOr') }}</span>
                <span class="cp-upload__hint">{{ t('stUploadHint') }}</span>
              </span>
            </button>
            <input
              ref="fileInput"
              type="file"
              accept="image/jpeg,image/png"
              hidden
              @change="onFileChange"
            />
          </section>

          <!-- 2 选模特 -->
          <section id="cp-sec-2">
            <div class="cp-section__head">
              <span class="cp-num">2</span>
              <h2 class="cp-section__title">{{ t('stChooseModel') }}</h2>
            </div>
            <div class="cp-picks">
              <div ref="modelRow" class="cp-pick-row">
                <button
                  v-for="(m, i) in MODELS"
                  :key="m"
                  type="button"
                  class="cp-pick"
                  :class="{ 'cp-pick--active': activeModel === i }"
                  @click="pickModel(i)"
                >
                  <img :src="m" alt="" />
                </button>
              </div>
              <button type="button" class="cp-picks__nav" :aria-label="t('stViewAll')" @click="scrollRow(modelRow)">
                <StudioIcon name="chevronRight" :size="13" />
              </button>
            </div>
          </section>

          <!-- 3 选场景 -->
          <section id="cp-sec-3">
            <div class="cp-section__head">
              <span class="cp-num">3</span>
              <h2 class="cp-section__title">{{ t('stSelectScene') }}</h2>
            </div>
            <div class="cp-picks">
              <div ref="sceneRow" class="cp-pick-row">
                <button
                  v-for="(s, i) in SCENES"
                  :key="s.key"
                  type="button"
                  class="cp-pick cp-pick--scene"
                  :class="{ 'cp-pick--active': activeScene === i }"
                  @click="pickScene(i)"
                >
                  <img :src="s.img" alt="" />
                  <span class="cp-pick__label">{{ t(s.key) }}</span>
                </button>
              </div>
              <button type="button" class="cp-picks__nav" aria-label="next" @click="scrollRow(sceneRow)">
                <StudioIcon name="chevronRight" :size="13" />
              </button>
            </div>
          </section>

          <!-- 4 生成 -->
          <section id="cp-sec-4">
            <button type="button" class="cp-generate" :disabled="generating" @click="generate">
              <StudioIcon v-if="generating" name="loader" :size="15" class="cp-spin" />
              <StudioIcon v-else name="sparkles" :size="15" />
              {{ generating ? t('stGenerating') : t('stGenerate') }}
            </button>
            <p class="cp-cost">{{ t('stCostHint') }}</p>
          </section>
        </div>

        <!-- 右：结果 -->
        <div class="cp-preview">
          <div
            ref="compareRoot"
            class="cp-compare cp-compare--hint"
            @pointerdown.prevent="onPointerDown"
          >
            <div class="cp-compare__after" :style="{ left: pos + '%' }">
              <img :src="afterImg" alt="" draggable="false" />
            </div>
            <div class="cp-compare__before" :style="{ width: pos + '%' }">
              <img :src="beforeImg" alt="" draggable="false" />
            </div>
            <span class="cp-pill cp-pill--before">{{ t('stBefore') }}</span>
            <span class="cp-pill cp-pill--after">{{ t('stAfter') }}</span>
            <div class="cp-compare__line" :style="{ left: pos + '%' }"></div>
            <button
              type="button"
              class="cp-compare__switch"
              :aria-label="t('stAfter')"
              @pointerdown.stop
              @click.stop="toggleCompare"
            >
              <StudioIcon name="chevronRight" :size="16" />
            </button>
          </div>

          <div class="cp-results">
            <div ref="resultRow" class="cp-results__row">
              <button
                v-for="(r, i) in RESULTS"
                :key="r"
                type="button"
                class="cp-result"
                :class="{ 'cp-result--active': activeResult === i }"
                @click="pickResult(i)"
              >
                <img :src="r" alt="" />
              </button>
            </div>
            <button type="button" class="cp-results__nav" aria-label="next" @click="scrollRow(resultRow)">
              <StudioIcon name="chevronRight" :size="13" />
            </button>
          </div>

          <div class="cp-actions">
            <button type="button" class="cp-action" @click="downloadAll">
              <StudioIcon name="download" :size="15" />{{ t('stDownloadAll') }}
            </button>
            <button type="button" class="cp-action" @click="addToProject">
              <StudioIcon name="folderPlus" :size="15" />{{ t('stAddProject') }}
            </button>
            <button type="button" class="cp-action" @click="regenerate">
              <StudioIcon name="refresh" :size="15" />{{ t('stRegenerate') }}
            </button>
          </div>
        </div>
      </div>

      <p class="cp-demo">{{ t('stDemoHint') }}</p>
    </div>
  </div>
</template>
