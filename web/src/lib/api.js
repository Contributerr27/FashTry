/**
 * API 客户端 - 统一请求封装，支持内存缓存与预加载
 */
import { i18n } from './i18n.js';
import { auth } from './auth.js';

const API_BASE = import.meta.env.VITE_API_BASE || '';

/** accessToken 到期前提前刷新（与后端 JWT exp 对齐） */
const REFRESH_BEFORE_EXPIRY_MS = 5 * 60 * 1000;
const FALLBACK_REFRESH_INTERVAL_MS = 50 * 60 * 1000;
let proactiveRefreshTimer = null;
let refreshTokenPromise = null;
let visibilityListenerBound = false;

function parseJwtExpMs(token) {
  if (!token || typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const pad = base64.length % 4;
    if (pad) base64 += '='.repeat(4 - pad);
    const payload = JSON.parse(atob(base64));
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

function scheduleProactiveTokenRefresh() {
  if (proactiveRefreshTimer != null) {
    clearTimeout(proactiveRefreshTimer);
    proactiveRefreshTimer = null;
  }
  const access = localStorage.getItem('accessToken');
  const refresh = localStorage.getItem('refreshToken');
  if (!access || !refresh) return;
  const expMs = parseJwtExpMs(access);
  const now = Date.now();
  let delay;
  if (expMs != null) {
    if (expMs <= now + 60 * 1000) {
      delay = 3000;
    } else {
      delay = Math.max(expMs - now - REFRESH_BEFORE_EXPIRY_MS, 15 * 1000);
    }
  } else {
    delay = FALLBACK_REFRESH_INTERVAL_MS;
  }
  proactiveRefreshTimer = setTimeout(async () => {
    proactiveRefreshTimer = null;
    const ok = await refreshToken();
    if (ok) scheduleProactiveTokenRefresh();
  }, delay);
}

/**
 * 登录后或已登录用户进入应用时调用：定时刷新 accessToken，切回标签页时若即将过期则立即刷新。
 */
export function initAccessTokenAutoRefresh() {
  scheduleProactiveTokenRefresh();
  if (visibilityListenerBound) return;
  visibilityListenerBound = true;
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') return;
    const access = localStorage.getItem('accessToken');
    const refresh = localStorage.getItem('refreshToken');
    if (!access || !refresh) return;
    const expMs = parseJwtExpMs(access);
    if (expMs != null && expMs - Date.now() < 10 * 60 * 1000) {
      refreshToken().then((ok) => {
        if (ok) scheduleProactiveTokenRefresh();
      });
    }
  });
}

export function stopAccessTokenAutoRefresh() {
  if (proactiveRefreshTimer != null) {
    clearTimeout(proactiveRefreshTimer);
    proactiveRefreshTimer = null;
  }
}

const CACHE = {
  TTL_PACKAGES: 5 * 60 * 1000,
  TTL_GARMENTS: 5 * 60 * 1000,
  TTL_USER: 30 * 1000,
  packages: {},
  garments: {},
  user: null,
  userAt: 0,
  _userPromise: null,
  invalidateUser() {
    this.user = null;
    this.userAt = 0;
    this._userPromise = null;
  },
};

function getLocaleHeader() {
  return i18n.getLocale ? i18n.getLocale() : 'zh';
}

function loginRedirectUrl() {
  const ret = encodeURIComponent(
    window.location.pathname + window.location.search + window.location.hash
  );
  return `/login?return=${ret}`;
}

