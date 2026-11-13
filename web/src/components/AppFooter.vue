<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { RouterLink } from 'vue-router';
import { i18n } from '../lib/i18n.js';

const footerKey = ref(0);
function onLocale() {
  footerKey.value++;
}
onMounted(() => window.addEventListener('localechange', onLocale));
onUnmounted(() => window.removeEventListener('localechange', onLocale));
</script>

<template>
  <footer class="site-footer" :key="footerKey">
    <span>© 2026 可腾网络科技工作室</span>
    <RouterLink to="/privacy">{{ i18n.t('navPrivacy') }}</RouterLink>
    <RouterLink to="/terms">{{ i18n.t('navTerms') }}</RouterLink>
    <RouterLink to="/commercial-license">{{ i18n.t('navCommercialLicense') }}</RouterLink>
    <RouterLink to="/contact">{{ i18n.t('navContact') }}</RouterLink>
    <RouterLink to="/brand-join">{{ i18n.t('navBrandJoin') }}</RouterLink>
    <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">粤ICP备2022144912号-5</a>
  </footer>
</template>