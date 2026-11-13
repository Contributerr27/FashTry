<script setup>
import { onMounted, ref } from 'vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';
import { DEFAULT_AVATAR, refreshUser, logout, userState } from '../lib/userStore.js';
import { useRouter } from 'vue-router';

const { t } = useT();
const router = useRouter();

const loggedIn = ref(auth.isLoggedIn());
const nickname = ref('');
const account = ref('');
const avatar = ref('');
const saving = ref(false);
const uploading = ref(false);

function apply(info) {
  nickname.value = info?.nickname || '';
  account.value = info?.mobile || info?.email || '';
  avatar.value = info?.avatarUrl || '';
}

async function onAvatarChange(e) {
  const file = e.target.files?.[0];
  e.target.value = '';
  if (!file) return;
  if (!file.type.startsWith('image/')) return toast(t('errUploadImageOnly'), 'error');
  uploading.value = true;
  try {
    const url = await api.uploadOther(file);
    if (url) {
      const updated = await api.updateProfile({ avatarUrl: url });
      auth.setUser(updated);
      apply(updated);
      refreshUser();
      toast(t('success'), 'success');
    }
  } catch (err) {
    toast(err.message || t('errUploadFail'), 'error');
  } finally {
    uploading.value = false;
  }
}

async function onSubmit() {
  saving.value = true;
  try {
    const updated = await api.updateProfile({ nickname: nickname.value.trim() || undefined });
    auth.setUser(updated);
    apply(updated);
    refreshUser();
    toast(t('success'), 'success');
  } catch (err) {
    toast(err.message || t('errSaveFail'), 'error');
  } finally {
    saving.value = false;
  }
}

async function onLogout() {
  await logout();
  router.push('/');
}

onMounted(async () => {
  if (!loggedIn.value) return;
  try {
    apply(await api.getUserInfo());
  } catch {
    toast(t('loadFail'), 'error');
    router.push('/login?return=' + encodeURIComponent('/profile'));
  }
});
</script>

<template>
  <div class="page pf">
    <header class="page-head">
      <h1 class="page-title">{{ t('profileTitle') }}</h1>
    </header>

    <div v-if="!loggedIn" class="pf__card pf__login">
      <p v-html="t('loginToProfile')"></p>
      <RouterLink :to="'/login?return=' + encodeURIComponent('/profile')" class="btn btn-primary">{{ t('login') }}</RouterLink>
    </div>

    <template v-else>
      <form class="pf__card" @submit.prevent="onSubmit">
        <div class="pf__avatar-row">
          <label class="pf__avatar" :class="{ 'is-busy': uploading }">
            <img :src="avatar || DEFAULT_AVATAR" alt="" />
            <span class="pf__avatar-edit"><Icon name="upload" :size="16" /></span>
            <span v-if="uploading" class="pf__avatar-busy"><span class="spinner"></span></span>
            <input type="file" accept="image/*" hidden @change="onAvatarChange" />
          </label>
          <div>
            <div class="pf__name">{{ nickname || account || '—' }}</div>
            <div class="pf__sub">{{ t('changeAvatar') }}</div>
          </div>
        </div>

        <div class="pf__field">
          <label for="pf-nickname">{{ t('nickname') }}</label>
          <input id="pf-nickname" v-model="nickname" type="text" maxlength="50" :placeholder="t('nicknamePlaceholder')" />
        </div>
        <div class="pf__field">
          <label>{{ t('mobileOrEmail') }}</label>
          <input :value="account" type="text" readonly disabled />
        </div>
        <div class="pf__foot">
          <button type="submit" class="btn btn-primary" :disabled="saving">
            <span v-if="saving" class="spinner" style="width: 15px; height: 15px"></span>{{ t('save') }}
          </button>
        </div>
      </form>

      <section class="pf__card pf__links">
        <RouterLink to="/wallet" class="pf__link">
          <Icon name="wallet" :size="18" /><span>{{ t('uiNavWallet') }}</span>
          <small>{{ userState.info?.totalCredits ?? 0 }} {{ t('credits') }}</small>
          <Icon name="chevronRight" :size="16" />
        </RouterLink>
        <RouterLink to="/works" class="pf__link">
          <Icon name="images" :size="18" /><span>{{ t('uiNavWorks') }}</span><small></small>
          <Icon name="chevronRight" :size="16" />
        </RouterLink>
        <RouterLink to="/invite" class="pf__link">
          <Icon name="gift" :size="18" /><span>{{ t('navInvite') }}</span><small></small>
          <Icon name="chevronRight" :size="16" />
        </RouterLink>
        <RouterLink to="/developer" class="pf__link">
          <Icon name="code" :size="18" /><span>{{ t('uiNavApi') }}</span><small></small>
          <Icon name="chevronRight" :size="16" />
        </RouterLink>
        <button type="button" class="pf__link pf__link--danger" @click="onLogout">
          <Icon name="logout" :size="18" /><span>{{ t('logout') }}</span><small></small>
        </button>
      </section>
    </template>
  </div>
</template>

<style scoped>
.pf {
  max-width: 640px;
}
.pf__card {
  padding: 22px;
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
}
.pf__login {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
  color: var(--text-2);
}
.pf__login p {
  margin: 0;
}
.pf__avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 22px;
}
.pf__avatar {
  position: relative;
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 50%;
  cursor: pointer;
}
.pf__avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  background: var(--panel-2);
  border: 2px solid var(--line-strong);
}
.pf__avatar-edit {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--brand);
  color: var(--brand-ink);
  border: 3px solid var(--panel);
}
.pf__avatar-busy {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(11, 13, 16, 0.6);
}
.pf__name {
  font-size: 1.1rem;
  font-weight: 600;
}
.pf__sub {
  margin-top: 2px;
  font-size: 0.78rem;
  color: var(--text-3);
}
.pf__field {
  margin-bottom: 16px;
}
.pf__field label {
  display: block;
  margin-bottom: 6px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-2);
}
.pf__field input {
  width: 100%;
}
.pf__foot {
  display: flex;
  justify-content: flex-end;
}
.pf__links {
  padding: 6px;
}
.pf__link {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius);
  border: none;
  background: none;
  color: var(--text);
  font: inherit;
  font-size: 0.9rem;
  text-decoration: none;
  text-align: left;
  cursor: pointer;
}
.pf__link:hover {
  background: var(--panel-2);
}
.pf__link > span {
  flex: 1;
}
.pf__link small {
  font-size: 0.78rem;
  color: var(--text-3);
}
.pf__link > :deep(svg) {
  color: var(--text-3);
}
.pf__link--danger,
.pf__link--danger > :deep(svg) {
  color: var(--error);
}
</style>
