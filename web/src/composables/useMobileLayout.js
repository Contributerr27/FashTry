import { ref, onMounted, onUnmounted } from 'vue';

export function useMobileLayout(breakpointPx = 768) {
  const isMobile = ref(false);
  let mq = null;

  function sync() {
    if (mq) isMobile.value = mq.matches;
  }

  onMounted(() => {
    if (typeof window === 'undefined') return;
    mq = window.matchMedia(`(max-width: ${breakpointPx}px)`);
    sync();
    mq.addEventListener('change', sync);
  });

  onUnmounted(() => {
    if (mq) mq.removeEventListener('change', sync);
  });

  return { isMobile };
}
