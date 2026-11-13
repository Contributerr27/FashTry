<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { i18n } from '../lib/i18n.js';
import {
  shouldShowCommercialNotice,
  acknowledgeCommercialNotice,
} from '../lib/commercialNoticeGate.js';

const router = useRouter();
const visible = ref(false);
const localeKey = ref(0);

function syncVisible() {
  visible.value = shouldShowCommercialNotice();
}

function onAck() {
  acknowledgeCommercialNotice();
  visible.value = false;
  if (router.currentRoute.value.path === '/login') {
    router.replace('/');
  }
}

function onLocaleChange() {
  localeKey.value++;
}

function onOnboardingDone() {
  syncVisible();
}

onMounted(() => {
  syncVisible();
  window.addEventListener('localechange', onLocaleChange);
  window.addEventListener('fashtry-onboarding-done', onOnboardingDone);
});

onUnmounted(() => {
  window.removeEventListener('localechange', onLocaleChange);
  window.removeEventListener('fashtry-onboarding-done', onOnboardingDone);
});

watch(
  () => router.currentRoute.value.fullPath,
  () => syncVisible()
);
</script>

<template>
  <div
    v-if="visible"
    class="commercial-notice-overlay"
    role="dialog"
    aria-modal="true"
    :aria-labelledby="'commercial-notice-title'"
  >
    <div class="commercial-notice-card">
      <h2 :id="'commercial-notice-title'" class="commercial-notice-title">
        {{ i18n.t('commercialNoticeTitle') }}
      </h2>
      <p class="commercial-notice-thanks">{{ i18n.t('commercialNoticeThanks') }}</p>
      <p class="commercial-notice-intro">{{ i18n.t('commercialNoticeIntro') }}</p>

      <section class="commercial-notice-section">
        <h3>{{ i18n.t('commercialSection1Title') }}</h3>
        <p>{{ i18n.t('commercialSection1Lead') }}</p>
        <ul>
          <li>{{ i18n.t('commercialSection1Item1') }}</li>
          <li>{{ i18n.t('commercialSection1Item2') }}</li>
          <li>{{ i18n.t('commercialSection1Item3') }}</li>
          <li>{{ i18n.t('commercialSection1Item4') }}</li>
        </ul>
      </section>

      <section class="commercial-notice-section">
        <h3>{{ i18n.t('commercialSection2Title') }}</h3>
        <p>{{ i18n.t('commercialSection2Lead') }}</p>
        <ul>
          <li>{{ i18n.t('commercialSection2Item1') }}</li>
          <li>{{ i18n.t('commercialSection2Item2') }}</li>
          <li>{{ i18n.t('commercialSection2Item3') }}</li>
        </ul>
      </section>

      <section class="commercial-notice-section">
        <h3>{{ i18n.t('commercialSection3Title') }}</h3>
        <p>{{ i18n.t('commercialSection3Body') }}</p>
      </section>

      <section class="commercial-notice-section">
        <h3>{{ i18n.t('commercialSection4Title') }}</h3>
        <p>{{ i18n.t('commercialSection4Body') }}</p>
      </section>

      <section class="commercial-notice-section">
        <h3>{{ i18n.t('commercialSection5Title') }}</h3>
        <p>{{ i18n.t('commercialSection5Body') }}</p>
      </section>

      <button type="button" class="btn btn-primary commercial-notice-btn" @click="onAck">
        {{ i18n.t('commercialNoticeBtn') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.commercial-notice-overlay {
  position: fixed;
  inset: 0;
  z-index: 1100;
  background: rgba(5, 5, 8, 0.82);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.commercial-notice-card {
  max-width: 560px;
  width: 100%;
  max-height: 90vh;
  overflow-y: auto;
  background: var(--bg-card-solid);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1.25rem 1.35rem;
  box-shadow: var(--shadow-glow);
}

.commercial-notice-title {
  font-size: 1.1rem;
  margin-bottom: 0.5rem;
}

.commercial-notice-thanks {
  color: var(--text-muted);
  font-size: 0.9rem;
  margin-bottom: 0.35rem;
}

.commercial-notice-intro {
  font-size: 0.88rem;
  line-height: 1.55;
  margin-bottom: 0.75rem;
}

.commercial-notice-section {
  margin-bottom: 0.75rem;
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--text-muted);
}

.commercial-notice-section h3 {
  color: var(--text);
  font-size: 0.9rem;
  margin-bottom: 0.35rem;
}

.commercial-notice-section ul {
  margin: 0.35rem 0 0;
  padding-left: 1.2rem;
}

.commercial-notice-btn {
  width: 100%;
  margin-top: 0.5rem;
}
</style>
