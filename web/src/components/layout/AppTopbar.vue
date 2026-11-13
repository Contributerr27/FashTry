<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import StudioIcon from '../ui/StudioIcon.vue';
import LocaleSwitch from '../LocaleSwitch.vue';
import { useT } from '../../lib/useT.js';
import { userState, logout, displayName } from '../../lib/userStore.js';

const emit = defineEmits(['open-menu', 'open-search']);

const { t } = useT();
const router = useRouter();
const notifOpen = ref(false);
const userOpen = ref(false);
const root = ref(null);

const credits = computed(() => userState.info?.totalCredits ?? 420);
const avatarUrl = computed(() => userState.info?.avatarUrl || '');
const userName = computed(() => displayName(userState.info) || 'John Doe');
const initials = computed(() => {
  const n = userName.value.trim();
  if (!n) return 'JD';
  const parts = n.split(/\s+/);
  return (parts.length > 1 ? parts[0][0] + parts[1][0] : n.slice(0, 2)).toUpperCase();
});

function onDocClick(e) {
  if (!root.value?.contains(e.target)) {
    notifOpen.value = false;
    userOpen.value = false;
  }
}
async function onLogout() {
  userOpen.value = false;
  await logout();
  router.push('/');
}

onMounted(() => document.addEventListener('click', onDocClick));
onUnmounted(() => document.removeEventListener('click', onDocClick));
</script>

<template>
  <header ref="root" class="tb2">
    <div class="tb2__left">
      <button type="button" class="tb2__menu" :aria-label="t('uiOpenMenu')" @click="emit('open-menu')">
        <StudioIcon name="menu" :size="19" />
      </button>
      <button type="button" class="tb2__icon-btn" :aria-label="t('uiSearch')" @click="emit('open-search')">
        <StudioIcon name="search" :size="17" />
      </button>
    </div>

    <RouterLink to="/wallet" class="tb2__credits" :title="t('uiCreditsLeft')">
      <StudioIcon name="bolt" :size="15" class="tb2__bolt" />
      <span>{{ credits }} {{ t('stCredits') }}</span>
      <StudioIcon name="chevronDown" :size="13" class="tb2__chev" />
    </RouterLink>

    <button
      type="button"
      class="tb2__icon-btn"
      :aria-label="t('stNotifications')"
      :aria-expanded="notifOpen"
      @click="notifOpen = !notifOpen"
    >
      <StudioIcon name="bell" :size="17" />
    </button>
    <div v-if="notifOpen" class="tb2__notif-panel">{{ t('stNoNotifications') }}</div>

    <span class="tb2__lang"><LocaleSwitch /></span>

    <div class="tb2__user">
      <button
        type="button"
        class="tb2__avatar"
        :aria-expanded="userOpen"
        aria-haspopup="menu"
        :aria-label="userName"
        @click="userOpen = !userOpen"
      >
        <img v-if="avatarUrl" :src="avatarUrl" alt="" />
        <span v-else>{{ initials }}</span>
      </button>
      <div v-if="userOpen" class="tb2__user-menu" role="menu">
        <template v-if="userState.loggedIn">
          <RouterLink to="/profile" @click="userOpen = false">
            <StudioIcon name="user" :size="15" />{{ t('profile') }}
          </RouterLink>
          <RouterLink to="/works" @click="userOpen = false">
            <StudioIcon name="folder" :size="15" />{{ t('uiNavWorks') }}
          </RouterLink>
          <RouterLink to="/wallet" @click="userOpen = false">
            <StudioIcon name="card" :size="15" />{{ t('uiNavWallet') }}
          </RouterLink>
          <RouterLink to="/developer" @click="userOpen = false">
            <StudioIcon name="grid" :size="15" />{{ t('uiNavApi') }}
          </RouterLink>
          <button type="button" @click="onLogout">
            <StudioIcon name="logout" :size="15" />{{ t('logout') }}
          </button>
        </template>
        <RouterLink v-else to="/login" @click="userOpen = false">
          <StudioIcon name="user" :size="15" />{{ t('login') }}
        </RouterLink>
      </div>
    </div>
  </header>
</template>
