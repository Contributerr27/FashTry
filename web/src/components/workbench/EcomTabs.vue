<script setup>
import { RouterLink, useRoute } from 'vue-router';
import Icon from '../ui/Icon.vue';
import { useT } from '../../lib/useT.js';

const { t } = useT();
const route = useRoute();

const TABS = [
  { path: '/model-shots', icon: 'model', key: 'navPackshot' },
  { path: '/clothing-suite', icon: 'grid', key: 'navEcomSuite' },
  { path: '/product-retouch', icon: 'retouch', key: 'navProductRetouch' },
];
</script>

<template>
  <nav class="ecom-tabs" aria-label="E-commerce tools">
    <RouterLink
      v-for="tab in TABS"
      :key="tab.path"
      :to="tab.path"
      class="ecom-tabs__item"
      :class="{ 'ecom-tabs__item--on': route.path === tab.path || (tab.path === '/model-shots' && route.path === '/clothing-packshot') }"
    >
      <Icon :name="tab.icon" :size="15" />{{ t(tab.key) }}
    </RouterLink>
  </nav>
</template>

<style scoped>
.ecom-tabs {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  border-radius: var(--radius-sm);
  background: var(--bg-elev);
  border: 1px solid var(--line);
}
.ecom-tabs__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border-radius: var(--radius-xs);
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--text-2);
  text-decoration: none;
  white-space: nowrap;
}
.ecom-tabs__item:hover {
  color: var(--text);
}
.ecom-tabs__item--on,
.ecom-tabs__item--on:hover {
  background: var(--panel-3);
  color: var(--text);
  box-shadow: var(--shadow-1);
}
.ecom-tabs__item--on :deep(.icon) {
  color: var(--brand);
}
@media (max-width: 768px) {
  .ecom-tabs {
    width: 100%;
    overflow-x: auto;
  }
  .ecom-tabs__item {
    flex: 1;
    justify-content: center;
  }
}
</style>
