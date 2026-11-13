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
    <h1>{{ i18n.t('legalTermsHeading') }}</h1>
    <p class="legal-updated">{{ i18n.t('legalUpdatedLabel') }}：{{ i18n.t('legalUpdatedDate') }}</p>
    <article class="legal-prose">
      <h2>{{ i18n.t('legalTermsS1Title') }}</h2>
      <p>{{ i18n.t('legalTermsS1Body') }}</p>
      <h2>{{ i18n.t('legalTermsS2Title') }}</h2>
      <p>{{ i18n.t('legalTermsS2Body') }}</p>
      <h2>{{ i18n.t('legalTermsS3Title') }}</h2>
      <p>{{ i18n.t('legalTermsS3Body') }}</p>
      <h2>{{ i18n.t('legalTermsS4Title') }}</h2>
      <p>{{ i18n.t('legalTermsS4Body') }}</p>
      <h2>{{ i18n.t('legalTermsS5Title') }}</h2>
      <p>{{ i18n.t('legalTermsS5Body') }}</p>
    </article>
  </main>
</template>
