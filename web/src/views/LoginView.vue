<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter, RouterLink } from 'vue-router';
import '../assets/css/login.css';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { suggestEmails } from '../lib/emailSuggest.js';
import { markCommercialNoticePendingAfterRegister } from '../lib/commercialNoticeGate.js';
import {
  markOnboardingTryonPendingAfterRegister,
  ONBOARDING_TRYON_PATH,
  shouldShowOnboardingTryon,
} from '../lib/onboardingTryonGate.js';
import LocaleSwitch from '../components/LocaleSwitch.vue';
import Icon from '../components/ui/Icon.vue';

const ASIDE_FEATURES = ['uiNavTryon', 'navEcomSuite', 'navPortraitStudio'];

const COUNTRY_CODES = [
  ['86', '中国', 'China'],
  ['1', '美国/加拿大', 'US/Canada'],
  ['90', '土耳其', 'Turkey'],
  ['66', '泰国', 'Thailand'],
  ['55', '巴西', 'Brazil'],
  ['48', '波兰', 'Poland'],
  ['853', '中国澳门', 'Macau'],
  ['357', '塞浦路斯', 'Cyprus'],
  ['82', '韩国', 'South Korea'],
  ['351', '葡萄牙', 'Portugal'],
  ['389', '马其顿', 'Macedonia'],
  ['264', '纳米比亚', 'Namibia'],
  ['65', '新加坡', 'Singapore'],
  ['56', '智利', 'Chile'],
  ['886', '中国台湾', 'Taiwan'],
  ['34', '西班牙', 'Spain'],
  ['44', '英国', 'UK'],
  ['61', '澳大利亚', 'Australia'],
  ['52', '墨西哥', 'Mexico'],
  ['41', '瑞士', 'Switzerland'],
  ['370', '立陶宛', 'Lithuania'],
  ['46', '瑞典', 'Sweden'],
  ['45', '丹麦', 'Denmark'],
  ['852', '中国香港', 'Hong Kong'],
  ['81', '日本', 'Japan'],
  ['1671', '关岛', 'Guam'],
  ['372', '爱沙尼亚', 'Estonia'],
  ['39', '意大利', 'Italy'],
  ['30', '希腊', 'Greece'],
  ['371', '拉脱维亚', 'Latvia'],
];

const router = useRouter();
const vueRoute = useRoute();

function resolveReturnPath() {
  const returnTo = vueRoute.query.return || '/';
  return typeof returnTo === 'string' ? decodeURIComponent(returnTo) : '/';
}

function afterAuthSuccess(isNewRegistration) {
  const returnPath = resolveReturnPath();
  if (isNewRegistration) {
    markCommercialNoticePendingAfterRegister();
    markOnboardingTryonPendingAfterRegister(returnPath);
    window.location.href = ONBOARDING_TRYON_PATH;
    return;
  }
  window.location.href = returnPath;
}

const selectedCode = ref('86');
const dropdownOpen = ref(false);
const mobile = ref('');
const captchaCode = ref('');
const captchaKey = ref('');
const captchaImg = ref('');
const captchaAlt = ref('');
const smsCode = ref('');
const sendSmsDisabled = ref(false);
const sendSmsLabel = ref('');
const loginBtnDisabled = ref(false);
const loginBtnLabel = ref('');
const toastMsg = ref('');
const toastType = ref('info');
const toastVisible = ref(false);
const inviteHintVisible = ref(false);
const googleUrl = ref('');
const localeKey = ref(0);

/** phone | email */
const loginMode = ref('phone');
const emailAddr = ref('');
const emailOtp = ref('');
const sendEmailDisabled = ref(false);
const sendEmailLabel = ref('');

const emailSuggestions = computed(() => suggestEmails(emailAddr.value));
const emailSuggestOpen = ref(false);
let emailSuggestBlurTimer = null;

function onEmailFocus() {
  if (emailSuggestBlurTimer) clearTimeout(emailSuggestBlurTimer);
  emailSuggestOpen.value = true;
}

