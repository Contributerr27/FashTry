import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { bootI18n } from './lib/i18n.js';
import { initAccessTokenAutoRefresh } from './lib/api.js';
import { markCommercialNoticePendingAfterRegister } from './lib/commercialNoticeGate.js';
import {
  markOnboardingTryonPendingAfterRegister,
  ONBOARDING_TRYON_PATH,
} from './lib/onboardingTryonGate.js';
import { updateSeoForRoute } from './lib/seo.js';
import './assets/css/common.css';
import './assets/css/workbench.css';
import './assets/css/studio.css';

bootI18n();

/**
 * Google OAuth 回调可能落在任意路径（历史配置曾用 /login.html、或误落到 /）。
 * 在首屏挂载前消费 query 中的 token 并 replace 掉敏感参数，避免仅 Login 页能处理导致首页漏处理。
 */
function consumeGoogleOAuthIfPresent() {
  const r = router.currentRoute.value;
  const q = r.query;
  if (q.googleSuccess !== '1' || q.accessToken == null || String(q.accessToken) === '') {
    return Promise.resolve();
  }
  localStorage.setItem('accessToken', String(q.accessToken));
  localStorage.setItem('refreshToken', q.refreshToken != null ? String(q.refreshToken) : '');
  let path = '/';
  if (q.return != null && String(q.return).trim() !== '') {
    try {
      path = decodeURIComponent(String(q.return));
    } catch {
      path = String(q.return);
    }
  }
  if (!path.startsWith('/') || path.startsWith('//')) {
    path = '/';
  }
  if (q.newRegistration === '1' || q.newRegistration === 1) {
    markCommercialNoticePendingAfterRegister();
    markOnboardingTryonPendingAfterRegister(path);
    path = ONBOARDING_TRYON_PATH;
  }
  return router.replace({ path, query: {}, hash: '' });
}

const app = createApp(App);
app.use(router);
router.afterEach((to) => {
  updateSeoForRoute(to);
});
router.isReady().then(() => consumeGoogleOAuthIfPresent()).finally(() => {
  app.mount('#app');
  if (localStorage.getItem('accessToken') && localStorage.getItem('refreshToken')) {
    initAccessTokenAutoRefresh();
  }
});
