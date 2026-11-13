/**
 * 认证状态与工具
 */
export const auth = {
  isLoggedIn() {
    return !!localStorage.getItem('accessToken');
  },

  getUser() {
    try {
      return JSON.parse(localStorage.getItem('user') || '{}');
    } catch {
      return {};
    }
  },

  setUser(user) {
    if (user == null) {
      localStorage.removeItem('user');
      return;
    }
    localStorage.setItem('user', JSON.stringify(user || {}));
  },

  requireLogin(redirectPath = '/login') {
    if (!this.isLoggedIn()) {
      const returnTo = encodeURIComponent(
        window.location.pathname + window.location.search + window.location.hash
      );
      window.location.href = `${redirectPath}?return=${returnTo}`;
      return false;
    }
    return true;
  },
};