function onEmailBlur() {
  emailSuggestBlurTimer = setTimeout(() => {
    emailSuggestOpen.value = false;
  }, 200);
}

function pickEmailSuggestion(full) {
  emailAddr.value = full;
  emailSuggestOpen.value = false;
}

const countryOptions = computed(() => {
  localeKey.value;
  const isEn = i18n.getLocale() === 'en';
  return COUNTRY_CODES.map(([code, zh, en]) => ({
    code,
    name: isEn ? en : zh,
    active: code === selectedCode.value,
  }));
});

function t(key, params) {
  return i18n.t(key, params);
}

function showToast(msg, type = 'info') {
  toastMsg.value = msg;
  toastType.value = type;
  toastVisible.value = true;
  setTimeout(() => {
    toastVisible.value = false;
  }, 3000);
}

function setSelectedCode(code) {
  selectedCode.value = code;
  dropdownOpen.value = false;
}

function getFullMobile() {
  const num = mobile.value.replace(/\s/g, '');
  if (!num) return '';
  return '+' + selectedCode.value + num;
}

async function loadCaptcha() {
  captchaAlt.value = t('loading');
  try {
    const data = await api.getCaptcha();
    if (data?.captchaKey && data?.captchaImage) {
      captchaKey.value = data.captchaKey;
      captchaImg.value = 'data:image/png;base64,' + data.captchaImage;
      captchaAlt.value = t('captcha');
    } else {
      showToast(t('errCaptchaInvalid'), 'error');
    }
  } catch (e) {
    showToast(e.message || t('errCaptchaInvalid'), 'error');
    captchaAlt.value = t('captcha');
  }
}

async function onSendEmail() {
  const em = String(emailAddr.value || '').trim();
  if (!em || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(em)) {
    showToast(t('errInvalidEmail'), 'error');
    return;
  }
  if (!captchaCode.value.trim()) {
    showToast(t('errCaptchaInvalid'), 'error');
    return;
  }
  try {
    sendEmailDisabled.value = true;
    await api.sendEmailCode({
      email: em,
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value.trim(),
    });
    showToast(t('emailCodeSent'), 'success');
    let count = 60;
    const timer = setInterval(() => {
      count--;
      sendEmailLabel.value = t('resendIn', { n: count });
      if (count <= 0) {
        clearInterval(timer);
        sendEmailDisabled.value = false;
        sendEmailLabel.value = t('getEmailCode');
      }
    }, 1000);
  } catch (e) {
    showToast(e.message || t('sendFail'), 'error');
    sendEmailDisabled.value = false;
    loadCaptcha();
  }
}

async function onSubmitEmail() {
  const em = String(emailAddr.value || '').trim();
  if (!em || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(em)) {
    showToast(t('errInvalidEmail'), 'error');
    return;
  }
  const code = String(emailOtp.value || '').replace(/\D/g, '').slice(0, 6);
  if (code.length !== 6) {
    showToast(t('errSmsCodeInvalid'), 'error');
    return;
  }
  try {
    loginBtnDisabled.value = true;
    loginBtnLabel.value = t('logining');
    const body = { email: em, code, registerSource: 'web' };
    const inviteCode = localStorage.getItem('inviteCode');
    if (inviteCode) body.invitationCode = inviteCode.trim();
    const data = await api.emailLogin(body);
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken || '');
    auth.setUser({
      userId: data.userId,
      nickname: data.nickname,
      mobile: data.mobile,
      invitationCode: data.invitationCode,
    });
    showToast(t('loginSuccess'), 'success');
    if (inviteCode) localStorage.removeItem('inviteCode');
    afterAuthSuccess(data.newRegistration === true);
  } catch (e) {
    showToast(e.message || t('loginFail'), 'error');
    loginBtnDisabled.value = false;
    loginBtnLabel.value = t('loginRegister');
  }
}

