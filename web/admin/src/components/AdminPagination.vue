<script setup>
const props = defineProps({
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  total: { type: Number, required: true },
});
const emit = defineEmits(['update:page']);

const totalPages = () => Math.max(1, Math.ceil(props.total / props.pageSize) || 1);

function prev() {
  if (props.page > 1) emit('update:page', props.page - 1);
}
function next() {
  if (props.page < totalPages()) emit('update:page', props.page + 1);
}
</script>

<template>
  <div class="admin-pager">
    <span class="admin-pager-info">共 {{ total }} 条 · 第 {{ page }} / {{ totalPages() }} 页</span>
    <button type="button" class="admin-pager-btn" :disabled="page <= 1" @click="prev">上一页</button>
    <button type="button" class="admin-pager-btn" :disabled="page >= totalPages()" @click="next">下一页</button>
  </div>
</template>
