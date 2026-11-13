function isHttpUrl(s) {
  return typeof s === 'string' && /^https?:\/\/.+/i.test(s.trim());
}

/** payload 中非图片的业务字段，深度扫描时跳过 */
const PAYLOAD_SKIP_KEYS = new Set([
  'prompt',
  'secondPrompt',
  'instruction',
  'modelId',
  'aspectRatio',
  'imageSize',
  'taskType',
  'stylePreset',
  'makeupPreset',
  'outfitMode',
  'outputCount',
  'parentTaskId',
  'regenerateSlotIndex',
  'regenerateSlotQueue',
  'artDirection',
  'ecomScene',
  'modelGender',
  'ageGroup',
  'bodyType',
  'modelStyleNote',
  'faceMode',
  'facePreset',
  'framingMode',
  'sceneVariantCount',
  'backgroundColor',
  'garmentScope',
  'removeHanger',
  'removePriceTag',
  'removeBadge',
  'shootHint',
  'cropPreference',
  'outputLanguage',
]);

const PAYLOAD_FIELD_LABELS = {
  sourceCosUrls: '输入图',
  sourceCosUrl: '源图',
  source_cos_urls: '输入图',
  source_cos_url: '源图',
  imageUrls: '输入图',
  imageUrl: '输入图',
  imageCosUrls: '参考图',
  image_cos_urls: '参考图',
  modelCosUrl: '模特图',
  model_cos_url: '模特图',
  modelUrl: '模特图',
  garmentCosUrl: '服装图',
  garment_cos_url: '服装图',
  garmentUrl: '服装图',
  maskCosUrl: '蒙版',
  mask_cos_url: '蒙版',
  referenceCosUrl: '参考图',
  reference_cos_url: '参考图',
  faceReferenceCosUrl: '参考脸图',
  face_reference_cos_url: '参考脸图',
  faceReferenceUrl: '参考脸图',
  flatImageUrl: '平铺图',
  garmentImageUrl: '服装图',
};

/** 从 JSON 数组字符串或逗号分隔串中取首张图 URL */
export function firstImageFromMulti(s) {
  if (!s || typeof s !== 'string') return '';
  const t = s.trim();
  if (!t) return '';
  try {
    const a = JSON.parse(t);
    if (Array.isArray(a) && a.length && typeof a[0] === 'string') {
      return a[0].trim();
    }
  } catch {
    /* ignore */
  }
  if (t.includes(',')) {
    return t.split(',')[0].trim();
  }
  return t;
}

/** @returns {{ url: string, label: string }[]} */
export function resultImagesFromGeminiResultJson(resultJson) {
  if (!resultJson || typeof resultJson !== 'string') return [];
  const t = resultJson.trim();
  if (!t) return [];
  try {
    const obj = JSON.parse(t);
    if (obj && Array.isArray(obj.urls)) {
      const labels = Array.isArray(obj.imageLabels) ? obj.imageLabels : [];
      const out = [];
      obj.urls.forEach((u, i) => {
        const url = typeof u === 'string' ? u.trim() : '';
        if (!isHttpUrl(url)) return;
        out.push({ url, label: labels[i] || '' });
      });
      if (out.length) return out;
    }
    if (typeof obj === 'string' && isHttpUrl(obj)) {
      return [{ url: obj.trim(), label: '' }];
    }
  } catch {
    /* ignore */
  }
  const single = firstImageFromMulti(t);
  return isHttpUrl(single) ? [{ url: single, label: '' }] : [];
}

/** 从 gemini_image_tasks.result_json 取首张 COS 图 URL */
export function firstCosUrlFromGeminiResultJson(resultJson) {
  const imgs = resultImagesFromGeminiResultJson(resultJson);
  return imgs[0]?.url || '';
}