async function onSendSms() {
  const m = getFullMobile();
  if (!/^\+?[1-9]\d{1,14}$/.test(m.replace(/\s/g, ''))) {
    showToast(t('errInvalidMobile'), 'error');
    return;
  }
  if (!captchaCode.value.trim()) {
    showToast(t('errCaptchaInvalid'), 'error');
    return;
  }
  try {
    sendSmsDisabled.value = true;
    await api.sendSms({
      mobile: m,
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value.trim(),
    });
    showToast(t('smsSent'), 'success');
    let count = 60;
    const timer = setInterval(() => {
      count--;
      sendSmsLabel.value = t('resendIn', { n: count });
      if (count <= 0) {
        clearInterval(timer);
        sendSmsDisabled.value = false;
        sendSmsLabel.value = t('getSmsCode');
      }
    }, 1000);
  } catch (e) {
    showToast(e.message || t('sendFail'), 'error');
    sendSmsDisabled.value = false;
    loadCaptcha();
  }
}

async function onSubmit(e) {
  e.preventDefault();
  const m = getFullMobile();
  if (!/^\+?[1-9]\d{1,14}$/.test(m.replace(/\s/g, ''))) {
    showToast(t('errInvalidMobile'), 'error');
    return;
  }
  if (!smsCode.value.trim()) {
    showToast(t('errSmsCodeInvalid'), 'error');
    return;
  }
  try {
    loginBtnDisabled.value = true;
    loginBtnLabel.value = t('logining');
    const body = { mobile: m, code: smsCode.value.trim(), registerSource: 'web' };
    const inviteCode = localStorage.getItem('inviteCode');
    if (inviteCode) body.invitationCode = inviteCode.trim();
    const data = await api.phoneLogin(body);
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('refreshToken', data.refreshToken || '');
    auth.setUser({
      userId: data.userId,
      nickname: data.nickname,
      mobile: data.mobile,
      invitationCode: data.invitationCode,
    });
    showToast(t('loginSuccess'), 'success');
    if (inviteCode) localStorage.removeItem('inviteCode');
    afterAuthSuccess(data.newRegistration === true);
  } catch (e) {
    showToast(e.message || t('loginFail'), 'error');
    loginBtnDisabled.value = false;
    loginBtnLabel.value = t('loginRegister');
  }
}

function onDocClick(e) {
  if (!e.target.closest?.('#countryPicker')) dropdownOpen.value = false;
}

function onLocaleChange() {
  localeKey.value++;
  sendSmsLabel.value = t('getSmsCode');
  sendEmailLabel.value = t('getEmailCode');
  loginBtnLabel.value = t('loginRegister');
}

onMounted(() => {
  sendSmsLabel.value = t('getSmsCode');
  sendEmailLabel.value = t('getEmailCode');
  loginBtnLabel.value = t('loginRegister');
  const q = vueRoute.query;
  if (q.googleSuccess === '1' && q.accessToken) {
    localStorage.setItem('accessToken', q.accessToken);
    localStorage.setItem('refreshToken', q.refreshToken || '');
    const isNew = q.newRegistration === '1' || q.newRegistration === 1;
    afterAuthSuccess(!!isNew);
    return;
  }
  if (auth.isLoggedIn()) {
    if (shouldShowOnboardingTryon()) {
      router.replace(ONBOARDING_TRYON_PATH);
      return;
    }
    const r = vueRoute.query.return;
    router.push(typeof r === 'string' ? decodeURIComponent(r) : '/');
    return;
  }
  loadCaptcha();
  const invite = vueRoute.query.invite || (vueRoute.hash && new URLSearchParams(vueRoute.hash.slice(1)).get('invite'));
  if (invite) {
    localStorage.setItem('inviteCode', String(invite).trim());
    inviteHintVisible.value = true;
    showToast(t('inviteHint'), 'info');
  } else {
    inviteHintVisible.value = !!localStorage.getItem('inviteCode');
  }
  const returnTo = vueRoute.query.return || '/';
  api.getGoogleLoginUrl(typeof returnTo === 'string' ? returnTo : '/')
    .then((data) => {
      if (data?.url) googleUrl.value = data.url;
    })
    .catch(() => {});
  window.addEventListener('click', onDocClick);
  window.addEventListener('localechange', onLocaleChange);
});
onUnmounted(() => {
  if (emailSuggestBlurTimer) clearTimeout(emailSuggestBlurTimer);
  window.removeEventListener('click', onDocClick);
  window.removeEventListener('localechange', onLocaleChange);
});
</script>

