<script setup>
import { onMounted, onUnmounted, watch } from 'vue';
import { i18n } from '../lib/i18n.js';
import { applySeo, descriptionKeyFromTitleKey } from '../lib/seo.js';

const props = defineProps({
  titleKey: { type: String, required: true },
});

function syncSeo() {
  applySeo({
    titleKey: props.titleKey,
    descriptionKey: descriptionKeyFromTitleKey(props.titleKey),
  });
}

onMounted(() => {
  syncSeo();
  window.addEventListener('localechange', syncSeo);
});

onUnmounted(() => {
  window.removeEventListener('localechange', syncSeo);
});

watch(
  () => props.titleKey,
  () => syncSeo()
);
</script>

<template>
  <main class="stub-page">
    <h1>{{ i18n.t(titleKey) }}</h1>
    <p>{{ i18n.t('stubPageHint') }}</p>
  </main>
</template>

<style scoped>
.stub-page {
  padding: 2.5rem 1.25rem;
  text-align: center;
  color: var(--text-3);
}

.stub-page h1 {
  color: var(--text);
  font-size: 1.25rem;
  margin-bottom: 0.75rem;
}
</style>
