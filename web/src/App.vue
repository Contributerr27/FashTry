<script setup>
import { computed, watch, onUnmounted, ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import AppSidebar from './components/layout/AppSidebar.vue';
import AppTopbar from './components/layout/AppTopbar.vue';
import CommandPalette from './components/layout/CommandPalette.vue';
import AppToaster from './components/layout/AppToaster.vue';
import AppFooter from './components/AppFooter.vue';
import SupportFab from './components/SupportFab.vue';
import CommercialNoticeModal from './components/CommercialNoticeModal.vue';
import MobileTabBar from './components/MobileTabBar.vue';
import { mobileTabKeyForPath } from './lib/mobileTab.js';
import { useMobileLayout } from './composables/useMobileLayout.js';
import { loadKertenSupportWidget } from './lib/kertenSupport.js';
import { api } from './lib/api.js';
import { bindUserStore, refreshUser, userState } from './lib/userStore.js';

const SIDEBAR_PREF_KEY = 'fashtry_sidebar_collapsed';

const route = useRoute();
const { isMobile } = useMobileLayout();
const { isMobile: isNarrow } = useMobileLayout(900);
const isMarketingHome = computed(() => route.path === '/' && !userState.loggedIn);
const showNav = computed(() => route.meta.showNav !== false && !isMarketingHome.value);
// 新版 Studio 页面自带完整版式，不叠加旧 footer / 客服浮球
const STUDIO_PATHS = ['/create-photos'];
const isStudioPage = computed(() => STUDIO_PATHS.includes(route.path));
const showMobileTabBar = computed(
  () => isMobile.value && showNav.value && mobileTabKeyForPath(route.path) != null
);
const showFooter = computed(() => !showMobileTabBar.value && !isStudioPage.value);
const drawerOpen = ref(false);
const searchOpen = ref(false);

function readPref() {
  const v = localStorage.getItem(SIDEBAR_PREF_KEY);
  return v === '1' ? true : v === '0' ? false : null;
}
const collapsePref = ref(readPref());

const collapsed = computed(() => {
  if (isNarrow.value) return false;
  if (collapsePref.value != null) return collapsePref.value;
  // 默认展开带文字标签的侧栏（与设计稿一致），只有用户手动收起才收起
  return false;
});

function toggleSidebar() {
  collapsePref.value = !collapsed.value;
  localStorage.setItem(SIDEBAR_PREF_KEY, collapsePref.value ? '1' : '0');
}

function onGlobalKey(e) {
  if (isMarketingHome.value) return;
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === 'k') {
    e.preventDefault();
    searchOpen.value = !searchOpen.value;
  }
}
onMounted(() => {
  loadKertenSupportWidget().catch(() => {});
  api.preload().catch(() => {});
  bindUserStore();
  refreshUser();
  window.addEventListener('keydown', onGlobalKey);
});

watch(
  () => route.fullPath,
  () => {
    drawerOpen.value = false;
    refreshUser();
  }
);

watch(drawerOpen, (open) => {
  document.body.classList.toggle('nav-menu-open', open);
  document.documentElement.classList.toggle('nav-menu-open', open);
});

watch(
  showMobileTabBar,
  (v) => {
    if (typeof document === 'undefined') return;
    document.body.classList.toggle('app-has-mobile-tab', v);
  },
  { immediate: true }
);
watch(
  isMarketingHome,
  (v) => {
    if (typeof document === 'undefined') return;
    document.documentElement.classList.toggle('landing-mode', v);
    document.body.classList.toggle('landing-mode', v);
  },
  { immediate: true }
);
onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKey);
  if (typeof document !== 'undefined') {
    document.body.classList.remove('app-has-mobile-tab', 'landing-mode');
    document.documentElement.classList.remove('landing-mode');
  }
});
</script>

<template>
  <CommercialNoticeModal />
  <div
    v-if="showNav"
    class="app-layout"
    :class="{ 'app-layout--collapsed': collapsed, 'app-layout--tab': showMobileTabBar }"
  >
    <AppSidebar
      :collapsed="collapsed"
      :mobile-open="drawerOpen"
      @toggle="toggleSidebar"
      @close="drawerOpen = false"
    />
    <div class="app-main">
      <AppTopbar @open-menu="drawerOpen = true" @open-search="searchOpen = true" />
      <main class="app-content">
        <router-view />
      </main>
      <AppFooter v-if="showFooter" />
    </div>
  </div>
  <div v-else class="app-router-shell">
    <router-view />
  </div>
  <CommandPalette v-model="searchOpen" />
  <AppToaster />
  <SupportFab v-if="showNav && !isStudioPage" />
  <MobileTabBar v-if="showMobileTabBar" />
</template>
