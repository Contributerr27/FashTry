const WIDGET_SRC = 'https://ask.kerten.cn/widget.js';
const SITE_KEY = 'ewk_6c33e79759e4ec835675543a2a3269bd';
const BASE = 'https://ask.kerten.cn';

let loadPromise = null;

/** 加载企知助手挂件（隐藏默认悬浮钮，由 SupportFab 唤起）。 */
export function loadKertenSupportWidget() {
  if (typeof document === 'undefined') return Promise.resolve();
  if (window.__QizhiWidgetLoaded) return Promise.resolve();
  if (loadPromise) return loadPromise;

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = WIDGET_SRC;
    script.defer = true;
    script.dataset.siteKey = SITE_KEY;
    script.dataset.base = BASE;
    script.onload = () => resolve();
    script.onerror = () => {
      loadPromise = null;
      reject(new Error('在线客服加载失败'));
    };
    document.body.appendChild(script);
  });

  return loadPromise;
}

/** 打开 AI 智能客服问答面板。 */
export async function openKertenSupportWidget() {
  await loadKertenSupportWidget();
  const panel = document.getElementById('qizhi-panel');
  const fab = document.getElementById('qizhi-fab');
  if (panel && !panel.classList.contains('open') && fab) {
    fab.click();
  }
}

/** 关闭 AI 智能客服问答面板。 */
export function closeKertenSupportWidget() {
  const panel = document.getElementById('qizhi-panel');
  const fab = document.getElementById('qizhi-fab');
  if (panel?.classList.contains('open') && fab) {
    fab.click();
  }
}
