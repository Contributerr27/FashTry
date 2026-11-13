import { getToken, TOKEN_KEY } from './auth.js';

const JSON_HDR = { 'Content-Type': 'application/json' };

async function parseResult(res) {
  const text = await res.text();
  let body;
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    throw new Error(text?.slice(0, 120) || `HTTP ${res.status}`);
  }
  if (body.code === 4010) {
    localStorage.removeItem(TOKEN_KEY);
    throw new Error('登录已失效，请重新登录');
  }
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败');
  }
  return body.data;
}

export async function adminFetch(path, options = {}) {
  const headers = { ...options.headers };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const res = await fetch(path, { ...options, headers });
  return res;
}

export async function getCaptcha() {
  const res = await adminFetch('/api/admin/auth/captcha');
  return parseResult(res);
}

export async function adminLogin(body) {
  const res = await adminFetch('/api/admin/auth/login', {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminLogout() {
  const res = await adminFetch('/api/admin/auth/logout', { method: 'POST' });
  try {
    await parseResult(res);
  } catch {
    /* ignore */
  }
  localStorage.removeItem(TOKEN_KEY);
}

export async function adminMe() {
  const res = await adminFetch('/api/admin/me');
  return parseResult(res);
}

export async function adminStatsOverview() {
  const res = await adminFetch('/api/admin/stats/overview');
  return parseResult(res);
}

function qs(params) {
  const u = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v) !== '') {
      u.set(k, String(v));
    }
  });
  const s = u.toString();
  return s ? `?${s}` : '';
}

export async function adminPageUsers(page, pageSize, keyword) {
  const res = await adminFetch(`/api/admin/users${qs({ page, pageSize, keyword })}`);
  return parseResult(res);
}

export async function adminPageFeedback(page, pageSize, keyword, userId) {
  const res = await adminFetch(`/api/admin/feedback${qs({ page, pageSize, keyword, userId })}`);
  return parseResult(res);
}

export async function adminSetUserCredits(userId, totalCredits) {
  const res = await adminFetch(`/api/admin/users/${encodeURIComponent(userId)}/credits`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify({ totalCredits }),
  });
  return parseResult(res);
}

/** 统一任务列表：试穿 + Gemini 异步图 + 模特视频 */
export async function adminPageTasks(page, pageSize, userId) {
  const res = await adminFetch(`/api/admin/tasks${qs({ page, pageSize, userId })}`);
  return parseResult(res);
}

export async function adminTaskDetail(recordSource, id) {
  const res = await adminFetch(
    `/api/admin/tasks/detail${qs({ recordSource, id })}`,
  );
  return parseResult(res);
}

/** category: business | other（可选） */
export async function adminPageUserUploads(page, pageSize, userId, category) {
  const res = await adminFetch(`/api/admin/user-uploads${qs({ page, pageSize, userId, category })}`);
  return parseResult(res);
}

export async function adminPageOrders(page, pageSize, userId) {
  const res = await adminFetch(`/api/admin/orders${qs({ page, pageSize, userId })}`);
  return parseResult(res);
}

export async function adminPageCreditRecords(page, pageSize, userId) {
  const res = await adminFetch(`/api/admin/credit-records${qs({ page, pageSize, userId })}`);
  return parseResult(res);
}

export async function adminPagePackages(page, pageSize) {
  const res = await adminFetch(`/api/admin/recharge-packages${qs({ page, pageSize })}`);
  return parseResult(res);
}

export async function adminGetPackage(id) {
  const res = await adminFetch(`/api/admin/recharge-packages/${id}`);
  return parseResult(res);
}

