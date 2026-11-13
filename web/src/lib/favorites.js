import { reactive } from 'vue';
import { api } from './api.js';
import { auth } from './auth.js';

/** url -> true（已收藏）/ false（已确认未收藏） */
const state = reactive({ map: {}, busy: {} });

let pending = new Set();
let flushTimer = null;

function flush() {
  flushTimer = null;
  const urls = [...pending];
  pending = new Set();
  if (!urls.length || !auth.isLoggedIn()) return;
  for (let i = 0; i < urls.length; i += 100) {
    const chunk = urls.slice(i, i + 100);
    api
      .checkFavorites(chunk)
      .then((hit) => {
        const set = new Set(Array.isArray(hit) ? hit : []);
        chunk.forEach((u) => {
          state.map[u] = set.has(u);
        });
      })
      .catch(() => {});
  }
}

/** 批量查询收藏状态（自动合并 60ms 内的请求） */
export function ensureFavChecked(urls) {
  if (!auth.isLoggedIn()) return;
  (urls || []).forEach((u) => {
    if (u && !(u in state.map)) pending.add(u);
  });
  if (pending.size && !flushTimer) flushTimer = setTimeout(flush, 60);
}

export function isFav(url) {
  return !!(url && state.map[url]);
}

export function isFavBusy(url) {
  return !!(url && state.busy[url]);
}

/** 在本地标记为已收藏（收藏列表加载时使用） */
export function markFav(urls, value = true) {
  (urls || []).forEach((u) => {
    if (u) state.map[u] = value;
  });
}

/**
 * 切换收藏
 * @param {{ url: string, thumbUrl?: string, sourceType?: string, sourceTaskId?: string|number, title?: string }} item
 * @returns {Promise<boolean>} 切换后的状态
 */
export async function toggleFav(item) {
  const url = item?.url;
  if (!url) return false;
  if (!auth.isLoggedIn()) {
    window.location.href = '/login?return=' + encodeURIComponent(location.pathname + location.search);
    return false;
  }
  if (state.busy[url]) return isFav(url);
  const next = !isFav(url);
  state.busy[url] = true;
  state.map[url] = next;
  try {
    if (next) {
      await api.addFavorite({
        imageUrl: url,
        thumbUrl: item.thumbUrl || undefined,
        sourceType: item.sourceType || undefined,
        sourceTaskId: item.sourceTaskId != null ? String(item.sourceTaskId) : undefined,
        title: item.title || undefined,
      });
    } else {
      await api.removeFavorite(url);
    }
    window.dispatchEvent(new CustomEvent('fashtry:favorites-changed', { detail: { url, value: next } }));
    return next;
  } catch (e) {
    state.map[url] = !next;
    throw e;
  } finally {
    state.busy[url] = false;
  }
}

export function resetFavorites() {
  state.map = {};
}
