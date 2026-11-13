import { reactive } from 'vue';
import { api, stopAccessTokenAutoRefresh } from './api.js';
import { auth } from './auth.js';

export const DEFAULT_AVATAR =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" fill="%236b7380"><circle cx="50" cy="40" r="20"/><path d="M20 85c0-16 13-30 30-30s30 14 30 30z"/></svg>'
  );

/** 顶栏 / 侧栏 / 首页共享的登录态与点数 */
export const userState = reactive({
  loggedIn: auth.isLoggedIn(),
  info: auth.isLoggedIn() ? auth.getUser() || {} : {},
  loading: false,
});

let bound = false;

export async function refreshUser() {
  userState.loggedIn = auth.isLoggedIn();
  if (!userState.loggedIn) {
    userState.info = {};
    return;
  }
  const cached = auth.getUser();
  if (cached && Object.keys(cached).length) userState.info = cached;
  userState.loading = true;
  try {
    const info = await api.getUserInfo();
    userState.info = info || {};
    auth.setUser(info);
  } catch {
    /* 保留缓存 */
  } finally {
    userState.loading = false;
  }
}

/** 生成完成后调用：强制刷新点数 */
export function refreshCredits() {
  api.invalidateUserCache?.();
  return refreshUser();
}

export async function logout() {
  try {
    await api.logout();
  } catch (_) {}
  stopAccessTokenAutoRefresh();
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  auth.setUser(null);
  api.invalidateUserCache?.();
  userState.loggedIn = false;
  userState.info = {};
}

export function bindUserStore() {
  if (bound || typeof window === 'undefined') return;
  bound = true;
  window.addEventListener('fashtry:wallet-updated', () => refreshCredits());
  window.addEventListener('storage', (e) => {
    if (e.key === 'accessToken' || e.key === 'user') refreshUser();
  });
}

export function displayName(info) {
  if (!info) return '';
  return info.nickname || info.username || (info.mobile ? String(info.mobile).replace(/^(\d{3})\d+(\d{4})$/, '$1****$2') : '') || info.email || '';
}