<template>
  <div class="login-wrap" :key="localeKey">
    <aside class="login-aside" aria-hidden="true">
      <img class="login-aside__img" src="/static/login/hero.jpg" alt="" />
      <RouterLink to="/" class="login-aside__logo">
        <span class="brand-mark brand-mark--lg brand-mark--ondark" aria-hidden="true">
          <img src="/static/logo-mark.png" alt="" />
        </span>
        <span>FashTry</span>
      </RouterLink>
      <div class="login-aside__body">
        <h2 class="login-aside__title">{{ i18n.t('uiHomeHeroTitle') }}</h2>
        <ul class="login-aside__feats">
          <li v-for="k in ASIDE_FEATURES" :key="k">
            <span class="login-aside__check"><Icon name="check" :size="13" :stroke-width="3" /></span>
            {{ i18n.t(k) }}
          </li>
        </ul>
      </div>
    </aside>

    <div class="login-panel">
      <div class="login-topbar">
        <RouterLink to="/" class="back-link">{{ i18n.t('backHome') }}</RouterLink>
        <LocaleSwitch />
      </div>

      <div class="login-card">
        <h1 class="login-title">{{ t('uiLoginHeading') }}</h1>
        <p class="login-sub">
          {{ t('uiLoginSub') }}
          <span class="login-pill"><Icon name="gift" :size="13" />{{ t('uiLoginTrialPill') }}</span>
        </p>
        <p v-show="inviteHintVisible" class="invite-hint">{{ i18n.t('inviteHint') }}</p>

        <div class="login-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            class="login-tab"
            :class="{ active: loginMode === 'phone' }"
            :aria-selected="loginMode === 'phone'"
            @click="loginMode = 'phone'"
          >
            {{ t('loginModePhone') }}
          </button>
          <button
            type="button"
            role="tab"
            class="login-tab"
            :class="{ active: loginMode === 'email' }"
            :aria-selected="loginMode === 'email'"
            @click="loginMode = 'email'"
          >
            {{ t('loginModeEmail') }}
          </button>
        </div>

        <form v-show="loginMode === 'phone'" class="login-form" @submit="onSubmit">
          <div class="lf">
            <div id="countryPicker" class="country-picker">
              <button
                type="button"
                class="country-trigger"
                aria-haspopup="listbox"
                :aria-expanded="dropdownOpen"
                @click.prevent="dropdownOpen = !dropdownOpen"
              >
                <span>+{{ selectedCode }}</span>
                <Icon class="country-chevron" name="chevronDown" :size="14" />
              </button>
              <div class="country-dropdown" role="listbox" :class="{ open: dropdownOpen }" :aria-hidden="!dropdownOpen">
                <button
                  v-for="opt in countryOptions"
                  :key="opt.code"
                  type="button"
                  class="country-option"
                  :class="{ active: opt.active }"
                  role="option"
                  :aria-selected="opt.active"
                  @click="setSelectedCode(opt.code)"
                >
                  <span class="country-option-code">+{{ opt.code }}</span>
                  <span>{{ opt.name }}</span>
                </button>
              </div>
            </div>
            <span class="lf__sep"></span>
            <input
              v-model="mobile"
              type="tel"
              class="lf__input"
              required
              maxlength="15"
              inputmode="numeric"
              pattern="[0-9]*"
              :aria-label="t('mobile')"
              :placeholder="t('mobilePlaceholder')"
            />
          </div>
          <div class="lf">
            <Icon class="lf__icon" name="shield" :size="17" />
            <input
              v-model="captchaCode"
              type="text"
              class="lf__input"
              required
              maxlength="4"
              :aria-label="t('captcha')"
              :placeholder="t('captchaPlaceholder')"
            />
            <img :src="captchaImg" class="captcha-img" :alt="captchaAlt || t('captcha')" :title="t('captcha')" @click="loadCaptcha" />
          </div>
          <div class="lf">
            <Icon class="lf__icon" name="lock" :size="17" />
            <input
              v-model="smsCode"
              type="text"
              class="lf__input"
              required
              maxlength="6"
              inputmode="numeric"
              :aria-label="t('smsCode')"
              :placeholder="t('smsPlaceholder')"
            />
            <button type="button" class="lf__action" :disabled="sendSmsDisabled" @click="onSendSms">
              {{ sendSmsLabel }}
            </button>
          </div>
          <button type="submit" class="btn btn-primary login-submit" :disabled="loginBtnDisabled">
            {{ loginBtnLabel }}
          </button>
        </form>

        <form v-show="loginMode === 'email'" class="login-form" @submit.prevent="onSubmitEmail">
          <div class="lf email-input-wrap">
            <Icon class="lf__icon" name="mail" :size="17" />
            <input
              v-model="emailAddr"
              type="email"
              class="lf__input"
              autocomplete="email"
              :aria-label="t('emailLabel')"
              :placeholder="t('emailPlaceholder')"
              @focus="onEmailFocus"
              @blur="onEmailBlur"
            />
            <ul v-show="emailSuggestOpen && emailSuggestions.length" class="email-suggest-dropdown" role="listbox">
              <li
                v-for="s in emailSuggestions"
                :key="s"
                role="option"
                class="email-suggest-option"
                @mousedown.prevent="pickEmailSuggestion(s)"
              >
                {{ s }}
              </li>
            </ul>
          </div>
          <div class="lf">
            <Icon class="lf__icon" name="shield" :size="17" />
            <input
              v-model="captchaCode"
              type="text"
              class="lf__input"
              required
              maxlength="4"
              :aria-label="t('captcha')"
              :placeholder="t('captchaPlaceholder')"
            />
            <img :src="captchaImg" class="captcha-img" :alt="captchaAlt || t('captcha')" :title="t('captcha')" @click="loadCaptcha" />
          </div>
          <div class="lf">
            <Icon class="lf__icon" name="lock" :size="17" />
            <input
              v-model="emailOtp"
              type="text"
              class="lf__input"
              required
              maxlength="6"
              inputmode="numeric"
              pattern="[0-9]*"
              :aria-label="t('emailOtpLabel')"
              :placeholder="t('smsPlaceholder')"
            />
            <button type="button" class="lf__action" :disabled="sendEmailDisabled" @click="onSendEmail">
              {{ sendEmailLabel }}
            </button>
          </div>
          <button type="submit" class="btn btn-primary login-submit" :disabled="loginBtnDisabled">
            {{ loginBtnLabel }}
          </button>
        </form>

        <template v-if="googleUrl">
          <div class="login-divider"><span>{{ i18n.t('or') }}</span></div>
          <a :href="googleUrl" class="btn-google">
            <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
              <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 8 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
              <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 8 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
              <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
              <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
            </svg>
            {{ t('uiLoginGoogle') }}
          </a>
        </template>

        <p class="login-agree">
          {{ t('uiLoginAgree') }}
          <RouterLink to="/terms">{{ i18n.t('navTerms') }}</RouterLink>
          {{ t('uiLoginAnd') }}
          <RouterLink to="/privacy">{{ i18n.t('navPrivacy') }}</RouterLink>
        </p>
      </div>
    </div>

    <div id="toast" :class="['toast', toastType]" :style="{ display: toastVisible ? 'block' : 'none' }">
      {{ toastMsg }}
    </div>
  </div>
  <footer class="site-footer">
    <span>© 2026 可腾网络科技</span>
    <RouterLink to="/privacy">{{ i18n.t('navPrivacy') }}</RouterLink>
    <RouterLink to="/terms">{{ i18n.t('navTerms') }}</RouterLink>
    <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">粤ICP备2022144912号-5</a>
  </footer>
</template>

