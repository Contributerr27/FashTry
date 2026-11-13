<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { i18n } from '../lib/i18n.js';

const open = ref(false);
const root = ref(null);
const current = ref(i18n.getLocale());

const triggerLabel = computed(() =>
  current.value === 'en' ? 'Language: English' : '语言：中文'
);

function syncCurrent() {
  current.value = i18n.getLocale();
}

function toggle(e) {
  e.stopPropagation();
  open.value = !open.value;
}

function close() {
  open.value = false;
}

function pick(lang, e) {
  e.stopPropagation();
  if (lang === 'zh' || lang === 'en') {
    if (i18n.getLocale() !== lang) i18n.setLocale(lang);
  }
  close();
}

function onDocClick(e) {
  if (!root.value?.contains(e.target)) close();
}

onMounted(() => {
  document.addEventListener('click', onDocClick);
  window.addEventListener('localechange', syncCurrent);
});
onUnmounted(() => {
  document.removeEventListener('click', onDocClick);
  window.removeEventListener('localechange', syncCurrent);
});
</script>

<template>
  <div ref="root" class="lang-dropdown">
    <button
      type="button"
      class="lang-dropdown-trigger"
      :aria-label="triggerLabel"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="toggle"
    >
      <svg class="lang-icon-globe" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
        <circle cx="12" cy="12" r="10" stroke-width="1.75" />
        <path stroke-width="1.75" stroke-linecap="round" d="M2 12h20M12 2c3 3.5 3 16.5 0 20M12 2c-3 3.5-3 16.5 0 20" />
      </svg>
      <svg
        class="lang-icon-chevron"
        :class="{ 'lang-icon-chevron-open': open }"
        width="10"
        height="6"
        viewBox="0 0 10 6"
        fill="none"
        aria-hidden="true"
      >
        <path d="M1 1l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
      </svg>
    </button>
    <div
      v-show="open"
      class="lang-dropdown-panel"
      role="listbox"
      :aria-hidden="!open"
    >
      <button
        type="button"
        class="lang-dropdown-option"
        data-lang="zh"
        role="option"
        :aria-selected="current === 'zh'"
        @click="pick('zh', $event)"
      >
        <span class="lang-flag lang-flag-cn" aria-hidden="true">
          <svg viewBox="0 0 24 16" xmlns="http://www.w3.org/2000/svg">
            <rect width="24" height="16" fill="#de2910" />
            <polygon fill="#ffde00" points="5,3 5.9,5.5 8.5,5.5 6.3,7 7.2,9.5 5,8 2.8,9.5 3.7,7 1.5,5.5 4.1,5.5" />
          </svg>
        </span>
        <span>中文</span>
      </button>
      <button
        type="button"
        class="lang-dropdown-option"
        data-lang="en"
        role="option"
        :aria-selected="current === 'en'"
        @click="pick('en', $event)"
      >
        <span class="lang-flag lang-flag-us" aria-hidden="true">
          <svg viewBox="0 0 24 16" xmlns="http://www.w3.org/2000/svg">
            <rect width="24" height="16" fill="#b22234" />
            <path
              fill="#fff"
              d="M0 2.3h24v1.7H0V2.3zm0 3.4h24V8H0V5.7zm0 3.4h24v1.7H0V9.1zm0 3.4h24V14H0v-1.7z"
            />
            <rect width="9.6" height="8.2" y="0" fill="#3c3b6e" />
          </svg>
        </span>
        <span>English</span>
      </button>
    </div>
  </div>
</template>
