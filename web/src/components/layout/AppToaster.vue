<script setup>
import { toastState } from '../../lib/toast.js';
import Icon from '../ui/Icon.vue';

const ICON = { success: 'checkCircle', error: 'alert', info: 'sparkles' };
</script>

<template>
  <div class="toaster" aria-live="polite">
    <TransitionGroup name="toaster">
      <div v-for="t in toastState.list" :key="t.id" class="toaster__item" :class="'toaster__item--' + t.type">
        <Icon :name="ICON[t.type] || 'sparkles'" :size="16" />
        <span>{{ t.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toaster {
  position: fixed;
  left: 50%;
  bottom: calc(28px + var(--app-tab-offset, 0px));
  transform: translateX(-50%);
  z-index: 3000;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}
.toaster__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: min(92vw, 480px);
  padding: 10px 16px;
  border-radius: 999px;
  background: var(--panel-3);
  border: 1px solid var(--line-strong);
  color: var(--text);
  font-size: 0.875rem;
  box-shadow: var(--shadow-3);
}
.toaster__item--success {
  color: #15803d;
  border-color: rgba(22, 163, 74, 0.35);
}
.toaster__item--success span {
  color: var(--text);
}
.toaster__item--error {
  color: #be123c;
  border-color: rgba(225, 29, 72, 0.35);
}
.toaster__item--error span {
  color: var(--text);
}
.toaster__item--info {
  color: var(--brand);
}
.toaster__item--info span {
  color: var(--text);
}
.toaster-enter-active,
.toaster-leave-active {
  transition: all 0.22s ease;
}
.toaster-enter-from,
.toaster-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
