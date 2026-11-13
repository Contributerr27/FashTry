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
    <h1>{{ i18n.t('commercialNoticeTitle') }}</h1>
    <p class="legal-updated">
      {{ i18n.t('legalUpdatedLabel') }}：{{ i18n.t('legalCommercialUpdatedDate') }}
    </p>
    <article class="legal-prose">
      <p>{{ i18n.t('commercialNoticeThanks') }}</p>
      <p>{{ i18n.t('commercialNoticeIntro') }}</p>
      <h2>{{ i18n.t('commercialSection1Title') }}</h2>
      <p>{{ i18n.t('commercialSection1Lead') }}</p>
      <ul>
        <li>{{ i18n.t('commercialSection1Item1') }}</li>
        <li>{{ i18n.t('commercialSection1Item2') }}</li>
        <li>{{ i18n.t('commercialSection1Item3') }}</li>
        <li>{{ i18n.t('commercialSection1Item4') }}</li>
      </ul>
      <h2>{{ i18n.t('commercialSection2Title') }}</h2>
      <p>{{ i18n.t('commercialSection2Lead') }}</p>
      <ul>
        <li>{{ i18n.t('commercialSection2Item1') }}</li>
        <li>{{ i18n.t('commercialSection2Item2') }}</li>
        <li>{{ i18n.t('commercialSection2Item3') }}</li>
      </ul>
      <h2>{{ i18n.t('commercialSection3Title') }}</h2>
      <p>{{ i18n.t('commercialSection3Body') }}</p>
      <h2>{{ i18n.t('commercialSection4Title') }}</h2>
      <p>{{ i18n.t('commercialSection4Body') }}</p>
      <h2>{{ i18n.t('commercialSection5Title') }}</h2>
      <p>{{ i18n.t('commercialSection5Body') }}</p>
    </article>
  </main>
</template>