async function request(url, options = {}) {
  const token = localStorage.getItem('accessToken');
  const headers = {
    'Content-Type': 'application/json',
    'X-Locale': getLocaleHeader(),
    ...options.headers,
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(API_BASE + url, { ...options, headers });
  } catch (e) {
    const hint = API_BASE
      ? '。若与 API 不同源请检查后端 CORS 及端口；dev 配置下已允许 http://localhost:* / 127.0.0.1:*'
      : '。开发环境默认通过 Vite 将 /api 代理到 http://127.0.0.1:18888；可通过环境变量 VITE_PROXY_TARGET 修改后重启 dev';
    throw new Error((e && e.message ? e.message : '网络请求失败') + hint);
  }

  const data = await res.json().catch(() => ({}));

  if (data.code === 4010) {
    const refreshed = await refreshToken();
    if (refreshed) {
      return request(url, options);
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = loginRedirectUrl();
    throw new Error(i18n.formatApiError('请重新登录'));
  }

  if (data.code !== 200 && data.code !== 0) {
    const msg = data.message || '请求失败';
    const err = new Error(i18n.formatApiError(msg));
    err.rawMessage = msg;
    throw err;
  }
  return data.data;
}

/**
 * multipart/form-data（勿手动设置 Content-Type），行为与 {@link request} 一致（含 401 刷新重试）。
 */
async function requestFormData(url, formData, options = {}) {
  const token = localStorage.getItem('accessToken');
  const headers = {
    'X-Locale': getLocaleHeader(),
    ...options.headers,
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let res;
  try {
    res = await fetch(API_BASE + url, {
      method: 'POST',
      body: formData,
      ...options,
      headers,
    });
  } catch (e) {
    const hint = API_BASE
      ? '。若与 API 不同源请检查后端 CORS 及端口；dev 配置下已允许 http://localhost:* / 127.0.0.1:*'
      : '。开发环境默认通过 Vite 将 /api 代理到 http://127.0.0.1:18888；可通过环境变量 VITE_PROXY_TARGET 修改后重启 dev';
    throw new Error((e && e.message ? e.message : '网络请求失败') + hint);
  }

  const data = await res.json().catch(() => ({}));

  if (data.code === 4010) {
    const refreshed = await refreshToken();
    if (refreshed) {
      return requestFormData(url, formData, options);
    }
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    window.location.href = loginRedirectUrl();
    throw new Error(i18n.formatApiError('请重新登录'));
  }

  if (data.code !== 200 && data.code !== 0) {
    const msg = data.message || '请求失败';
    const err = new Error(i18n.formatApiError(msg));
    err.rawMessage = msg;
    throw err;
  }
  return data.data;
}

async function refreshToken() {
  if (refreshTokenPromise) return refreshTokenPromise;
  refreshTokenPromise = (async () => {
    const refresh = localStorage.getItem('refreshToken');
    if (!refresh) return false;
    try {
      const res = await fetch(API_BASE + '/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: refresh }),
      });
      const data = await res.json();
      if (data.code === 200 && data.data) {
        localStorage.setItem('accessToken', data.data.accessToken);
        if (data.data.refreshToken) {
          localStorage.setItem('refreshToken', data.data.refreshToken);
        }
        scheduleProactiveTokenRefresh();
        return true;
      }
    } catch (e) {
      /* ignore */
    }
    return false;
  })();
  try {
    return await refreshTokenPromise;
  } finally {
    refreshTokenPromise = null;
  }
}

/** COS 直传成功后确认落库（失败不阻断上传流程） */
async function confirmPresignUpload(category, presignData, file) {
  try {
    const token = localStorage.getItem('accessToken');
    const path = category === 'other' ? '/api/upload/other/confirm' : '/api/upload/business/confirm';
    await fetch(API_BASE + path, {
      method: 'POST',
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        fileUrl: presignData.fileUrl,
        objectKey: presignData.objectKey,
        fileName: file?.name,
        fileSize: file?.size,
        category: category === 'other' ? 'other' : 'business',
      }),
    });
  } catch {
    /* ignore */
  }
}