export async function adminCreatePackage(body) {
  const res = await adminFetch('/api/admin/recharge-packages', {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminUpdatePackage(id, body) {
  const res = await adminFetch(`/api/admin/recharge-packages/${id}`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminDeletePackage(id) {
  const res = await adminFetch(`/api/admin/recharge-packages/${id}`, { method: 'DELETE' });
  return parseResult(res);
}

export async function adminPagePublicModels(page, pageSize, keyword) {
  const res = await adminFetch(`/api/admin/public-models${qs({ page, pageSize, keyword })}`);
  return parseResult(res);
}

export async function adminGetPublicModel(id) {
  const res = await adminFetch(`/api/admin/public-models/${id}`);
  return parseResult(res);
}

export async function adminCreatePublicModel(body) {
  const res = await adminFetch('/api/admin/public-models', {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminUpdatePublicModel(id, body) {
  const res = await adminFetch(`/api/admin/public-models/${id}`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminDeletePublicModel(id) {
  const res = await adminFetch(`/api/admin/public-models/${id}`, { method: 'DELETE' });
  return parseResult(res);
}

async function presignAndPutImageWithThumb(presignPath, file) {
  // 生成 200px 宽等比 JPEG 缩略图
  const makeThumb = () =>
    new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => {
        const w = img.naturalWidth || img.width;
        const h = img.naturalHeight || img.height;
        if (!w || !h) {
          reject(new Error('图片解析失败'));
          return;
        }
        const tw = Math.min(200, w);
        const th = Math.round((h * tw) / w);
        const canvas = document.createElement('canvas');
        canvas.width = tw;
        canvas.height = th;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          reject(new Error('Canvas 不可用'));
          return;
        }
        ctx.fillStyle = '#fff';
        ctx.fillRect(0, 0, tw, th);
        ctx.drawImage(img, 0, 0, tw, th);
        canvas.toBlob(
          (blob) => {
            if (!blob) {
              reject(new Error('缩略图生成失败'));
              return;
            }
            resolve(blob);
          },
          'image/jpeg',
          0.82,
        );
      };
      img.onerror = () => reject(new Error('图片加载失败'));
      img.src = URL.createObjectURL(file);
    });

  const thumbBlob = await makeThumb();
  const presignRes = await adminFetch(presignPath, {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify({
      fileName: file.name || 'upload.jpg',
      contentType: file.type || 'image/jpeg',
      fileSize: file.size || 0,
    }),
  });
  const presign = await parseResult(presignRes);
  if (!presign?.uploadUrl || !presign?.imageUrl || !presign?.uploadThumbUrl || !presign?.thumbUrl) {
    throw new Error('获取上传地址失败');
  }

  const putFull = await fetch(presign.uploadUrl, {
    method: 'PUT',
    headers: file.type ? { 'Content-Type': file.type } : {},
    body: file,
  });
  if (!putFull.ok) throw new Error('原图上传失败');

  const putThumb = await fetch(presign.uploadThumbUrl, {
    method: 'PUT',
    headers: { 'Content-Type': 'image/jpeg' },
    body: thumbBlob,
  });
  if (!putThumb.ok) throw new Error('缩略图上传失败');

  return { imageUrl: presign.imageUrl, thumbUrl: presign.thumbUrl };
}

/** 上传公共模特原图到 COS，返回 { imageUrl, thumbUrl }（缩略图由服务端生成） */
export async function adminUploadPublicModelImage(file) {
  return presignAndPutImageWithThumb('/api/admin/public-models/presign-image', file);
}

export async function adminPagePublicGarments(page, pageSize, keyword, sceneCategory, gender) {
  const res = await adminFetch(
    `/api/admin/public-garments${qs({ page, pageSize, keyword, sceneCategory, gender })}`,
  );
  return parseResult(res);
}

export async function adminGetPublicGarment(id) {
  const res = await adminFetch(`/api/admin/public-garments/${id}`);
  return parseResult(res);
}

export async function adminCreatePublicGarment(body) {
  const res = await adminFetch('/api/admin/public-garments', {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminUpdatePublicGarment(id, body) {
  const res = await adminFetch(`/api/admin/public-garments/${id}`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminDeletePublicGarment(id) {
  const res = await adminFetch(`/api/admin/public-garments/${id}`, { method: 'DELETE' });
  return parseResult(res);
}

export async function adminUploadPublicGarmentImage(file) {
  return presignAndPutImageWithThumb('/api/admin/public-garments/presign-image', file);
}

export async function adminChangePassword(body) {
  const res = await adminFetch('/api/admin/account/change-password', {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

/** ---------- 品牌方 ---------- */
export async function adminPageBrands(page, pageSize, keyword, auditStatus, status) {
  const res = await adminFetch(`/api/admin/brands${qs({ page, pageSize, keyword, auditStatus, status })}`);
  return parseResult(res);
}

export async function adminGetBrand(id) {
  const res = await adminFetch(`/api/admin/brands/${id}`);
  return parseResult(res);
}

/** 品牌进店链接 + PC/H5 二维码（PNG Base64） */
export async function adminGetBrandEntry(id) {
  const res = await adminFetch(`/api/admin/brands/${id}/brand-entry`);
  return parseResult(res);
}

export async function adminCreateBrand(body) {
  const res = await adminFetch('/api/admin/brands', {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminUpdateBrand(id, body) {
  const res = await adminFetch(`/api/admin/brands/${id}`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminAuditBrand(id, body) {
  const res = await adminFetch(`/api/admin/brands/${id}/audit`, {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminDeleteBrand(id) {
  const res = await adminFetch(`/api/admin/brands/${id}`, { method: 'DELETE' });
  return parseResult(res);
}

function adminUploadBrandMultipart(path, file) {
  const fd = new FormData();
  fd.append('file', file);
  return adminFetch(path, { method: 'POST', body: fd });
}

/** 品牌 Logo  multipart → { imageUrl } */
export async function adminUploadBrandLogo(file) {
  const res = await adminUploadBrandMultipart('/api/admin/brands/upload-logo', file);
  return parseResult(res);
}

/** 营业执照 */
export async function adminUploadBrandBusinessLicense(file) {
  const res = await adminUploadBrandMultipart('/api/admin/brands/upload-business-license', file);
  return parseResult(res);
}

/** 法人身份证正面 */
export async function adminUploadBrandLegalIdFront(file) {
  const res = await adminUploadBrandMultipart('/api/admin/brands/upload-legal-id-front', file);
  return parseResult(res);
}

/** 法人身份证反面 */
export async function adminUploadBrandLegalIdBack(file) {
  const res = await adminUploadBrandMultipart('/api/admin/brands/upload-legal-id-back', file);
  return parseResult(res);
}

export async function adminPageBrandGarments(brandId, page, pageSize, keyword, sceneCategory, gender) {
  const res = await adminFetch(
    `/api/admin/brands/${brandId}/garments${qs({ page, pageSize, keyword, sceneCategory, gender })}`,
  );
  return parseResult(res);
}

export async function adminCreateBrandGarment(brandId, body) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/garments`, {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminUpdateBrandGarment(brandId, garmentId, body) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/garments/${garmentId}`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminDeleteBrandGarment(brandId, garmentId) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/garments/${garmentId}`, { method: 'DELETE' });
  return parseResult(res);
}

export async function adminUploadBrandGarmentImage(brandId, file) {
  const fd = new FormData();
  fd.append('file', file);
  const res = await adminFetch(`/api/admin/brands/${brandId}/garments/upload-image`, {
    method: 'POST',
    body: fd,
  });
  return parseResult(res);
}

export async function adminPageBrandUsers(brandId, page, pageSize, keyword) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/users${qs({ page, pageSize, keyword })}`);
  return parseResult(res);
}

export async function adminCreateBrandUser(brandId, body) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/users`, {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminUpdateBrandUser(brandId, userId, body) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/users/${userId}`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminResetBrandUserPassword(brandId, userId, body) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/users/${userId}/password`, {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminDeleteBrandUser(brandId, userId) {
  const res = await adminFetch(`/api/admin/brands/${brandId}/users/${userId}`, { method: 'DELETE' });
  return parseResult(res);
}

export async function adminGeminiKeys() {
  const res = await adminFetch('/api/admin/gemini-keys');
  return parseResult(res);
}

export async function adminGeminiStrategy(strategy) {
  const res = await adminFetch('/api/admin/gemini-keys/strategy', {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify({ strategy }),
  });
  return parseResult(res);
}

export async function adminCreateGeminiKey(body) {
  const res = await adminFetch('/api/admin/gemini-keys', {
    method: 'POST',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminUpdateGeminiKey(id, body) {
  const res = await adminFetch(`/api/admin/gemini-keys/${id}`, {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminDeleteGeminiKey(id) {
  const res = await adminFetch(`/api/admin/gemini-keys/${id}`, { method: 'DELETE' });
  return parseResult(res);
}

export async function adminProbeGeminiKey(id) {
  const res = await adminFetch(`/api/admin/gemini-keys/${id}/probe`, { method: 'POST' });
  return parseResult(res);
}

export async function adminClearGeminiCooldown(id) {
  const res = await adminFetch(`/api/admin/gemini-keys/${id}/clear-cooldown`, { method: 'POST' });
  return parseResult(res);
}

export async function adminRegisterGift() {
  const res = await adminFetch('/api/admin/register-gift');
  return parseResult(res);
}

export async function adminUpdateRegisterGift(body) {
  const res = await adminFetch('/api/admin/register-gift', {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}

export async function adminRegisterAbuse() {
  const res = await adminFetch('/api/admin/register-abuse');
  return parseResult(res);
}

export async function adminUpdateRegisterAbuse(body) {
  const res = await adminFetch('/api/admin/register-abuse', {
    method: 'PUT',
    headers: JSON_HDR,
    body: JSON.stringify(body),
  });
  return parseResult(res);
}
