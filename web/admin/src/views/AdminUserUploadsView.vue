<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { adminPageUserUploads } from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';

const route = useRoute();
const page = ref(1);
const pageSize = ref(36);
const userId = ref(route.query.userId ? String(route.query.userId) : '');
const category = ref(route.query.category ? String(route.query.category) : '');
const data = ref({ records: [], total: 0 });
const err = ref('');

const lightboxUrl = ref(null);

function openLightbox(url) {
  lightboxUrl.value = url;
}
function closeLightbox() {
  lightboxUrl.value = null;
}

function onKeydown(e) {
  if (e.key === 'Escape') {
    closeLightbox();
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));

async function load() {
  err.value = '';
  try {
    const uid = userId.value.trim() || undefined;
    const cat = category.value.trim() || undefined;
    data.value = await adminPageUserUploads(page.value, pageSize.value, uid, cat);
  } catch (e) {
    err.value = e.message || '加载失败';
  }
}

watch([page, pageSize], load, { immediate: true });

watch(
  () => [route.query.userId, route.query.category],
  () => {
    userId.value = route.query.userId ? String(route.query.userId) : '';
    category.value = route.query.category ? String(route.query.category) : '';
    page.value = 1;
    load();
  }
);

function applyFilter() {
  page.value = 1;
  load();
}
</script>

<template>
  <div class="admin-page">
    <h2 class="admin-page-title">用户上传图片</h2>
    <p class="admin-muted" style="margin: -0.5rem 0 1rem; font-size: 0.9rem">
      包含用户直传 COS、试穿模特/服装图、局部改款/Gemini 任务输入图等；历史数据会在打开本页时自动补录。
    </p>
    <div class="admin-toolbar admin-toolbar-compact">
      <input v-model="userId" class="admin-input admin-input-inline" placeholder="用户ID（可选）" />
      <select v-model="category" class="admin-input admin-input-inline admin-select">
        <option value="">全部分类</option>
        <option value="business">业务图</option>
        <option value="other">其他</option>
      </select>
      <button type="button" class="admin-btn" @click="applyFilter">筛选</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <template v-else>
      <div v-if="data.records?.length" class="upload-grid">
        <template v-for="r in data.records" :key="r.id">
          <button
            v-if="r.fileUrl"
            type="button"
            class="upload-grid-item"
            @click="openLightbox(r.fileUrl)"
          >
            <img :src="r.fileUrl" alt="" loading="lazy" />
          </button>
        </template>
      </div>
      <p v-else class="admin-empty">暂无数据</p>
      <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />
    </template>

    <div
      v-if="lightboxUrl"
      class="lightbox-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="大图预览"
      @click.self="closeLightbox"
    >
      <button type="button" class="lightbox-close" aria-label="关闭" @click="closeLightbox">×</button>
      <div class="lightbox-inner" @click.stop>
        <img :src="lightboxUrl" alt="" class="lightbox-img" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-toolbar-compact {
  margin-bottom: 16px;
}
.admin-select {
  min-width: 140px;
}
.upload-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
  margin-bottom: 20px;
}
.upload-grid-item {
  display: block;
  padding: 0;
  margin: 0;
  border: none;
  border-radius: 8px;
  overflow: hidden;
  cursor: zoom-in;
  background: #f1f5f9;
  aspect-ratio: 1;
  line-height: 0;
}
.upload-grid-item:focus {
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}
.upload-grid-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  vertical-align: top;
}
.lightbox-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(15, 23, 42, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px 24px;
  cursor: zoom-out;
}
.lightbox-close {
  position: fixed;
  top: 16px;
  right: 20px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  z-index: 1001;
}
.lightbox-close:hover {
  background: rgba(255, 255, 255, 0.22);
}
.lightbox-inner {
  max-width: min(96vw, 1400px);
  max-height: 90vh;
  cursor: default;
}
.lightbox-img {
  max-width: 100%;
  max-height: 90vh;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 4px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.35);
}
</style>
