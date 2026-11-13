export function orderStatusText(s) {
  const m = { 0: '待支付', 1: '已支付', 2: '超时/关闭' };
  return m[s] ?? String(s);
}

export function taskStatusText(s) {
  const m = { 0: '排队', 1: '生成中', 2: '完成', 3: '失败' };
  return m[s] ?? String(s);
}

export function creditRecordStatus(s) {
  const m = { 0: '有效', 1: '已过期' };
  return m[s] ?? String(s);
}

export function fmtDt(v) {
  if (v == null || v === '') return '—';
  return String(v).replace('T', ' ').slice(0, 19);
}

/** 充值订单支付渠道 */
export function payChannelText(ch) {
  if (ch == null || ch === '') return '—';
  const key = String(ch).trim().toLowerCase();
  const m = {
    wxpay: '微信支付',
    alipay: '支付宝',
    paypal: 'PayPal',
    usdt_tron: 'USDT（TRON）',
    usdt_bsc: 'USDT（BSC）',
  };
  return m[key] ?? ch;
}

/**
 * 试穿任务服装类别（库内 auto 会落成 tops）
 * tops / bottoms / one-pieces
 */
export function tryonCategoryText(cat) {
  if (cat == null || cat === '') return '—';
  const key = String(cat).trim().toLowerCase();
  const m = {
    tops: '上装（含自动识别）',
    bottoms: '下装',
    'one-pieces': '连体/连衣裙',
    auto: '自动',
  };
  return m[key] ?? cat;
}

/** 试穿引擎 provider */
export function tryonProviderText(provider) {
  if (provider == null || provider === '' || provider === '—') return '—';
  const key = String(provider).trim().toUpperCase();
  const m = {
    GEMINI: 'Gemini 试穿',
    FASHN: 'FASHN 试穿',
  };
  return m[key] ?? provider;
}

/** 统一任务列表：记录来源 */
export function adminTaskRecordSourceText(source) {
  if (source == null || source === '') return '—';
  const key = String(source).trim().toLowerCase();
  const m = {
    tryon: '虚拟试穿',
    gemini_image: '图像生成',
    model_video: '模特视频',
  };
  return m[key] ?? source;
}

/** Gemini 异步图 task_type */
export function geminiTaskTypeText(type) {
  if (type == null || type === '' || type === '—') return '—';
  const key = String(type).trim().toUpperCase();
  const m = {
    TRY_ON: '虚拟试穿（历史）',
    TEXT_TO_IMAGE: '文生图',
    INTERLEAVED: '图文交织',
    EDIT: '局部改款',
    CLOTHING_PACKSHOT: '模特出图',
    CLOTHING_PRODUCT_SUITE: '服装套图',
    PRODUCT_RETOUCH: '服装精修',
    PORTRAIT_STUDIO: 'AI 写真',
  };
  return m[key] ?? type;
}

/** 统一任务列表：任务类型列 */
export function adminUnifiedTaskTypeText(row) {
  if (!row) return '—';
  const src = String(row.recordSource || '').trim().toLowerCase();
  if (src === 'gemini_image') return geminiTaskTypeText(row.taskLabel);
  if (src === 'model_video') return '图生视频（Veo）';
  if (src === 'tryon') {
    const p = tryonProviderText(row.taskLabel);
    return p === '—' ? '虚拟试穿' : `虚拟试穿 · ${p}`;
  }
  return row.taskLabel || '—';
}

/** 统一任务列表：明细列 */
export function adminTaskDetailTagText(row) {
  if (!row) return '—';
  const src = String(row.recordSource || '').trim().toLowerCase();
  const tag = row.detailTag;
  if (tag == null || tag === '') return '—';
  if (src === 'tryon') return tryonCategoryText(tag);
  if (src === 'model_video') return formatVideoDetailTag(tag);
  return tag;
}

/** Gemini 成片 imageLabels 中文标签（后台预览） */
export function geminiResultImageLabel(label) {
  if (!label) return '';
  const map = {
    MODEL_WEAR: '模特上身',
    ECOM_WHITE: '白底主图',
    ECOM_WHITE_MAIN: '白底主图',
    ECOM_PREMIUM_MAIN: '高级主图',
    DETAIL_LONG_STRIP: '详情长图',
  };
  if (map[label]) return map[label];
  if (label.startsWith('SCENE_')) return `场景 ${label.replace('SCENE_', '')}`;
  if (label.startsWith('DETAIL_')) return `细节 ${label.replace('DETAIL_', '')}`;
  if (label.startsWith('SELL_')) return `卖点 ${label.replace('SELL_', '')}`;
  return label;
}

/** 模特视频分辨率/画幅明细 */
export function formatVideoDetailTag(tag) {
  const s = String(tag).trim();
  if (!s) return '—';
  return s
    .replace(/\b720p\b/i, '720p')
    .replace(/\b1080p\b/i, '1080p')
    .replace(/\b16:9\b/, '横屏 16:9')
    .replace(/\b9:16\b/, '竖屏 9:16');
}

export function currencyText(c) {
  if (c == null || c === '') return '';
  const u = String(c).trim().toUpperCase();
  if (u === 'CNY') return '元（人民币）';
  if (u === 'USD') return '美元';
  return c;
}
