<script setup>
defineProps({
  text: { type: String, default: '' },
  pct: { type: Number, default: null },
});
</script>

<template>
  <div v-if="text" class="wb-progress" role="status">
    <span class="spinner" style="width: 16px; height: 16px"></span>
    <span>{{ text }}</span>
    <span v-if="pct != null" class="wb-progress__bar"><i :style="{ width: Math.max(4, Math.min(100, pct)) + '%' }"></i></span>
  </div>
</template>
