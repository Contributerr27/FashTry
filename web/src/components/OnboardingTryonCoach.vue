<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useT } from '../lib/useT.js';

const props = defineProps({
  /** model | garment | generate | result */
  step: { type: String, required: true },
});
const emit = defineEmits(['finish']);
const { t } = useT();

const hole = ref(null);
const fingerStyle = ref({});
const bubbleStyle = ref({});
const bubblePlace = ref('below');

const tip = computed(() => {
  if (props.step === 'model') return t('onboardingTryonStepModel');
  if (props.step === 'garment') return t('onboardingTryonStepGarment');
  if (props.step === 'generate') return t('onboardingTryonStepGenerate');
  return t('onboardingTryonStepResult');
});

const actionHint = computed(() => t('onboardingTryonActionClick'));

const stepIndex = computed(() => {
  const map = { model: 1, garment: 2, generate: 3, result: 4 };
  return map[props.step] || 1;
});

const panels = computed(() => {
  const h = hole.value;
  if (!h || props.step === 'result') return null;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const { top, left, width, height } = h;
  const bottom = top + height;
  const right = left + width;
  return [
    { key: 't', style: { top: '0px', left: '0px', width: '100%', height: `${top}px` } },
    { key: 'b', style: { top: `${bottom}px`, left: '0px', width: '100%', height: `${Math.max(0, vh - bottom)}px` } },
    { key: 'l', style: { top: `${top}px`, left: '0px', width: `${left}px`, height: `${height}px` } },
    { key: 'r', style: { top: `${top}px`, left: `${right}px`, width: `${Math.max(0, vw - right)}px`, height: `${height}px` } },
  ];
});

function layoutFromHole(h) {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const tipW = Math.min(360, vw - 24);
  const spaceBelow = vh - (h.top + h.height);
  const place = spaceBelow < 160 ? 'above' : 'below';
  let left = h.left + h.width / 2 - tipW / 2;
  left = Math.max(12, Math.min(left, vw - tipW - 12));
  const top = place === 'below' ? h.top + h.height + 18 : Math.max(12, h.top - 18 - 128);
  bubblePlace.value = place;
  bubbleStyle.value = { top: `${top}px`, left: `${left}px`, width: `${tipW}px` };
  fingerStyle.value = {
    top: `${h.top + h.height / 2}px`,
    left: `${Math.min(h.left + h.width - 8, vw - 140)}px`,
  };
}

function measure() {
  const el = document.querySelector('.ob-target--active');
  if (!el) {
    hole.value = null;
    return;
  }
  el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  const r = el.getBoundingClientRect();
  if (r.width < 8 || r.height < 8) {
    hole.value = null;
    return;
  }
  const pad = 10;
  const next = {
    top: Math.max(4, r.top - pad),
    left: Math.max(4, r.left - pad),
    width: Math.min(window.innerWidth - 8, r.width + pad * 2),
    height: Math.min(window.innerHeight - 8, r.height + pad * 2),
  };
  hole.value = next;
  layoutFromHole(next);
}

let raf = 0;
let retries = 0;
function scheduleMeasure() {
  cancelAnimationFrame(raf);
  raf = requestAnimationFrame(() => {
    measure();
    if (!hole.value && retries < 12 && props.step !== 'result') {
      retries += 1;
      window.setTimeout(scheduleMeasure, 80);
    } else {
      retries = 0;
    }
  });
}

watch(
  () => props.step,
  async () => {
    retries = 0;
    await nextTick();
    scheduleMeasure();
  }
);

onMounted(() => {
  scheduleMeasure();
  window.addEventListener('resize', scheduleMeasure, { passive: true });
  window.addEventListener('scroll', scheduleMeasure, { passive: true, capture: true });
});

onUnmounted(() => {
  cancelAnimationFrame(raf);
  window.removeEventListener('resize', scheduleMeasure);
  window.removeEventListener('scroll', scheduleMeasure, { capture: true });
});
</script>

