<script setup>
/**
 * Fashion Studio 细线图标集（24×24，stroke=currentColor）
 * 纯 <path> 渲染，圆/方形统一用路径表达，避免 SVG innerHTML 兼容问题。
 */
import { computed } from 'vue';

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
});

const rect = (x, y, w, h, r = 2) =>
  `M${x + r} ${y}h${w - 2 * r}a${r} ${r} 0 0 1 ${r} ${r}v${h - 2 * r}a${r} ${r} 0 0 1 -${r} ${r}H${x + r}a${r} ${r} 0 0 1 -${r} -${r}V${y + r}a${r} ${r} 0 0 1 ${r} -${r}z`;
const circle = (cx, cy, r) =>
  `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${r * 2} 0a${r} ${r} 0 1 0 -${r * 2} 0z`;

const ICONS = {
  hanger: [
    'M12 6.4a2 2 0 1 1 2-2c0 .85-.53 1.57-1.28 1.86',
    'M12 6.4v2.3L3.5 16a1.4 1.4 0 0 0 1.02 2.4h14.96A1.4 1.4 0 0 0 20.5 16L12 8.7',
  ],
  home: ['M3 10.3 12 3l9 7.3V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z'],
  photo: [
    rect(3, 4, 18, 16, 2.5),
    circle(8.5, 9.5, 1.6),
    'm3.5 17 5-5 3.5 3.5 3-3 5.5 5.5',
  ],
  folder: ['M3 7a2 2 0 0 1 2-2h3.6l2 2H19a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'],
  presets: [
    'M4 7h6M14 7h6M4 17h10M18 17h2',
    circle(12, 7, 2),
    circle(16, 17, 2),
  ],
  users: [
    'M15 20v-1.6a4 4 0 0 0-4-4H6.5a4 4 0 0 0-4 4V20',
    circle(8.75, 7.5, 3.5),
    'M21.5 20v-1.6a4 4 0 0 0-3-3.87M15.5 4.2a3.5 3.5 0 0 1 0 6.6',
  ],
  card: [rect(2.5, 5, 19, 14, 2.5), 'M2.5 9.5h19M6 15h3'],
  settings: [
    circle(12, 12, 3),
    'M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06A1.7 1.7 0 0 0 15 19.4a1.7 1.7 0 0 0-1 1.56V21a2 2 0 1 1-4 0v-.09A1.7 1.7 0 0 0 9 19.4a1.7 1.7 0 0 0-1.88.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-1.56-1H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.6 9a1.7 1.7 0 0 0-.33-1.88l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.7 1.7 0 0 0 9 4.6a1.7 1.7 0 0 0 1-1.56V3a2 2 0 1 1 4 0v.09A1.7 1.7 0 0 0 15 4.6a1.7 1.7 0 0 0 1.88-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.7 1.7 0 0 0 19.4 9v.09A1.7 1.7 0 0 0 21 10.6a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1z',
  ],
  crown: ['M2.8 7.6 7.4 12l4.6-6.6 4.6 6.6 4.6-4.4-1.7 11.2H4.5z', 'M4.8 20.4h14.4'],
  bolt: ['M13.2 2 4.5 13.6h6.6L10 22l8.8-11.8h-6.7z'],
  bell: [
    'M18 8.5a6 6 0 1 0-12 0c0 6.5-2.5 8-2.5 9h17c0-1-2.5-2.5-2.5-9',
    'M10 20.5a2.2 2.2 0 0 0 4 0',
  ],
  chevronDown: ['m5 9 7 7 7-7'],
  chevronRight: ['m9 5 7 7-7 7'],
  chevronLeft: ['m15 5-7 7 7 7'],
  arrowRight: ['M4 12h15m-6-6 6 6-6 6'],
  arrowLeft: ['M20 12H5m6 6-6-6 6-6'],
  menu: ['M3 6h18M3 12h18M3 18h18'],
  x: ['M5 5 19 19M19 5 5 19'],
  cloud: [
    'M7.5 18.5h9a4 4 0 0 0 .5-8 6 6 0 0 0-11.6 1.6A3.4 3.4 0 0 0 7.5 18.5z',
    'M12 12v6m-2.4-3.6L12 16l2.4-1.6',
  ],
  sparkles: [
    'm12 3 1.7 4.8L18.5 9.5l-4.8 1.7L12 16l-1.7-4.8L5.5 9.5l4.8-1.7z',
    'm18.5 16 .8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z',
  ],
  download: ['M12 3v12m-4.5-4.5L12 15l4.5-4.5M4 17v2.5A1.5 1.5 0 0 0 5.5 21h13a1.5 1.5 0 0 0 1.5-1.5V17'],
  folderPlus: [
    'M3 7a2 2 0 0 1 2-2h3.6l2 2H19a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z',
    'M12 11v5m-2.5-2.5h5',
  ],
  refresh: ['M20.5 12a8.5 8.5 0 1 1-2.4-5.9M20.5 4v5h-5'],
  info: [circle(12, 12, 9), 'M12 11v5m0-8h.01'],
  grid: [rect(3, 3, 7, 7, 1.5), rect(14, 3, 7, 7, 1.5), rect(3, 14, 7, 7, 1.5), rect(14, 14, 7, 7, 1.5)],
  loader: ['M12 3v3.5M12 17.5V21M3 12h3.5M17.5 12H21M5.6 5.6l2.5 2.5M15.9 15.9l2.5 2.5M18.4 5.6l-2.5 2.5M8.1 15.9l-2.5 2.5'],
  logout: ['M9 20H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h4M15 16l5-4-5-4M20 12H9'],
  user: [circle(12, 8, 4), 'M4.5 20a7.5 7.5 0 0 1 15 0'],
  search: [circle(10.5, 10.5, 6.5), 'm16 16 4.5 4.5'],
};

const paths = computed(() => ICONS[props.name] || ICONS.grid);
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>
