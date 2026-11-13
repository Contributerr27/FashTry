<script setup>
import { computed } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import Icon from './ui/Icon.vue';
import { useT } from '../lib/useT.js';
import { mobileTabKeyForPath } from '../lib/mobileTab.js';

const { t } = useT();
const route = useRoute();
const active = computed(() => mobileTabKeyForPath(route.path));

const tabs = [
  { key: 'home', to: '/', labelKey: 'uiNavHome', icon: 'home' },
  { key: 'create', to: '/create', labelKey: 'uiNavCreate', icon: 'sparkles' },
  { key: 'works', to: '/works', labelKey: 'uiNavWorks', icon: 'images' },
  { key: 'me', to: '/profile', labelKey: 'uiNavMe', icon: 'user' },
];
</script>

<template>
  <nav class="mtb" aria-label="Mobile navigation">
    <RouterLink
      v-for="tab in tabs"
      :key="tab.key"
      :to="tab.to"
      class="mtb__item"
      :class="{ 'mtb__item--active': active === tab.key }"
    >
      <Icon :name="tab.icon" :size="21" :stroke-width="active === tab.key ? 2 : 1.75" />
      <span>{{ t(tab.labelKey) }}</span>
    </RouterLink>
  </nav>
</template>

<style scoped>
.mtb {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 350;
  display: flex;
  height: calc(var(--tabbar-h) + env(safe-area-inset-bottom, 0px));
  padding-bottom: env(safe-area-inset-bottom, 0px);
  background: rgba(16, 19, 23, 0.94);
  border-top: 1px solid var(--line);
  backdrop-filter: saturate(140%) blur(14px);
}
.mtb__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  font-size: 0.68rem;
  color: var(--text-3);
  text-decoration: none;
  transition: color var(--transition);
}
.mtb__item--active {
  color: var(--brand);
  font-weight: 600;
}
</style>
