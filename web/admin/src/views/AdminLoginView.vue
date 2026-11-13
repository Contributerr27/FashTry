<script setup>
import { ref, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { getCaptcha, adminLogin } from '../lib/api.js';
import { TOKEN_KEY } from '../lib/auth.js';

const router = useRouter();
const route = useRoute();

const username = ref('');
const password = ref('');
const captchaCode = ref('');
const captchaKey = ref('');
const captchaImage = ref('');
const loading = ref(false);
const errorMsg = ref('');

async function refreshCaptcha() {
  errorMsg.value = '';
  try {
    const data = await getCaptcha();
    captchaKey.value = data.captchaKey;
    captchaImage.value = data.captchaImage ? `data:image/png;base64,${data.captchaImage}` : '';
  } catch (e) {
    errorMsg.value = e.message || '获取验证码失败';
  }
}

onMounted(() => {
  refreshCaptcha();
});

async function onSubmit() {
  errorMsg.value = '';
  loading.value = true;
  try {
    const data = await adminLogin({
      username: username.value.trim(),
      password: password.value,
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value.trim(),
    });
    localStorage.setItem(TOKEN_KEY, data.accessToken);
    let target = '/dashboard';
    const redir = route.query.redirect;
    if (typeof redir === 'string' && redir.startsWith('/') && !redir.startsWith('//')) {
      target = redir.split('#')[0] || '/dashboard';
    }
    await router.replace(target);
  } catch (e) {
    errorMsg.value = e.message || '登录失败';
    await refreshCaptcha();
    captchaCode.value = '';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="admin-login">
    <div class="admin-login-card">
      <div class="admin-login-brand">
        <span class="admin-login-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" width="28" height="28">
            <path
              d="M16 8.2a2.2 2.2 0 1 0-2.2-2.2M16 8.2v1.6L8.2 16.2a1.5 1.5 0 0 0 .9 2.6h13.8a1.5 1.5 0 0 0 .9-2.6L16 9.8"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </span>
        <div class="admin-login-name">FashTry</div>
        <div class="admin-login-kicker">管理后台</div>
      </div>
      <h1 class="admin-login-title">登录</h1>

      <form class="admin-form" @submit.prevent="onSubmit">
        <label class="admin-label">账号</label>
        <input v-model="username" class="admin-input" type="text" autocomplete="username" required />

        <label class="admin-label">密码</label>
        <input v-model="password" class="admin-input" type="password" autocomplete="current-password" required />

        <label class="admin-label">验证码</label>
        <div class="admin-captcha-row">
          <input v-model="captchaCode" class="admin-input admin-captcha-input" type="text" maxlength="8" required />
          <button type="button" class="admin-captcha-img-wrap" title="点击刷新" @click="refreshCaptcha">
            <img v-if="captchaImage" :src="captchaImage" alt="验证码" class="admin-captcha-img" />
            <span v-else class="admin-captcha-placeholder">加载中</span>
          </button>
        </div>

        <p v-if="errorMsg" class="admin-error">{{ errorMsg }}</p>

        <button type="submit" class="admin-submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
      <p class="admin-login-foot">仅限管理员访问</p>
    </div>
  </div>
</template>
