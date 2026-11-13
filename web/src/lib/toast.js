import { reactive } from 'vue';

export const toastState = reactive({ list: [] });
let seq = 0;

/** 全局轻提示：type = info | success | error */
export function toast(message, type = 'info', ms = 2800) {
  if (!message) return;
  const id = ++seq;
  toastState.list.push({ id, message: String(message), type });
  if (toastState.list.length > 3) toastState.list.shift();
  setTimeout(() => {
    const i = toastState.list.findIndex((x) => x.id === id);
    if (i >= 0) toastState.list.splice(i, 1);
  }, ms);
}
