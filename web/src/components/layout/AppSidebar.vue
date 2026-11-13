<script setup>
import { computed } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import StudioIcon from '../ui/StudioIcon.vue';
import Icon from '../ui/Icon.vue';
import { NAV_GROUPS } from '../../lib/navItems.js';
import { useT } from '../../lib/useT.js';
import { userState, displayName, DEFAULT_AVATAR } from '../../lib/userStore.js';

defineProps({
  collapsed: { type: Boolean, default: false },
  mobileOpen: { type: Boolean, default: false },
});
const emit = defineEmits(['toggle', 'close']);

const { t } = useT();
const route = useRoute();

/** 主导航：设计稿版式的两个核心页面 */
const MAIN = [
  { path: '/', icon: 'home', key: 'stNavDashboard', exact: true },
  { path: '/create-photos', icon: 'photo', key: 'stNavCreate' },
];

/**
 * 工具分组：沿用 navItems.js 单一数据源，
 * 把全部真实创作工具直接铺在侧栏明面上（不再收进「更多工具」下拉）。
 */
const TOOL_GROUPS = NAV_GROUPS.filter((g) => ['tryon', 'ecom', 'create'].includes(g.key));
/** 「我的」分组：作品 / 账户相关，登录后才可见的项由 auth 控制 */
const MINE = NAV_GROUPS.find((g) => g.key === 'mine')?.items || [];

const currentPath = computed(() => (route.path === '/clothing-packshot' ? '/model-shots' : route.path));
function isActive(item) {
  return item.exact ? currentPath.value === item.path : currentPath.value.startsWith(item.path);
}

const credits = computed(() => userState.info?.totalCredits ?? 420);
const userName = computed(() => displayName(userState.info) || 'John Doe');
const userMail = computed(() => userState.info?.email || 'john@brand.com');
const avatarUrl = computed(() => userState.info?.avatarUrl || DEFAULT_AVATAR);
const initials = computed(() => {
  const n = userName.value.trim();
  if (!n) return 'JD';
  const parts = n.split(/\s+/);
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : n.slice(0, 2)).toUpperCase();
});
</script>

<template>
  <div v-if="mobileOpen" class="sb2-overlay" aria-hidden="true" @click="emit('close')"></div>
  <aside class="sb2" :class="{ 'sb2--collapsed': collapsed, 'sb2--open': mobileOpen }">
    <div class="sb2__brand">
      <RouterLink to="/" class="sb2__brand-link" @click="emit('close')">
        <span class="brand-mark" aria-hidden="true">
          <img src="/static/logo-mark.png" alt="" />
        </span>
        <span class="sb2__brand-text">
          <span>{{ t('stBrand') }}</span>
          <span>{{ t('stBrandSub') }}</span>
        </span>
      </RouterLink>
      <button type="button" class="sb2__toggle" :title="t('uiCollapse')" @click="emit('toggle')">
        <StudioIcon name="chevronLeft" :size="15" />
      </button>
      <button type="button" class="sb2__close" :aria-label="t('close')" @click="emit('close')">
        <StudioIcon name="x" :size="17" />
      </button>
    </div>

    <nav class="sb2__nav" aria-label="Main">
      <RouterLink
        v-for="it in MAIN"
        :key="it.path"
        :to="it.path"
        class="sb2__item"
        :class="{ 'sb2__item--active': isActive(it) }"
        :title="collapsed ? t(it.key) : undefined"
        @click="emit('close')"
      >
        <StudioIcon :name="it.icon" :size="18" class="sb2__ico" />
        <span class="sb2__label">{{ t(it.key) }}</span>
      </RouterLink>

      <template v-for="g in TOOL_GROUPS" :key="g.key">
        <div class="sb2__group-label">{{ t(g.titleKey) }}</div>
        <RouterLink
          v-for="it in g.items"
          :key="it.path"
          :to="it.path"
          class="sb2__item sb2__item--tool"
          :class="{ 'sb2__item--active': isActive(it) }"
          :title="collapsed ? t(it.titleKey) : undefined"
          @click="emit('close')"
        >
          <Icon :name="it.icon" :size="17" class="sb2__ico" />
          <span class="sb2__label">{{ t(it.titleKey) }}</span>
          <span v-if="it.badge === 'new'" class="sb2__new">{{ t('stNewBadge') }}</span>
          <span v-else-if="it.badge === 'hot'" class="sb2__new sb2__new--hot">{{ t('uiBadgeHot') }}</span>
        </RouterLink>
      </template>

      <div class="sb2__divider"></div>

      <div class="sb2__group-label">{{ t('uiGroupMine') }}</div>
      <RouterLink
        v-for="it in MINE"
        :key="it.path"
        :to="it.path"
        class="sb2__item sb2__item--tool"
        :class="{ 'sb2__item--active': isActive(it) }"
        :title="collapsed ? t(it.titleKey) : undefined"
        @click="emit('close')"
      >
        <Icon :name="it.icon" :size="17" class="sb2__ico" />
        <span class="sb2__label">{{ t(it.titleKey) }}</span>
      </RouterLink>
    </nav>

    <div class="sb2__spacer"></div>

    <div class="sb2__upgrade">
      <StudioIcon name="crown" :size="24" class="sb2__crown" />
      <h4>{{ t('stUpgradeTitle') }}</h4>
      <p>{{ t('stUpgradeBody') }}</p>
      <RouterLink to="/recharge" class="sb2__upgrade-btn" @click="emit('close')">
        {{ t('stUpgradeBtn') }}
      </RouterLink>
    </div>

    <RouterLink :to="userState.loggedIn ? '/profile' : '/login'" class="sb2__user" @click="emit('close')">
      <img v-if="userState.loggedIn && userState.info?.avatarUrl" :src="avatarUrl" class="sb2__avatar" alt="" />
      <span v-else class="sb2__avatar">{{ initials }}</span>
      <span class="sb2__user-meta">
        <strong>{{ userState.loggedIn ? userName : t('uiLoginRegister') }}</strong>
        <span>{{ userState.loggedIn ? userMail : credits + ' ' + t('stCredits') }}</span>
      </span>
    </RouterLink>
  </aside>
</template>
