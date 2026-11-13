<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import '../assets/css/legal.css';
import { i18n } from '../lib/i18n.js';

const key = ref(0);

function sync() {
  key.value++;
}

onMounted(() => {
  sync();
  window.addEventListener('localechange', sync);
});
onUnmounted(() => {
  window.removeEventListener('localechange', sync);
});
</script>

<template>
  <main :key="key" class="legal-main">
    <h1>{{ i18n.t('legalPrivacyHeading') }}</h1>
    <p class="legal-updated">{{ i18n.t('legalUpdatedLabel') }}：{{ i18n.t('legalUpdatedDate') }}</p>
    <article class="legal-prose">
      <h2>{{ i18n.t('legalPrivacyS1Title') }}</h2>
      <p>{{ i18n.t('legalPrivacyS1Body') }}</p>
      <h2>{{ i18n.t('legalPrivacyS2Title') }}</h2>
      <p>{{ i18n.t('legalPrivacyS2Body') }}</p>
      <h2>{{ i18n.t('legalPrivacyS3Title') }}</h2>
      <p>{{ i18n.t('legalPrivacyS3Body') }}</p>
      <h2>{{ i18n.t('legalPrivacyS4Title') }}</h2>
      <p>{{ i18n.t('legalPrivacyS4Body') }}</p>
      <h2>{{ i18n.t('legalPrivacyS5Title') }}</h2>
      <p>{{ i18n.t('legalPrivacyS5Body') }}</p>
    </article>
  </main>
</template>
