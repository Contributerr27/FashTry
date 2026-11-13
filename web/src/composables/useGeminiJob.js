import { onUnmounted, ref } from 'vue';
import { pollGeminiTask, estimateTaskProgress } from '../lib/geminiTaskPoll.js';
import { i18n } from '../lib/i18n.js';

/**
 * Gemini 异步任务：提交 → 轮询 → 进度文案
 * @param {{ getTask: (id) => Promise<any>, estMs?: number, busyKey?: string }} opts
 */
export function useGeminiJob(opts) {
  const running = ref(false);
  const progressText = ref('');
  const progressPct = ref(null);
  let session = 0;
  let disposed = false;

  function tickText(task, sec, busyKey) {
    const pct = estimateTaskProgress(task, opts.estMs || 180000);
    progressPct.value = task.status === 'PENDING' ? null : pct;
    if (task.status === 'PENDING') return i18n.t('localEditQueueing', { sec });
    if (pct != null) return i18n.t('localEditProgress', { pct });
    return i18n.t(busyKey || opts.busyKey || 'localEditPolling');
  }

  /**
   * @param {() => Promise<{ taskId }>} submit
   * @param {{ onTick?: (task) => void, busyKey?: string, getTask?: Function }} [o]
   * @returns {Promise<{ taskId, final } | null>} 被新任务打断或组件卸载时返回 null
   */
  async function run(submit, o = {}) {
    const my = ++session;
    running.value = true;
    progressPct.value = null;
    progressText.value = i18n.t('localEditSubmitting');
    try {
      const { taskId } = await submit();
      const final = await pollGeminiTask(
        o.getTask || opts.getTask,
        taskId,
        (task, sec) => {
          if (my !== session) return;
          progressText.value = tickText(task, sec, o.busyKey);
          o.onTick?.(task, taskId);
        },
        () => disposed || my !== session
      );
      if (!final || my !== session) return null;
      return { taskId, final };
    } finally {
      if (my === session) {
        running.value = false;
        progressText.value = '';
        progressPct.value = null;
      }
    }
  }

  function cancel() {
    session++;
    running.value = false;
    progressText.value = '';
  }

  onUnmounted(() => {
    disposed = true;
    session++;
  });

  return { running, progressText, progressPct, run, cancel };
}
