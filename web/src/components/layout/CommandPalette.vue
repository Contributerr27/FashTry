<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '../ui/Icon.vue';
import { useT } from '../../lib/useT.js';
import { searchableEntries } from '../../lib/navItems.js';

const props = defineProps({ modelValue: { type: Boolean, default: false } });
const emit = defineEmits(['update:modelValue']);

const { t } = useT();
const router = useRouter();

const RECENT_KEY = 'fashtry_search_recent';
const q = ref('');
const active = ref(0);
const inputEl = ref(null);
const listEl = ref(null);
const recentTick = ref(0);

const entries = searchableEntries();

function readRecent() {
  try {
    const arr = JSON.parse(localStorage.getItem(RECENT_KEY) || '[]');
    return Array.isArray(arr) ? arr.slice(0, 5) : [];
  } catch {
    return [];
  }
}
function pushRecent(path) {
  const arr = [path, ...readRecent().filter((p) => p !== path)].slice(0, 5);
  localStorage.setItem(RECENT_KEY, JSON.stringify(arr));
}

function norm(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/\s+/g, '');
}

function score(e, query) {
  const title = norm(t(e.titleKey));
  const desc = norm(e.descKey ? t(e.descKey) : '');
  const kw = norm(e.keywords);
  if (title.startsWith(query)) return 100;
  if (title.includes(query)) return 80;
  if (kw.includes(query)) return 60;
  if (desc.includes(query)) return 40;
  const parts = String(q.value).toLowerCase().split(/\s+/).filter(Boolean);
  if (parts.length > 1 && parts.every((p) => (title + kw + desc).includes(p))) return 30;
  return 0;
}

const sections = computed(() => {
  const query = norm(q.value);
  if (!query) {
    void recentTick.value;
    const recent = readRecent()
      .map((p) => entries.find((e) => e.path === p))
      .filter(Boolean);
    const out = [];
    if (recent.length) out.push({ key: 'recent', titleKey: 'uiSearchGroupRecent', items: recent });
    out.push({ key: 'tools', titleKey: 'uiSearchGroupTools', items: entries.filter((e) => e.group === 'tools') });
    out.push({ key: 'pages', titleKey: 'uiSearchGroupPages', items: entries.filter((e) => e.group === 'pages') });
    return out;
  }
  const ranked = entries
    .map((e) => ({ e, s: score(e, query) }))
    .filter((x) => x.s > 0)
    .sort((a, b) => b.s - a.s)
    .map((x) => x.e);
  const tools = ranked.filter((e) => e.group === 'tools');
  const pages = ranked.filter((e) => e.group === 'pages');
  const out = [];
  if (tools.length) out.push({ key: 'tools', titleKey: 'uiSearchGroupTools', items: tools });
  if (pages.length) out.push({ key: 'pages', titleKey: 'uiSearchGroupPages', items: pages });
  return out;
});

const flat = computed(() => sections.value.flatMap((s) => s.items));

function indexOf(item, sectionKey) {
  let i = 0;
  for (const s of sections.value) {
    for (const it of s.items) {
      if (s.key === sectionKey && it === item) return i;
      i++;
    }
  }
  return -1;
}

function close() {
  emit('update:modelValue', false);
}

function go(item) {
  if (!item) return;
  pushRecent(item.path);
  close();
  router.push(item.path);
}

function onKey(e) {
  const n = flat.value.length;
  if (e.key === 'ArrowDown') {
    e.preventDefault();
    if (n) active.value = (active.value + 1) % n;
    scrollActive();
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    if (n) active.value = (active.value - 1 + n) % n;
    scrollActive();
  } else if (e.key === 'Enter') {
    e.preventDefault();
    go(flat.value[active.value]);
  } else if (e.key === 'Escape') {
    e.preventDefault();
    close();
  }
}

function scrollActive() {
  nextTick(() => {
    listEl.value?.querySelector('.cp__item--active')?.scrollIntoView({ block: 'nearest' });
  });
}

watch(q, () => {
  active.value = 0;
});

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      q.value = '';
      active.value = 0;
      recentTick.value++;
      nextTick(() => inputEl.value?.focus());
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = '';
    }
  }
);
</script>

