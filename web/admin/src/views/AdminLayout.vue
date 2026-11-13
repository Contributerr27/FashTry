<script setup>
import { computed, ref, onMounted } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { adminLogout, adminMe } from '../lib/api.js';

const SIDEBAR_COLLAPSED_KEY = 'admin-sidebar-collapsed';

const route = useRoute();
const router = useRouter();
const profile = ref(null);
const sideCollapsed = ref(typeof localStorage !== 'undefined' && localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1');

const groups = [
  { label: '概览', items: [{ path: '/dashboard', label: '数据概览' }] },
  {
    label: '用户',
    items: [
      { path: '/users', label: '用户列表' },
      { path: '/user-uploads', label: '用户上传' },
      { path: '/feedback', label: '问题反馈' },
    ],
  },
  { label: '任务', items: [{ path: '/tasks', label: '任务列表' }] },
  {
    label: '交易',
    items: [
      { path: '/orders', label: '充值订单' },
      { path: '/credit-records', label: '点数记录' },
      { path: '/packages', label: '充值套餐' },
    ],
  },
  {
    label: '内容',
    items: [
      { path: '/public-models', label: '公共模特库' },
      { path: '/public-garments', label: '公共服装库' },
      { path: '/brands', label: '品牌方' },
    ],
  },
  {
    label: '系统',
    items: [
      { path: '/gemini-keys', label: 'Gemini Key' },
      { path: '/register-gift', label: '注册赠送' },
      { path: '/register-abuse', label: '注册风控' },
      { path: '/password', label: '修改密码' },
    ],
  },
];

const activePath = computed(() => route.path);

function navActive(itemPath) {
  if (itemPath === '/brands') {
    return activePath.value === '/brands' || activePath.value.startsWith('/brands/');
  }
  return activePath.value === itemPath;
}

onMounted(async () => {
  try {
    profile.value = await adminMe();
  } catch {
    profile.value = null;
  }
});

async function logout() {
  await adminLogout();
  await router.replace('/login');
}

function toggleSidebar() {
  sideCollapsed.value = !sideCollapsed.value;
  try {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, sideCollapsed.value ? '1' : '0');
  } catch {
    /* ignore */
  }
}
</script>

<template>
  <div class="admin-shell" :class="{ 'admin-shell--side-collapsed': sideCollapsed }">
    <aside class="admin-side" aria-label="主导航">
      <div class="admin-side-brand-row">
        <div class="admin-side-brand">FashTry <span class="admin-pill admin-pill--ok">后台</span></div>
        <button
          type="button"
          class="admin-side-hide-btn"
          title="收起侧边栏"
          aria-label="收起侧边栏"
          @click="toggleSidebar"
        >
          <span class="admin-side-hide-icon" aria-hidden="true">⟨⟨</span>
        </button>
      </div>
      <nav class="admin-side-nav">
        <template v-for="group in groups" :key="group.label">
          <div class="admin-side-group">{{ group.label }}</div>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="admin-side-link"
            :class="{ active: navActive(item.path) }"
          >
            {{ item.label }}
          </RouterLink>
        </template>
      </nav>
      <div class="admin-side-foot">
        <span v-if="profile" class="admin-side-user">{{ profile.username }}</span>
        <button type="button" class="admin-side-logout" @click="logout">退出</button>
      </div>
    </aside>
    <div class="admin-main-wrap">
      <button
        v-if="sideCollapsed"
        type="button"
        class="admin-side-toggle-fab"
        title="展开侧边栏"
        aria-label="展开侧边栏"
        aria-expanded="false"
        @click="toggleSidebar"
      >
        <svg class="admin-side-toggle-svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
      <RouterView />
    </div>
  </div>
</template>
