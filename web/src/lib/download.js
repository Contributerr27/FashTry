/**
 * 图片下载与 ZIP 打包
 */

function extFromUrl(url, fallback = 'png') {
  const m = String(url || '')
    .split('?')[0]
    .match(/\.(png|jpe?g|webp|gif)$/i);
  return m ? m[1].toLowerCase().replace('jpeg', 'jpg') : fallback;
}

function extFromType(type) {
  if (!type) return null;
  if (type.includes('png')) return 'png';
  if (type.includes('jpeg') || type.includes('jpg')) return 'jpg';
  if (type.includes('webp')) return 'webp';
  if (type.includes('gif')) return 'gif';
  return null;
}

function triggerBlobDownload(blob, filename) {
  const a = document.createElement('a');
  const href = URL.createObjectURL(blob);
  a.href = href;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(href), 4000);
}

async function fetchBlob(url) {
  const res = await fetch(url, { mode: 'cors', cache: 'no-store' });
  if (!res.ok) throw new Error('HTTP ' + res.status);
  return res.blob();
}

/**
 * 下载单张图；跨域失败时新标签页打开
 * @returns {Promise<'downloaded'|'opened'>}
 */
export async function downloadUrl(url, filename) {
  if (!url) throw new Error('empty url');
  const base = filename || 'fashtry-' + Date.now();
  try {
    const blob = await fetchBlob(url);
    const ext = extFromType(blob.type) || extFromUrl(url);
    const name = /\.[a-z0-9]{3,4}$/i.test(base) ? base : `${base}.${ext}`;
    triggerBlobDownload(blob, name);
    return 'downloaded';
  } catch {
    window.open(url, '_blank', 'noopener');
    return 'opened';
  }
}

/**
 * 打包多张图为 ZIP
 * @param {Array<{ url: string, name?: string }>} items
 * @param {string} zipName 不含扩展名
 * @param {(done: number, total: number) => void} [onProgress]
 * @returns {Promise<{ ok: number, failed: number }>}
 */
export async function downloadZip(items, zipName = 'fashtry', onProgress) {
  const list = (items || []).filter((x) => x && x.url);
  if (!list.length) return { ok: 0, failed: 0 };
  const { default: JSZip } = await import('jszip');
  const zip = new JSZip();
  const used = new Set();
  let done = 0;
  let ok = 0;
  let failed = 0;
  const failedUrls = [];
  const concurrency = 4;
  let cursor = 0;

  async function worker() {
    while (cursor < list.length) {
      const i = cursor++;
      const it = list[i];
      try {
        const blob = await fetchBlob(it.url);
        const ext = extFromType(blob.type) || extFromUrl(it.url);
        let base = (it.name || `image-${String(i + 1).padStart(2, '0')}`).replace(/[\\/:*?"<>|]+/g, '_');
        base = base.replace(/\.[a-z0-9]{3,4}$/i, '');
        let name = `${base}.${ext}`;
        let n = 2;
        while (used.has(name)) name = `${base}-${n++}.${ext}`;
        used.add(name);
        zip.file(name, blob);
        ok++;
      } catch {
        failed++;
        failedUrls.push(it.url);
      }
      done++;
      onProgress?.(done, list.length);
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, list.length) }, worker));
  if (failedUrls.length) {
    zip.file('failed-urls.txt', failedUrls.join('\n'));
  }
  if (ok === 0) return { ok, failed };
  const blob = await zip.generateAsync({ type: 'blob', compression: 'STORE' });
  triggerBlobDownload(blob, `${zipName}.zip`);
  return { ok, failed };
}

export function stamp() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}`;
}