<template>
  <Teleport to="body">
    <div class="ob-coach" aria-live="polite">
      <template v-if="panels">
        <div v-for="p in panels" :key="p.key" class="ob-coach__shade" :style="p.style" />
        <div
          class="ob-coach__ring"
          :style="{
            top: hole.top + 'px',
            left: hole.left + 'px',
            width: hole.width + 'px',
            height: hole.height + 'px',
          }"
        />
      </template>
      <div v-else-if="step === 'result'" class="ob-coach__shade ob-coach__shade--full" />

      <div v-if="hole && step !== 'result'" class="ob-coach__finger" :style="fingerStyle">
        <span class="ob-coach__finger-dot" />
        <span class="ob-coach__finger-label">{{ actionHint }}</span>
      </div>

      <!-- 量到目标时贴在旁边；量不到也固定显示底部卡片，保证文案一定可见 -->
      <div
        v-if="step !== 'result'"
        class="ob-coach__bubble"
        :class="[
          hole ? 'ob-coach__bubble--' + bubblePlace : 'ob-coach__bubble--fallback',
        ]"
        :style="hole ? bubbleStyle : undefined"
      >
        <div class="ob-coach__meta">
          <span class="ob-coach__badge">{{ t('onboardingTryonBadge') }}</span>
          <span class="ob-coach__step">{{ stepIndex }}/4</span>
        </div>
        <p class="ob-coach__tip">{{ tip }}</p>
        <p class="ob-coach__note">{{ t('onboardingTryonNote') }}</p>
      </div>

      <div v-if="step === 'result'" class="ob-coach__done">
        <div class="ob-coach__done-card">
          <div class="ob-coach__meta">
            <span class="ob-coach__badge">{{ t('onboardingTryonBadge') }}</span>
            <span class="ob-coach__step">4/4</span>
          </div>
          <p class="ob-coach__tip">{{ tip }}</p>
          <p class="ob-coach__note">{{ t('onboardingTryonNote') }}</p>
          <button type="button" class="btn btn-primary ob-coach__finish" @click="emit('finish')">
            {{ t('onboardingTryonFinish') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ob-coach {
  position: fixed;
  inset: 0;
  z-index: 1200;
  pointer-events: none;
}

.ob-coach__shade {
  position: fixed;
  z-index: 1200;
  background: rgba(5, 5, 8, 0.72);
  pointer-events: auto;
}
.ob-coach__shade--full {
  inset: 0;
}

.ob-coach__ring {
  position: fixed;
  z-index: 1201;
  border-radius: 16px;
  border: 2px solid var(--brand, #6c4cf6);
  box-shadow:
    0 0 0 4px rgba(108, 76, 246, 0.2),
    0 0 28px rgba(108, 76, 246, 0.35);
  pointer-events: none;
  animation: ob-glow 1.45s ease-in-out infinite;
}

.ob-coach__finger {
  position: fixed;
  z-index: 1203;
  display: flex;
  align-items: center;
  gap: 8px;
  transform: translateY(-50%);
  pointer-events: none;
  animation: ob-bounce 1.1s ease-in-out infinite;
}
.ob-coach__finger-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--brand, #6c4cf6);
  box-shadow:
    0 0 0 6px rgba(108, 76, 246, 0.28),
    0 0 24px rgba(108, 76, 246, 0.55);
}
.ob-coach__finger-label {
  padding: 7px 14px;
  border-radius: 999px;
  background: var(--brand, #6c4cf6);
  color: #042f2e;
  font-size: 0.88rem;
  font-weight: 800;
  letter-spacing: 0.02em;
  white-space: nowrap;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.ob-coach__bubble {
  position: fixed;
  z-index: 1202;
  pointer-events: none;
  padding: 1rem 1.15rem;
  border-radius: 14px;
  border: 1px solid rgba(108, 76, 246, 0.45);
  background: rgba(20, 23, 28, 0.98);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.5);
}
.ob-coach__bubble--fallback {
  left: 16px;
  right: auto;
  bottom: 24px;
  top: auto;
  width: min(360px, calc(100vw - 32px));
}
.ob-coach__bubble--below::before,
.ob-coach__bubble--above::before {
  content: '';
  position: absolute;
  left: 50%;
  width: 12px;
  height: 12px;
  background: rgba(20, 23, 28, 0.98);
  border-left: 1px solid rgba(108, 76, 246, 0.45);
  border-top: 1px solid rgba(108, 76, 246, 0.45);
  transform: translateX(-50%) rotate(45deg);
}
.ob-coach__bubble--below::before {
  top: -6px;
}
.ob-coach__bubble--above::before {
  bottom: -6px;
  transform: translateX(-50%) rotate(225deg);
}

.ob-coach__done {
  position: absolute;
  inset: 0;
  z-index: 1202;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  pointer-events: auto;
}
.ob-coach__done-card {
  width: 100%;
  max-width: 420px;
  padding: 1.35rem 1.4rem;
  border-radius: 16px;
  border: 1px solid rgba(108, 76, 246, 0.4);
  background: rgba(20, 23, 28, 0.98);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.55);
}

.ob-coach__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.45rem;
}
.ob-coach__badge {
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  color: var(--brand, #6c4cf6);
  font-weight: 700;
}
.ob-coach__step {
  font-size: 0.75rem;
  color: #9aa3af;
  font-weight: 600;
}
.ob-coach__tip {
  margin: 0;
  font-size: 1.12rem;
  font-weight: 800;
  line-height: 1.4;
  color: #f3f4f6;
}
.ob-coach__note {
  margin: 0.45rem 0 0;
  font-size: 0.82rem;
  color: #9aa3af;
}
.ob-coach__finish {
  width: 100%;
  margin-top: 1.05rem;
}

@keyframes ob-glow {
  0%,
  100% {
    box-shadow:
      0 0 0 4px rgba(108, 76, 246, 0.22),
      0 0 28px rgba(108, 76, 246, 0.4);
  }
  50% {
    box-shadow:
      0 0 0 8px rgba(108, 76, 246, 0.12),
      0 0 36px rgba(108, 76, 246, 0.55);
  }
}
@keyframes ob-bounce {
  0%,
  100% {
    transform: translateY(-50%) translateX(0);
  }
  50% {
    transform: translateY(-50%) translateX(10px);
  }
}
</style>
