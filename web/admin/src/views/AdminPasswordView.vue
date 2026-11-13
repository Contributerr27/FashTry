<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { getCaptcha, adminChangePassword, adminLogout } from '../lib/api.js';

const router = useRouter();
const oldPassword = ref('');
const newPassword = ref('');
const captchaCode = ref('');
const captchaKey = ref('');
const captchaImage = ref('');
const err = ref('');
const loading = ref(false);

async function refreshCaptcha() {
  try {
    const data = await getCaptcha();
    captchaKey.value = data.captchaKey;
    captchaImage.value = data.captchaImage ? `data:image/png;base64,${data.captchaImage}` : '';
  } catch (e) {
    err.value = e.message || '验证码加载失败';
  }
}

refreshCaptcha();

async function submit() {
  err.value = '';
  loading.value = true;
  try {
    await adminChangePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value.trim(),
    });
    oldPassword.value = '';
    newPassword.value = '';
    captchaCode.value = '';
    await adminLogout();
    await router.replace('/login');
  } catch (e) {
    err.value = e.message || '修改失败';
    await refreshCaptcha();
    captchaCode.value = '';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="admin-page">
    <h2 class="admin-page-title">修改管理员密码</h2>
    <p class="admin-hint">修改成功后将退出登录。</p>
    <form class="admin-pass-form" @submit.prevent="submit">
      <label class="admin-label">当前密码</label>
      <input v-model="oldPassword" class="admin-input" type="password" autocomplete="current-password" required />

      <label class="admin-label">新密码（至少 6 位）</label>
      <input v-model="newPassword" class="admin-input" type="password" autocomplete="new-password" required minlength="6" />

      <label class="admin-label">验证码</label>
      <div class="admin-captcha-row">
        <input v-model="captchaCode" class="admin-input admin-captcha-input" required />
        <button type="button" class="admin-captcha-img-wrap" @click="refreshCaptcha">
          <img v-if="captchaImage" :src="captchaImage" alt="" class="admin-captcha-img" />
        </button>
      </div>

      <p v-if="err" class="admin-error">{{ err }}</p>

      <button type="submit" class="admin-btn admin-btn-primary admin-pass-submit" :disabled="loading">
        {{ loading ? '提交中…' : '确认修改' }}
      </button>
    </form>
  </div>
</template>
