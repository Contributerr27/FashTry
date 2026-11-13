<script setup>
import Icon from '../components/ui/Icon.vue';
import { TOOLS } from '../lib/navItems.js';
import { useT } from '../lib/useT.js';

const { t } = useT();
</script>

<template>
  <div class="page ch">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ t('mobileTabCreate') }}</h1>
        <p class="page-lead">{{ t('uiHomeHeroLead') }}</p>
      </div>
    </header>
    <nav class="ch__grid">
      <RouterLink v-for="tool in TOOLS" :key="tool.key" :to="tool.path" class="ch__card">
        <span class="ch__icon"><Icon :name="tool.icon" :size="22" /></span>
        <span class="ch__body">
          <strong>
            {{ t(tool.titleKey) }}
            <span v-if="tool.badge" class="tag tag--brand">{{ t(tool.badge === 'hot' ? 'uiBadgeHot' : 'uiBadgeNew') }}</span>
          </strong>
          <small>{{ t(tool.descKey) }}</small>
        </span>
        <span class="ch__cost">{{ t('uiCostFrom', { n: tool.cost }) }}</span>
      </RouterLink>
    </nav>
    <RouterLink to="/works" class="ch__works">
      <Icon name="images" :size="18" />
      <span>{{ t('uiNavWorks') }}</span>
      <Icon name="chevronRight" :size="16" />
    </RouterLink>
  </div>
</template>

<style scoped>
.ch {
  max-width: 760px;
}
.ch__grid {
  display: grid;
  gap: 10px;
}
.ch__card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--text);
  text-decoration: none;
  transition: border-color 0.15s;
}
.ch__card:hover,
.ch__card:active {
  border-color: var(--brand);
}
.ch__icon {
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 13px;
  background: var(--brand-soft);
  color: var(--brand);
}
.ch__body {
  flex: 1;
  min-width: 0;
}
.ch__body strong {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.95rem;
  font-weight: 600;
}
.ch__body small {
  display: block;
  margin-top: 3px;
  font-size: 0.78rem;
  line-height: 1.45;
  color: var(--text-3);
}
.ch__cost {
  flex-shrink: 0;
  font-size: 0.72rem;
  color: var(--text-3);
}
.ch__works {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 14px;
  border-radius: var(--radius-lg);
  border: 1px dashed var(--line-strong);
  color: var(--text-2);
  text-decoration: none;
  font-size: 0.9rem;
}
.ch__works span {
  flex: 1;
}
</style>
