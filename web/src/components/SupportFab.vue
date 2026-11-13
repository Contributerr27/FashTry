<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { RouterLink } from 'vue-router';
import { i18n } from '../lib/i18n.js';
import { openKertenSupportWidget } from '../lib/kertenSupport.js';

const open = ref(false);
const rootRef = ref(null);
const aiLoading = ref(false);

function closeMenu() {
  open.value = false;
}

function toggleMenu() {
  open.value = !open.value;
}

async function onAiSupport() {
  closeMenu();
  aiLoading.value = true;
  try {
    await openKertenSupportWidget();
  } catch {
    /* 静默失败 */
  } finally {
    aiLoading.value = false;
  }
}

function onDocClick(e) {
  if (!rootRef.value?.contains(e.target)) closeMenu();
}

function onKeydown(e) {
  if (e.key === 'Escape') closeMenu();
}

onMounted(() => {
  document.addEventListener('click', onDocClick);
  document.addEventListener('keydown', onKeydown);
});

onUnmounted(() => {
  document.removeEventListener('click', onDocClick);
  document.removeEventListener('keydown', onKeydown);
});
</script>

<template>
  <div ref="rootRef" class="support-fab" :class="{ 'support-fab--open': open }">
    <div v-show="open" class="support-fab__menu" role="menu" :aria-label="i18n.t('supportFabMenu')">
      <RouterLink to="/feedback" class="support-fab__item" role="menuitem" @click="closeMenu">
        <span class="support-fab__item-ico" aria-hidden="true">💬</span>
        <span class="support-fab__item-text">{{ i18n.t('feedbackTitle') }}</span>
      </RouterLink>
      <button
        type="button"
        class="support-fab__item"
        role="menuitem"
        :disabled="aiLoading"
        @click="onAiSupport"
      >
        <span class="support-fab__item-ico support-fab__item-ico--ai" aria-hidden="true">AI</span>
        <span class="support-fab__item-text">{{ i18n.t('supportFabAiChat') }}</span>
      </button>
    </div>

    <button
      type="button"
      class="support-fab__main"
      :aria-expanded="open"
      :aria-label="i18n.t('supportFabTitle')"
      @click.stop="toggleMenu"
    >
      <span class="support-fab__main-ico" aria-hidden="true">{{ open ? '×' : '?' }}</span>
    </button>
  </div>
</template>

<style scoped>
.support-fab {
  position: fixed;
  right: 1rem;
  bottom: 1.25rem;
  z-index: 300;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.65rem;
}

:global(body.app-has-mobile-tab) .support-fab {
  bottom: calc(52px + 1rem);
}

.support-fab__menu {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 148px;
  padding: 0.4rem;
  border-radius: 14px;
  background: rgba(12, 16, 24, 0.96);
  border: 1px solid var(--border-subtle, rgba(148, 163, 184, 0.22));
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(12px);
}

.support-fab__item {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  padding: 0.55rem 0.65rem;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text, #f0f9ff);
  font-size: 0.84rem;
  font-family: inherit;
  text-decoration: none;
  text-align: left;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;
}

.support-fab__item:hover:not(:disabled) {
  background: rgba(108, 76, 246, 0.12);
  color: var(--accent-secondary, #8b73ff);
}

.support-fab__item:disabled {
  opacity: 0.6;
  cursor: wait;
}

.support-fab__item-ico {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(148, 163, 184, 0.12);
  font-size: 0.95rem;
  line-height: 1;
}

.support-fab__item-ico--ai {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: #8b73ff;
  background: rgba(15, 118, 110, 0.22);
}

.support-fab__item-text {
  flex: 1;
  line-height: 1.3;
  white-space: nowrap;
}

.support-fab__main {
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid var(--border, rgba(148, 163, 184, 0.28));
  border-radius: 50%;
  background: var(--bg-card-solid, rgba(15, 23, 42, 0.95));
  color: var(--text, #f0f9ff);
  box-shadow: var(--shadow-glow, 0 4px 24px rgba(108, 76, 246, 0.15));
  cursor: pointer;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.support-fab__main:hover {
  border-color: rgba(108, 76, 246, 0.45);
  background: rgba(108, 76, 246, 0.08);
}

.support-fab--open .support-fab__main {
  border-color: var(--accent, #6c4cf6);
  background: rgba(108, 76, 246, 0.14);
}

.support-fab__main-ico {
  font-size: 1.25rem;
  font-weight: 700;
  line-height: 1;
  color: var(--accent-secondary, #8b73ff);
}
</style>