<template>
  <Teleport to="body">
    <Transition name="cp-fade">
      <div v-if="modelValue" class="cp" role="dialog" aria-modal="true" @mousedown.self="close">
        <div class="cp__panel" @keydown="onKey">
          <div class="cp__input-row">
            <Icon name="search" :size="18" />
            <input
              ref="inputEl"
              v-model="q"
              class="cp__input"
              type="text"
              :placeholder="t('uiSearchPh')"
              autocomplete="off"
              spellcheck="false"
            />
            <kbd class="cp__esc" @click="close">Esc</kbd>
          </div>
          <div ref="listEl" class="cp__list">
            <template v-if="flat.length">
              <div v-for="s in sections" :key="s.key" class="cp__section">
                <div class="cp__section-title">{{ t(s.titleKey) }}</div>
                <button
                  v-for="it in s.items"
                  :key="s.key + it.path"
                  type="button"
                  class="cp__item"
                  :class="{ 'cp__item--active': indexOf(it, s.key) === active }"
                  @mouseenter="active = indexOf(it, s.key)"
                  @click="go(it)"
                >
                  <span class="cp__item-icon"><Icon :name="it.icon" :size="17" /></span>
                  <span class="cp__item-body">
                    <span class="cp__item-title">{{ t(it.titleKey) }}</span>
                    <span v-if="it.descKey" class="cp__item-desc">{{ t(it.descKey) }}</span>
                  </span>
                  <span v-if="it.cost" class="cp__item-cost">{{ t('uiCostFrom', { n: it.cost }) }}</span>
                  <Icon class="cp__item-enter" name="arrowRight" :size="15" />
                </button>
              </div>
            </template>
            <p v-else class="cp__empty">{{ t('uiSearchEmpty', { q }) }}</p>
          </div>
          <div class="cp__foot">{{ t('uiSearchHint') }}</div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.cp {
  position: fixed;
  inset: 0;
  z-index: 2500;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 12vh 16px 16px;
  background: rgba(3, 5, 8, 0.6);
  backdrop-filter: blur(4px);
}
.cp__panel {
  width: min(640px, 100%);
  max-height: 72vh;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line-strong);
  box-shadow: var(--shadow-3);
  overflow: hidden;
}
.cp__input-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px 0 16px;
  height: 56px;
  border-bottom: 1px solid var(--line);
  color: var(--text-3);
}
.cp__input {
  flex: 1;
  height: 100%;
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  padding: 0 !important;
  font-size: 1rem;
  color: var(--text);
  outline: none;
}
.cp__esc {
  padding: 2px 7px;
  border-radius: 5px;
  border: 1px solid var(--line-strong);
  color: var(--text-3);
  font: 600 0.7rem/1.4 var(--font-sans);
  cursor: pointer;
}
.cp__list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 8px 10px;
}
.cp__section + .cp__section {
  margin-top: 6px;
}
.cp__section-title {
  padding: 10px 10px 6px;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-3);
}
.cp__item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.cp__item--active {
  background: var(--panel-3);
}
.cp__item-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--panel-2);
  border: 1px solid var(--line);
  color: var(--text-2);
  flex-shrink: 0;
}
.cp__item--active .cp__item-icon {
  background: var(--brand-soft);
  border-color: transparent;
  color: var(--brand);
}
.cp__item-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.cp__item-title {
  font-size: 0.9rem;
  font-weight: 500;
}
.cp__item-desc {
  font-size: 0.75rem;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cp__item-cost {
  font-size: 0.72rem;
  color: var(--text-3);
  white-space: nowrap;
}
.cp__item-enter {
  color: var(--text-3);
  opacity: 0;
}
.cp__item--active .cp__item-enter {
  opacity: 1;
}
.cp__empty {
  padding: 36px 0;
  text-align: center;
  color: var(--text-3);
  font-size: 0.875rem;
}
.cp__foot {
  padding: 10px 16px;
  border-top: 1px solid var(--line);
  font-size: 0.72rem;
  color: var(--text-3);
}
.cp-fade-enter-active,
.cp-fade-leave-active {
  transition: opacity 0.15s ease;
}
.cp-fade-enter-active .cp__panel,
.cp-fade-leave-active .cp__panel {
  transition: transform 0.15s ease;
}
.cp-fade-enter-from,
.cp-fade-leave-to {
  opacity: 0;
}
.cp-fade-enter-from .cp__panel {
  transform: translateY(-8px) scale(0.98);
}
@media (max-width: 768px) {
  .cp {
    padding-top: 8px;
  }
  .cp__panel {
    max-height: 86vh;
  }
  .cp__foot,
  .cp__item-cost {
    display: none;
  }
}
</style>
