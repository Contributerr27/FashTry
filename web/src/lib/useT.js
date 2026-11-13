import { ref } from 'vue';
import { i18n } from './i18n.js';

const locale = ref(i18n.getLocale());
let bound = false;

function bind() {
  if (bound || typeof window === 'undefined') return;
  bound = true;
  window.addEventListener('localechange', () => {
    locale.value = i18n.getLocale();
  });
}

/** 响应式翻译：模板里直接用 t('key')，切换语言后自动重渲染 */
export function useT() {
  bind();
  const t = (key, params) => {
    void locale.value;
    return i18n.t(key, params);
  };
  return { t, locale };
}