function labelForPayloadKey(key) {
  if (PAYLOAD_FIELD_LABELS[key]) return PAYLOAD_FIELD_LABELS[key];
  const lower = key.toLowerCase();
  if (lower.includes('mask')) return '蒙版';
  if (lower.includes('face') && lower.includes('ref')) return '参考脸图';
  if (lower.includes('reference') || lower.includes('ref')) return '参考图';
  if (lower.includes('garment') || lower.includes('cloth')) return '服装图';
  if (lower.includes('model')) return '模特图';
  if (lower.includes('source') || lower.includes('input')) return '输入图';
  if (lower.includes('image') || lower.includes('url')) return '图片';
  return '图片';
}

function isImageUrlFieldKey(key) {
  const k = String(key || '').toLowerCase();
  if (PAYLOAD_SKIP_KEYS.has(key)) return false;
  return (
    k.endsWith('url')
    || k.endsWith('urls')
    || k.includes('cosurl')
    || k.includes('_url')
    || k.includes('image')
  );
}

function pushUploadUrl(seen, out, url, label) {
  const u = typeof url === 'string' ? url.trim() : '';
  if (!isHttpUrl(u) || seen.has(u)) return;
  seen.add(u);
  out.push({ url: u, label: label || '图片' });
}

function collectFromPayloadValue(seen, out, key, val) {
  if (val == null) return;
  const baseLabel = labelForPayloadKey(key);

  if (typeof val === 'string') {
    if (isImageUrlFieldKey(key)) pushUploadUrl(seen, out, val, baseLabel);
    return;
  }

  if (Array.isArray(val)) {
    if (!isImageUrlFieldKey(key)) return;
    val.forEach((item, i) => {
      if (typeof item === 'string') {
        pushUploadUrl(seen, out, item, `${baseLabel} ${i + 1}`);
      } else if (item && typeof item === 'object') {
        const nestedUrl = item.url || item.fileUrl || item.imageUrl || item.cosUrl;
        if (typeof nestedUrl === 'string') {
          pushUploadUrl(seen, out, nestedUrl, item.label || `${baseLabel} ${i + 1}`);
        }
      }
    });
  }
}

/** 从 gemini_image_tasks.payload_json 解析用户上传/输入图 URL */
export function uploadUrlsFromGeminiPayloadJson(payloadJson) {
  if (!payloadJson || typeof payloadJson !== 'string') return [];
  const t = payloadJson.trim();
  if (!t) return [];
  let obj;
  try {
    obj = JSON.parse(t);
  } catch {
    return [];
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return [];

  const seen = new Set();
  const out = [];

  for (const [key, val] of Object.entries(obj)) {
    collectFromPayloadValue(seen, out, key, val);
  }

  return out;
}

/** 从 payload_json 读取 parentTaskId（单张重生成子任务） */
export function parentTaskIdFromGeminiPayloadJson(payloadJson) {
  if (!payloadJson || typeof payloadJson !== 'string') return null;
  try {
    const obj = JSON.parse(payloadJson.trim());
    const id = obj?.parentTaskId ?? obj?.parent_task_id;
    if (id == null || id === '') return null;
    const n = Number(id);
    return Number.isFinite(n) && n > 0 ? n : null;
  } catch {
    return null;
  }
}

/** 合并上传图列表（按 URL 去重，保留先出现的标签） */
export function mergeUploadImageLists(...lists) {
  const seen = new Set();
  const out = [];
  for (const list of lists) {
    if (!Array.isArray(list)) continue;
    for (const item of list) {
      if (!item?.url || seen.has(item.url)) continue;
      seen.add(item.url);
      out.push(item);
    }
  }
  return out;
}

/** 试穿任务：模特图 + 服装图（含 gemini_payload_json 内可能存在的 URL） */
export function uploadUrlsFromTryonTask(tryonVo) {
  if (!tryonVo) return [];
  const fromPayload = tryonVo.geminiPayloadJson
    ? uploadUrlsFromGeminiPayloadJson(tryonVo.geminiPayloadJson)
    : [];
  return mergeUploadImageLists(
    tryonVo.modelUrl ? [{ url: tryonVo.modelUrl.trim(), label: '模特图' }] : [],
    tryonVo.garmentUrl ? [{ url: tryonVo.garmentUrl.trim(), label: '服装图' }] : [],
    fromPayload,
  ).filter((item) => isHttpUrl(item.url));
}
