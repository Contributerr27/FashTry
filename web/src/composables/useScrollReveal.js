import { onMounted, onUnmounted, ref } from 'vue';

/** 滚动进入视口时给 `[data-reveal]` 加上 `is-in`。尊重减少动效。 */
export function useScrollReveal() {
  const root = ref(null);
  let io = null;

  onMounted(() => {
    const el = root.value;
    if (!el) return;
    const nodes = el.querySelectorAll('[data-reveal]');
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce) {
      nodes.forEach((n) => n.classList.add('is-in'));
      return;
    }
    io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.classList.add('is-in');
          io?.unobserve(entry.target);
        });
      },
      { threshold: 0.16, rootMargin: '0px 0px -10% 0px' }
    );
    nodes.forEach((n) => io.observe(n));
  });

  onUnmounted(() => {
    io?.disconnect();
    io = null;
  });

  return root;
}
