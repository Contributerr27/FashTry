/**
 * 统一历史 → 扁平作品列表
 */
import { i18n } from './i18n.js';
import { suiteImageLabel, packshotImageLabel } from './geminiTaskPoll.js';

export const WORK_KINDS = [
  { value: 'all', labelKey: 'uiKindAll' },
  { value: 'tryon', labelKey: 'uiKindTryon', path: '/tryon' },
  { value: 'packshot', labelKey: 'uiKindPackshot', path: '/model-shots' },
  { value: 'suite', labelKey: 'uiKindSuite', path: '/clothing-suite' },
  { value: 'retouch', labelKey: 'uiKindRetouch', path: '/product-retouch' },
  { value: 'image_edit', labelKey: 'uiKindEdit', path: '/local-edit' },
  { value: 'portrait', labelKey: 'uiKindPortrait', path: '/portrait-studio' },
];

export function kindLabel(kind) {
  const k = WORK_KINDS.find((x) => x.value === kind);
  return k ? i18n.t(k.labelKey) : kind;
}

export function kindPath(kind) {
  return WORK_KINDS.find((x) => x.value === kind)?.path || '/';
}

function toMs(v) {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  const n = Number(v);
  if (!Number.isNaN(n) && String(v).trim() !== '') return n;
  const d = Date.parse(String(v).replace(' ', 'T'));
  return Number.isNaN(d) ? 0 : d;
}

/** gemini 任务状态 → done|loading|failed */
function geminiStatus(s) {
  if (s === 'COMPLETED') return 'done';
  if (s === 'FAILED') return 'failed';
  return 'loading';
}

/**
 * 一条统一历史 → { kind, taskId, status, createdAt, source, images: [{ url, thumb, label }] }
 */
export function normalizeHistoryItem(row) {
  const kind = row.kind;
  const at = toMs(row.sortAtMs);
  if (kind === 'tryon' && row.tryon) {
    const v = row.tryon;
    const status = v.status === 2 ? 'done' : v.status === 3 ? 'failed' : 'loading';
    const url = v.resultUrl || '';
    return {
      kind,
      taskId: String(v.taskId),
      status,
      createdAt: at,
      source: v.modelUrl || '',
      images: url ? [{ url, thumb: v.thumbUrl || url, label: i18n.t('uiKindTryon') }] : [],
    };
  }
  if (kind === 'image_edit' && row.imageEdit) {
    const v = row.imageEdit;
    return {
      kind,
      taskId: String(v.taskId),
      status: geminiStatus(v.status),
      createdAt: at,
      source: v.sourceImageUrl || '',
      title: v.instructionPreview || '',
      images: (v.outputUrls || []).map((u, i) => ({ url: u, thumb: u, label: `${i18n.t('uiKindEdit')} ${i + 1}` })),
    };
  }
  const payload = row.packshot || row.suite || row.retouch || row.portrait;
  if (payload) {
    const labels = payload.imageLabels || [];
    const lbl = (i) => {
      const raw = labels[i];
      if (kind === 'suite') return suiteImageLabel(raw, i18n) || `${i18n.t('uiKindSuite')} ${i + 1}`;
      if (kind === 'packshot') return packshotImageLabel(raw, i18n) || `${i18n.t('uiKindPackshot')} ${i + 1}`;
      return `${kindLabel(kind)} ${i + 1}`;
    };
    return {
      kind,
      taskId: String(payload.taskId),
      status: geminiStatus(payload.status),
      createdAt: at || toMs(payload.createdAt),
      source: payload.sourceImageUrl || payload.sourceImageUrls?.[0] || '',
      images: (payload.outputUrls || []).map((u, i) => ({ url: u, thumb: u, label: lbl(i) })),
    };
  }
  return null;
}

/** 历史 → 扁平图片列表（只含已完成的图） */
export function flattenWorks(rows) {
  const out = [];
  for (const r of rows || []) {
    const n = normalizeHistoryItem(r);
    if (!n || n.status !== 'done') continue;
    n.images.forEach((img, i) => {
      out.push({
        key: `${n.kind}-${n.taskId}-${i}`,
        kind: n.kind,
        taskId: n.taskId,
        createdAt: n.createdAt,
        url: img.url,
        thumb: img.thumb,
        label: img.label,
        source: n.source,
      });
    });
  }
  return out;
}

export function formatDate(ms) {
  if (!ms) return '';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function formatDay(ms) {
  if (!ms) return '';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