export const api = {
  getCaptcha: () => request('/api/auth/captcha'),
  getGoogleLoginUrl: (state) =>
    request('/api/auth/google/login-url' + (state ? '?state=' + encodeURIComponent(state) : '')),
  sendSms: (body) => request('/api/auth/sms/send', { method: 'POST', body: JSON.stringify(body) }),
  sendEmailCode: (body) => request('/api/auth/email/send', { method: 'POST', body: JSON.stringify(body) }),
  phoneLogin: (body) => request('/api/auth/phone/login', { method: 'POST', body: JSON.stringify(body) }),
  emailLogin: (body) => request('/api/auth/email/login', { method: 'POST', body: JSON.stringify(body) }),

  getUserInfo: async () => {
    if (CACHE.user && Date.now() - CACHE.userAt < CACHE.TTL_USER) return CACHE.user;
    if (CACHE._userPromise) return CACHE._userPromise;
    CACHE._userPromise = (async () => {
      try {
        const data = await request('/api/user/info');
        CACHE.user = data;
        CACHE.userAt = Date.now();
        return data;
      } finally {
        CACHE._userPromise = null;
      }
    })();
    return CACHE._userPromise;
  },
  updateProfile: (body) => request('/api/user/profile', { method: 'PUT', body: JSON.stringify(body) }),
  logout: () => request('/api/user', { method: 'POST' }),

  /** 问题反馈（公开接口；已登录会附带 JWT） */
  submitFeedback: (body) => request('/api/public/feedback', { method: 'POST', body: JSON.stringify(body) }),

  submitTryon: (body) => request('/api/tryon/submit', { method: 'POST', body: JSON.stringify(body) }),
  /** 批量试穿：{ modelUrl, garmentUrls: string[], category?, outputResolution?（1K|2K|4K，默认 1K）} → { taskIds: string[] } */
  submitTryonBatch: (body) => request('/api/tryon/submit-batch', { method: 'POST', body: JSON.stringify(body) }),
  getTaskStatus: (taskId) => request(`/api/tryon/task?taskId=${taskId}`),
  /** 批量查询试穿任务状态：{ taskIds: string[] } → TryonStatusVO[]（顺序与 taskIds 一致） */
  getTryonTasksStatus: (taskIds) =>
    request('/api/tryon/tasks-status', {
      method: 'POST',
      body: JSON.stringify({ taskIds }),
    }),
  getMyTasks: (page = 1, size = 10) => request(`/api/tryon/myTasks?page=${page}&size=${size}`),

  submitProductToModel: (body) =>
    request('/api/product-to-model/submit', { method: 'POST', body: JSON.stringify(body) }),
  getProductToModelStatus: (taskId) => request(`/api/product-to-model/task?taskId=${taskId}`),
  getProductToModelTasks: (page = 1, size = 10) =>
    request(`/api/product-to-model/myTasks?page=${page}&size=${size}`),

  getModelVideoPricing: () => request('/api/model-video/pricing'),
  submitModelVideo: (body) => request('/api/model-video/submit', { method: 'POST', body: JSON.stringify(body) }),
  getModelVideoTask: (taskId) => request(`/api/model-video/tasks/${taskId}`),
  listModelVideoTasks: (page = 1, size = 20) =>
    request(`/api/model-video/tasks?page=${page}&size=${size}`),

  /**
   * 业务图：优先 COS 直传（预签名 PUT），失败则走后端 multipart。
   */
  uploadBusiness: async (file) => {
    const token = localStorage.getItem('accessToken');
    const authHeaders = token ? { Authorization: `Bearer ${token}` } : {};

    try {
      const presignRes = await fetch(API_BASE + '/api/upload/business/presign', {
        method: 'POST',
        headers: { ...authHeaders, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fileName: file.name || 'upload.jpg',
          contentType: file.type || 'image/jpeg',
          fileSize: file.size,
        }),
      });
      const presignJson = await presignRes.json();
      if (presignJson.code === 4010) {
        const ok = await refreshToken();
        if (ok) return api.uploadBusiness(file);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = loginRedirectUrl();
        throw new Error(i18n.formatApiError('请重新登录'));
      }
      if (
        (presignJson.code === 200 || presignJson.code === 0) &&
        presignJson.data?.uploadUrl &&
        presignJson.data?.fileUrl
      ) {
        const putRes = await fetch(presignJson.data.uploadUrl, {
          method: 'PUT',
          headers: file.type ? { 'Content-Type': file.type } : {},
          body: file,
        });
        if (putRes.ok) {
          await confirmPresignUpload('business', presignJson.data, file);
          return presignJson.data.fileUrl;
        }
      }
    } catch (e) {
      /* 直传失败（如 CORS），回退 */
    }

    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(API_BASE + '/api/upload/business', {
      method: 'POST',
      headers: authHeaders,
      body: formData,
    });
    const data = await res.json();
    if (data.code === 4010) {
      const ok = await refreshToken();
      if (ok) return api.uploadBusiness(file);
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = loginRedirectUrl();
      throw new Error(i18n.formatApiError('请重新登录'));
    }
    if (data.code !== 200 && data.code !== 0) {
      const msg = data.message || '上传失败';
      throw new Error(i18n.formatApiError(msg));
    }
    return data.data.url;
  },

  /**
   * 头像等：优先 COS 直传，失败则走后端 multipart。
   */
  uploadOther: async (file) => {
    const token = localStorage.getItem('accessToken');
    const authHeaders = token ? { Authorization: `Bearer ${token}` } : {};

    try {
      const presignRes = await fetch(API_BASE + '/api/upload/other/presign', {
        method: 'POST',
        headers: { ...authHeaders, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fileName: file.name || 'avatar.jpg',
          contentType: file.type || 'image/jpeg',
          fileSize: file.size,
        }),
      });
      const presignJson = await presignRes.json();
      if (presignJson.code === 4010) {
        const ok = await refreshToken();
        if (ok) return api.uploadOther(file);
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = loginRedirectUrl();
        throw new Error(i18n.formatApiError('请重新登录'));
      }
      if (
        (presignJson.code === 200 || presignJson.code === 0) &&
        presignJson.data?.uploadUrl &&
        presignJson.data?.fileUrl
      ) {
        const putRes = await fetch(presignJson.data.uploadUrl, {
          method: 'PUT',
          headers: file.type ? { 'Content-Type': file.type } : {},
          body: file,
        });
        if (putRes.ok) {
          await confirmPresignUpload('other', presignJson.data, file);
          return presignJson.data.fileUrl;
        }
      }
    } catch (e) {
      /* 回退 */
    }

    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(API_BASE + '/api/upload/other', {
      method: 'POST',
      headers: authHeaders,
      body: formData,
    });
    const data = await res.json();
    if (data.code === 4010) {
      const ok = await refreshToken();
      if (ok) return api.uploadOther(file);
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = loginRedirectUrl();
      throw new Error(i18n.formatApiError('请重新登录'));
    }
    if (data.code !== 200 && data.code !== 0) {
      const msg = data.message || '上传失败';
      throw new Error(i18n.formatApiError(msg));
    }
    return data.data.url;
  },

  /** @param {{ page?: number, size?: number, sceneCategory?: string, gender?: string }} params */
  getPublicGarments: async (params = {}) => {
    const page = params.page != null ? Number(params.page) : 1;
    const size = params.size != null ? Number(params.size) : 6;
    const u = new URLSearchParams();
    u.set('page', String(Math.max(1, page)));
    u.set('size', String(Math.max(1, Math.min(50, size))));
    if (params.sceneCategory) u.set('sceneCategory', String(params.sceneCategory));
    if (params.gender) u.set('gender', String(params.gender));
    const qs = u.toString();
    const key = qs;
    const cached = CACHE.garments[key];
    if (cached && Date.now() - cached[1] < CACHE.TTL_GARMENTS) return cached[0];
    const data = await request(`/api/public/garments?${qs}`);
    CACHE.garments[key] = [data, Date.now()];
    return data;
  },

  getInviteInfo: () => request('/api/invite/info'),

  getPackages: async () => {
    const loc = getLocaleHeader();
    const key = loc || 'zh';
    const cached = CACHE.packages[key];
    if (cached && Date.now() - cached[1] < CACHE.TTL_PACKAGES) return cached[0];
    const data = await request('/api/recharge/packages');
    CACHE.packages[key] = [data, Date.now()];
    return data;
  },
  canPurchase: (packageId) => request(`/api/recharge/packages/can-purchase?packageId=${packageId}`),
  createOrder: (body) => request('/api/recharge/order', { method: 'POST', body: JSON.stringify(body) }),
  capturePayPal: (body) => request('/api/recharge/paypal/capture', { method: 'POST', body: JSON.stringify(body) }),
  getOrderStatus: (orderId) => request(`/api/recharge/order/${orderId}/status`),
  getMyOrders: (page = 1, size = 10) => request(`/api/recharge/orders?page=${page}&size=${size}`),

  invalidateUserCache: () => CACHE.invalidateUser(),

  /** 品牌入驻审核状态（需本地保存 submit 返回的 code，一般为 join-*） */
  getBrandIntakeStatus: (code) =>
    request(`/api/public/brand-intake/status?code=${encodeURIComponent(code)}`),

  /** 需登录：当前账号是否已提交 C 端入驻 */
  getMyBrandIntake: () => request('/api/user/brand-intake'),

  /**
   * 单图局部改款（需登录）：multipart 字段 file、instruction；可选 mask、reference、modelId、aspectRatio、imageSize。
   * @returns {Promise<{ taskId: number, consumeCredits?: number }>}
   */
  submitImageEdit: (formData) => requestFormData('/api/image-edit/submit', formData),

  /** 局部改款计费说明（需登录）：成功出图后扣点，失败不扣 */
  getImageEditPricing: () => request('/api/image-edit/pricing'),

  /** @returns {Promise<{ records: Array, total: number, page: number, pageSize: number }>} */
  listImageEditHistory: (page = 1, size = 12) =>
    request(`/api/image-edit/history?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`),

  /** @returns {Promise<{ taskId, status, urls?, errorMsg?, ... }>} */
  getImageEditTask: (taskId) =>
    request(`/api/image-edit/tasks/${encodeURIComponent(String(taskId))}`),

  /** 模特出图定价；可选 sceneVariantCount 与提交一致以便 consumeCredits 正确 */
  getPackshotPricing: (sceneVariantCount) => {
    const q =
      sceneVariantCount != null && sceneVariantCount !== ''
        ? `?sceneVariantCount=${encodeURIComponent(String(sceneVariantCount))}`
        : '';
    return request(`/api/clothing-packshot/pricing${q}`);
  },
  submitClothingPackshot: (formData) => requestFormData('/api/clothing-packshot/submit', formData),
  listPackshotHistory: (page = 1, size = 12) =>
    request(
      `/api/clothing-packshot/history?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`
    ),
  getPackshotTask: (taskId) =>
    request(`/api/clothing-packshot/tasks/${encodeURIComponent(String(taskId))}`),

  /**
   * 模特出图单张重生成：parentTaskId 为整套任务 ID，slotIndex 与成片顺序一致
   * @param {number|string} parentTaskId
   * @param {number} slotIndex
   */
  regeneratePackshotSlot: (parentTaskId, slotIndex) => {
    const fd = new FormData();
    fd.append('parentTaskId', String(parentTaskId));
    fd.append('slotIndex', String(slotIndex));
    return requestFormData('/api/clothing-packshot/regenerate-slot', fd);
  },

  /** 服装电商套图：固定 10 步 */
  /** 服装精修：{ enabled, maxFiles }，无模型名。 */
  getProductRetouchConfig: () => request('/api/product-retouch/config'),
  getProductRetouchPricing: (fileCount) => {
    const q =
      fileCount != null && fileCount !== ''
        ? `?fileCount=${encodeURIComponent(String(fileCount))}`
        : '';
    return request(`/api/product-retouch/pricing${q}`);
  },
  submitProductRetouch: (formData) => requestFormData('/api/product-retouch/submit', formData),
  listProductRetouchHistory: (page = 1, size = 12) =>
    request(
      `/api/product-retouch/history?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`
    ),
  getProductRetouchTask: (taskId) =>
    request(`/api/product-retouch/tasks/${encodeURIComponent(String(taskId))}`),

  regenerateProductRetouchSlot: (parentTaskId, slotIndex) => {
    const fd = new FormData();
    fd.append('parentTaskId', String(parentTaskId));
    fd.append('slotIndex', String(slotIndex));
    return requestFormData('/api/product-retouch/regenerate-slot', fd);
  },

  getPortraitStudioConfig: () => request('/api/portrait-studio/config'),
  getPortraitStudioPricing: (outputCount) => {
    const q =
      outputCount != null && outputCount !== ''
        ? `?outputCount=${encodeURIComponent(String(outputCount))}`
        : '';
    return request(`/api/portrait-studio/pricing${q}`);
  },
  submitPortraitStudio: (formData) => requestFormData('/api/portrait-studio/submit', formData),
  listPortraitStudioHistory: (page = 1, size = 12) =>
    request(
      `/api/portrait-studio/history?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`
    ),
  getPortraitStudioTask: (taskId) =>
    request(`/api/portrait-studio/tasks/${encodeURIComponent(String(taskId))}`),
  /**
   * AI 写真单张重生成：parentTaskId 为整套父任务 ID，slotIndex 为 0～outputCount-1
   */
  regeneratePortraitStudioSlot: (parentTaskId, slotIndex) => {
    const fd = new FormData();
    fd.append('parentTaskId', String(parentTaskId));
    fd.append('slotIndex', String(slotIndex));
    return requestFormData('/api/portrait-studio/regenerate-slot', fd);
  },

  /** 小红书图文卡片：AI 文案 JSON + 前端 HTML 模板导出 */
  getXhsCardPricing: () => request('/api/xhs-card/pricing'),
  generateXhsCard: (body) =>
    request('/api/xhs-card/generate', {
      method: 'POST',
      body: JSON.stringify(body || {}),
    }),
  listXhsCardHistory: (page = 1, size = 12) =>
    request(
      `/api/xhs-card/history?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`
    ),
  getXhsCardTask: (taskId) =>
    request(`/api/xhs-card/tasks/${encodeURIComponent(String(taskId))}`),

  getClothingProductSuitePricing: () => request('/api/clothing-product-suite/pricing'),
  submitClothingProductSuite: (formData) =>
    requestFormData('/api/clothing-product-suite/submit', formData),
  writeClothingProductSuiteAiBrief: (formData) =>
    requestFormData('/api/clothing-product-suite/ai-brief', formData),
  listClothingProductSuiteHistory: (page = 1, size = 12) =>
    request(
      `/api/clothing-product-suite/history?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`
    ),
  getClothingProductSuiteTask: (taskId) =>
    request(`/api/clothing-product-suite/tasks/${encodeURIComponent(String(taskId))}`),

  /**
   * 单张重生成：parentTaskId 为整套套图任务 ID，slotIndex 0～9
   * @param {number|string} parentTaskId
   * @param {number} slotIndex
   */
  regenerateClothingSuiteSlot: (parentTaskId, slotIndex) => {
    const fd = new FormData();
    fd.append('parentTaskId', String(parentTaskId));
    fd.append('slotIndex', String(slotIndex));
    return requestFormData('/api/clothing-product-suite/regenerate-slot', fd);
  },

  submitBrandIntake: async (formData) => {
    const headers = { 'X-Locale': getLocaleHeader() };
    const token = localStorage.getItem('accessToken');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const res = await fetch(API_BASE + '/api/public/brand-intake', {
      method: 'POST',
      headers,
      body: formData,
    });
    const data = await res.json().catch(() => ({}));
    if (data.code !== 200 && data.code !== 0) {
      throw new Error(i18n.formatApiError(data.message || '提交失败'));
    }
    return data.data;
  },

  /**
   * 统一作品历史：kind = all|tryon|image_edit|packshot|suite|retouch|portrait
   * @returns {Promise<{ records: Array<{ kind, sortAtMs, tryon?, imageEdit?, packshot?, suite?, retouch?, portrait? }>, total, page, pageSize }>}
   */
  getUnifiedTaskHistory: (kind = 'all', page = 1, pageSize = 20) =>
    request(
      `/api/mobile/task-history?kind=${encodeURIComponent(kind)}&page=${encodeURIComponent(String(page))}&pageSize=${encodeURIComponent(String(pageSize))}`
    ),

  /** 收藏列表：{ records: [{ id, imageUrl, thumbUrl, sourceType, sourceTaskId, title, createdAt }], total, page, pageSize } */
  listFavorites: (page = 1, size = 30, sourceType = '') =>
    request(
      `/api/favorites?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}` +
        (sourceType ? `&sourceType=${encodeURIComponent(sourceType)}` : '')
    ),
  /** 收藏（幂等）：{ imageUrl, thumbUrl?, sourceType?, sourceTaskId?, title? } */
  addFavorite: (body) => request('/api/favorites', { method: 'POST', body: JSON.stringify(body) }),
  removeFavorite: (imageUrl) =>
    request(`/api/favorites?imageUrl=${encodeURIComponent(imageUrl)}`, { method: 'DELETE' }),
  /** 批量查询收藏状态：imageUrls → 已收藏的 URL 数组 */
  checkFavorites: (imageUrls) =>
    request('/api/favorites/check', { method: 'POST', body: JSON.stringify({ imageUrls }) }),

  listApiKeys: () => request('/api/user/api-keys'),
  createApiKey: (name) =>
    request('/api/user/api-keys', {
      method: 'POST',
      body: JSON.stringify(name ? { name } : {}),
    }),
  revokeApiKey: (id) =>
    request(`/api/user/api-keys/${encodeURIComponent(String(id))}`, { method: 'DELETE' }),

  async preload() {
    const loc = getLocaleHeader();
    const promises = [];
    promises.push(
      request('/api/recharge/packages')
        .then((d) => {
          CACHE.packages[loc] = [d, Date.now()];
        })
        .catch(() => {})
    );
    promises.push(
      request('/api/public/garments?page=1&size=6')
        .then((d) => {
          CACHE.garments['page=1&size=6'] = [d, Date.now()];
        })
        .catch(() => {})
    );
    if (localStorage.getItem('accessToken')) {
      promises.push(
        this.getUserInfo()
          .then((d) => auth.setUser(d))
          .catch(() => {})
      );
    }
    await Promise.all(promises);
  },
};
