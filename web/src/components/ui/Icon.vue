<script setup>
import { computed } from 'vue';

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
  strokeWidth: { type: [Number, String], default: 1.75 },
  filled: { type: Boolean, default: false },
});

/** 24×24 线性图标（lucide 风格） */
const PATHS = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V20a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9.5"/>',
  shirt:
    '<path d="M20.4 6.6 16 3.5a4 4 0 0 1-8 0L3.6 6.6a1 1 0 0 0-.3 1.3l1.6 3a1 1 0 0 0 1.3.4L8 10.5V20a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1v-9.5l1.8.8a1 1 0 0 0 1.3-.4l1.6-3a1 1 0 0 0-.3-1.3Z"/>',
  layers:
    '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 13 9 5 9-5"/><path d="m3 17.5 9 5 9-5" opacity=".6"/>',
  model: '<circle cx="12" cy="7" r="3.5"/><path d="M5.5 21v-1.5A6.5 6.5 0 0 1 12 13a6.5 6.5 0 0 1 6.5 6.5V21"/>',
  grid: '<rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/>',
  retouch:
    '<path d="m14.5 4.5 5 5"/><path d="M3 21l3.5-1 11-11a2.1 2.1 0 0 0-3-3l-11 11L3 21Z"/><path d="M18 14v4M16 16h4M7 3v3M5.5 4.5h3"/>',
  brush:
    '<path d="M9.5 14.5 18.8 5.2a1.8 1.8 0 0 1 2.5 2.5L12 17"/><path d="M7.5 14a3.5 3.5 0 0 0-3.5 3.5c0 1.5-1 2.5-1 2.5h4.5A3.5 3.5 0 0 0 11 16.5a2.5 2.5 0 0 0-3.5-2.5Z"/>',
  portrait:
    '<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="12" cy="10" r="3"/><path d="M6.5 19a6 6 0 0 1 11 0"/>',
  images:
    '<rect x="7" y="3" width="14" height="14" rx="2"/><path d="M3 7v12a2 2 0 0 0 2 2h12"/><circle cx="12" cy="8" r="1.5"/><path d="m21 13-3.5-3.5L10 17"/>',
  wallet:
    '<path d="M19 7V5.5A1.5 1.5 0 0 0 17.5 4h-12A2.5 2.5 0 0 0 3 6.5v11A2.5 2.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15"/><path d="M21 9h-5a3 3 0 0 0 0 6h5a.9.9 0 0 0 .9-.9V9.9A.9.9 0 0 0 21 9Z"/><circle cx="16.5" cy="12" r=".5" fill="currentColor"/>',
  card: '<rect x="2.5" y="5" width="19" height="14" rx="2.5"/><path d="M2.5 10h19M6.5 15h3"/>',
  gift: '<rect x="3" y="8" width="18" height="4" rx="1"/><path d="M12 8v13M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7"/><path d="M7.5 8a2.5 2.5 0 0 1 0-5C10 3 12 8 12 8s2-5 4.5-5a2.5 2.5 0 0 1 0 5"/>',
  code: '<path d="m16 18 6-6-6-6M8 6l-6 6 6 6"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  chevronDown: '<path d="m6 9 6 6 6-6"/>',
  chevronRight: '<path d="m9 6 6 6-6 6"/>',
  chevronLeft: '<path d="m15 6-6 6 6 6"/>',
  sidebar: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M9 4v16"/>',
  download: '<path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5"/><path d="M4 17v1.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V17"/>',
  archive:
    '<rect x="3" y="4" width="18" height="5" rx="1.2"/><path d="M5 9v9.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V9"/><path d="M10 13h4"/>',
  heart:
    '<path d="M19.5 12.6 12 20l-7.5-7.4A4.6 4.6 0 0 1 12 6.3a4.6 4.6 0 0 1 7.5 6.3Z"/>',
  refresh: '<path d="M20 11a8 8 0 0 0-14.3-4.9L4 8"/><path d="M4 4v4h4"/><path d="M4 13a8 8 0 0 0 14.3 4.9L20 16"/><path d="M20 20v-4h-4"/>',
  zoomIn: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5M11 8v6M8 11h6"/>',
  zoomOut: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5M8 11h6"/>',
  maximize: '<path d="M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3"/>',
  compare: '<path d="M12 3v18"/><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m8 10-2 2 2 2M16 10l2 2-2 2"/>',
  upload: '<path d="M12 16V5m0 0L7.5 9.5M12 5l4.5 4.5"/><path d="M4 15v3.5A1.5 1.5 0 0 0 5.5 20h13a1.5 1.5 0 0 0 1.5-1.5V15"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6"/><path d="M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4.5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1V7"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8 12.5 2.8 2.8L16 10"/>',
  alert: '<circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.2a2.6 2.6 0 0 1 5 .9c0 1.7-2.5 2.4-2.5 2.4M12 16.5h.01"/>',
  message: '<path d="M20 15.5a2 2 0 0 1-2 2H8l-4 3.5V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v9.5Z"/>',
  logout: '<path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  undo: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>',
  redo: '<path d="m15 14 5-5-5-5"/><path d="M20 9H9.5a5.5 5.5 0 0 0 0 11H13"/>',
  eraser:
    '<path d="m7 21-4.3-4.3a1 1 0 0 1 0-1.4L13.3 4.7a1 1 0 0 1 1.4 0l5.6 5.6a1 1 0 0 1 0 1.4L11 21"/><path d="M21 21H7M5.5 13.5l6 6"/>',
  lasso:
    '<path d="M7 18.5C4.6 17.3 3 15.3 3 13c0-4.4 4-8 9-8s9 3.6 9 8-4 8-9 8c-1 0-2-.1-2.9-.4"/><circle cx="8" cy="19" r="2"/><path d="M8 21v1.5"/>',
  wand: '<path d="m3 21 12-12M14 4l1-2 1 2 2 1-2 1-1 2-1-2-2-1 2-1ZM19.5 10.5l.5-1 .5 1 1 .5-1 .5-.5 1-.5-1-1-.5 1-.5Z"/><path d="m13.5 7.5 3 3"/>',
  square: '<rect x="4" y="4" width="16" height="16" rx="1.5" stroke-dasharray="3 2.5"/>',
  hand: '<path d="M18 11V6.5a1.5 1.5 0 0 0-3 0V11M15 10.5V4.5a1.5 1.5 0 0 0-3 0v6M12 10.5v-5a1.5 1.5 0 0 0-3 0V14"/><path d="M18 8.5a1.5 1.5 0 0 1 3 0V14a7 7 0 0 1-7 7h-1.5a7 7 0 0 1-5.4-2.6L4 15.6a1.6 1.6 0 0 1 2.5-2L9 16"/>',
  copy: '<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h3"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  sparkles:
    '<path d="M12 3.5 13.8 9l5.7 1.8-5.7 1.8L12 18l-1.8-5.4L4.5 10.8 10.2 9 12 3.5Z"/><path d="M19 3v4M17 5h4M5 17v3M3.5 18.5h3"/>',
  arrowRight: '<path d="M5 12h14m0 0-5.5-5.5M19 12l-5.5 5.5"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3.5 6.5 8.5 6.5 8.5-6.5"/>',
  building:
    '<rect x="4" y="3" width="16" height="18" rx="1.5"/><path d="M9 7h1.5M13.5 7H15M9 11h1.5M13.5 11H15M9 15h1.5M13.5 15H15M10 21v-3h4v3"/>',
  file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z"/><path d="M14 3v5h5M9 13h6M9 17h4"/>',
  external: '<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10"/>',
  hanger:
    '<path d="M12 7.5a2.2 2.2 0 1 0-2.2-2.2"/><path d="M12 7.5v1.6L3.4 15.4A1.5 1.5 0 0 0 4.3 18h15.4a1.5 1.5 0 0 0 .9-2.6L12 9.1"/>',
  filter: '<path d="M4 5h16l-6 7.5V19l-4 1.5v-8L4 5Z"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/>',
  palette:
    '<path d="M12 21a9 9 0 1 1 9-9c0 2.5-2 3.5-3.5 3.5H15a2 2 0 0 0-1.4 3.4A1.3 1.3 0 0 1 12 21Z"/><circle cx="7.5" cy="11" r="1"/><circle cx="10.5" cy="7" r="1"/><circle cx="15.5" cy="7.5" r="1"/>',
  bolt: '<path d="M13 2.5 4.5 13.5H12L11 21.5l8.5-11H12l1-8Z"/>',
  command: '<path d="M9 6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6Z"/>',
  lock: '<rect x="4.5" y="10.5" width="15" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3M12 14.5v2"/>',
  shield: '<path d="M12 3 4.5 6v5.5c0 4.6 3.2 8.2 7.5 9.5 4.3-1.3 7.5-4.9 7.5-9.5V6L12 3Z"/><path d="m9 12 2 2 4-4"/>',
  video:
    '<rect x="3" y="5" width="13" height="14" rx="2"/><path d="m16 10 5-2.5v9L16 14"/><path d="M8 10.5v3l2.5-1.5L8 10.5Z"/>',
};

const FILLED = {
  heart: '<path fill="currentColor" stroke="none" d="M19.5 12.6 12 20l-7.5-7.4A4.6 4.6 0 0 1 12 6.3a4.6 4.6 0 0 1 7.5 6.3Z"/>',
};

const svg = computed(() => (props.filled && FILLED[props.name]) || PATHS[props.name] || '');
</script>

<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="svg"
  />
</template>

<style scoped>
.icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;
}
</style>
