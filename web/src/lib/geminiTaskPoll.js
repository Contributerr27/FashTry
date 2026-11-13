/**
 * 轮询 Gemini 异步任务直至完成或失败。
 * @param {(id: number|string) => Promise<{ status: string, urls?: string[], imageLabels?: string[], errorMsg?: string, elapsedMsSinceProcessingStart?: number }>} getTask
 */
export async function pollGeminiTask(getTask, taskId, onTick, shouldAbort) {
  let sec = 0;
  while (true) {
    if (shouldAbort?.()) return null;
    const task = await getTask(taskId);
    if (onTick) onTick(task, sec);
    if (task.status === 'COMPLETED' || task.status === 'FAILED') return task;
    await new Promise((r) => setTimeout(r, 2000));
    sec += 2;
  }
}

export function estimateTaskProgress(task, estMs = 120000) {
  if (task?.elapsedMsSinceProcessingStart != null) {
    return Math.min(99, Math.round((task.elapsedMsSinceProcessingStart / estMs) * 100));
  }
  return null;
}

/** 服装电商套图成片标签 */
export function suiteImageLabel(label, i18n) {
  if (!label) return '';
  if (label === 'ECOM_WHITE_MAIN') return i18n.t('suiteLblWhiteMain');
  if (label === 'ECOM_PREMIUM_MAIN') return i18n.t('suiteLblPremiumMain');
  if (label === 'DETAIL_LONG_STRIP') return i18n.t('suiteLblLongStrip');
  if (label.startsWith('SCENE_')) return i18n.t('suiteLblScene') + label.replace('SCENE_', '');
  if (label.startsWith('DETAIL_')) return i18n.t('suiteLblDetail') + label.replace('DETAIL_', '');
  if (label.startsWith('SELL_')) return i18n.t('suiteLblSell') + label.replace('SELL_', '');
  return label;
}
/** 模特出图成片标签 */
export function packshotImageLabel(label, i18n) {
  if (!label) return '';
  if (label === 'MODEL_WEAR') return i18n.t('packshotLblModel');
  if (label === 'ECOM_WHITE') return i18n.t('packshotLblWhite');
  if (label.startsWith('SCENE_')) {
    return i18n.t('packshotLblScene') + label.replace('SCENE_', '');
  }
  return label;
}
