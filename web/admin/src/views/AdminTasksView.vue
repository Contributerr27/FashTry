<script setup>
import { ref, watch, computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { adminPageTasks, adminTaskDetail } from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import {
  fmtDt,
  taskStatusText,
  tryonCategoryText,
  tryonProviderText,
  adminTaskRecordSourceText,
  geminiTaskTypeText,
  adminTaskDetailTagText,
  adminUnifiedTaskTypeText,
  formatVideoDetailTag,
} from '../lib/format.js';
import {
  resultImagesFromGeminiResultJson,
  uploadUrlsFromGeminiPayloadJson,
  uploadUrlsFromTryonTask,
} from '../lib/taskPreview.js';

const route = useRoute();
const page = ref(1);
const pageSize = ref(20);
const userId = ref(route.query.userId ? String(route.query.userId) : '');
const data = ref({ records: [], total: 0 });
const err = ref('');

const previewOpen = ref(false);
const previewListRow = ref(null);
const previewDetail = ref(null);
const previewLoading = ref(false);
const previewErr = ref('');

const lightboxUrl = ref('');

function tryonResultMainUrl(r) {
  return r?.resultCosUrl || r?.resultUrl || r?.thumbUrl || '';
}

function openLightbox(url) {
  if (url) lightboxUrl.value = url;
}

function closeLightbox() {
  lightboxUrl.value = '';
}

function onKeydown(e) {
  if (e.key === 'Escape') {
    if (lightboxUrl.value) closeLightbox();
    else if (previewOpen.value) closePreview();
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onUnmounted(() => window.removeEventListener('keydown', onKeydown));

function closePreview() {
  previewOpen.value = false;
  previewListRow.value = null;
  previewDetail.value = null;
  previewErr.value = '';
  previewLoading.value = false;
  closeLightbox();
}

async function openPreview(row) {
  previewListRow.value = row;
  previewDetail.value = null;
  previewErr.value = '';
  previewOpen.value = true;
  previewLoading.value = true;
  try {
    previewDetail.value = await adminTaskDetail(row.recordSource, row.id);
  } catch (e) {
    previewErr.value = e.message || '加载失败';
  } finally {
    previewLoading.value = false;
  }
}

function previewImagesFromApi(apiList, fallbackFn) {
  if (Array.isArray(apiList) && apiList.length) {
    return apiList.filter((item) => item?.url);
  }
  return fallbackFn();
}

const tryonVo = computed(() => previewDetail.value?.tryon || null);
const geminiVo = computed(() => previewDetail.value?.geminiImage || null);
const videoVo = computed(() => previewDetail.value?.modelVideo || null);

const geminiUploadImages = computed(() => {
  const g = geminiVo.value;
  return previewImagesFromApi(g?.uploadImages, () =>
    g?.payloadJson ? uploadUrlsFromGeminiPayloadJson(g.payloadJson) : [],
  );
});

const tryonUploadImages = computed(() => {
  const t = tryonVo.value;
  return previewImagesFromApi(t?.uploadImages, () => uploadUrlsFromTryonTask(t));
});

const tryonResultImages = computed(() => {
  const t = tryonVo.value;
  return previewImagesFromApi(t?.resultImages, () => {
    const url = tryonResultMainUrl(t);
    return url ? [{ url, label: '试穿效果' }] : [];
  });
});

const geminiResultImages = computed(() => {
  const g = geminiVo.value;
  return previewImagesFromApi(g?.resultImages, () =>
    g?.resultJson ? resultImagesFromGeminiResultJson(g.resultJson) : [],
  );
});

const videoUploadImages = computed(() => {
  const v = videoVo.value;
  return previewImagesFromApi(v?.uploadImages, () =>
    v?.sourceImageUrl ? [{ url: v.sourceImageUrl, label: '起始帧图' }] : [],
  );
});

async function load() {
  err.value = '';
  try {
    const uid = userId.value.trim() || undefined;
    data.value = await adminPageTasks(page.value, pageSize.value, uid);
  } catch (e) {
    err.value = e.message || '加载失败';
  }
}

watch([page, pageSize], load, { immediate: true });

watch(
  () => route.query.userId,
  (q) => {
    userId.value = q ? String(q) : '';
    page.value = 1;
    load();
  },
);

function applyFilter() {
  page.value = 1;
  load();
}
</script>

<template>
  <div class="admin-page">
    <h2 class="admin-page-title">任务列表</h2>
    <p class="admin-muted" style="margin: -0.5rem 0 1rem; font-size: 0.9rem">
      虚拟试穿、局部改款、模特出图、服装套图、AI 写真、图生视频等任务统一按创建时间倒序展示。
    </p>
    <div class="admin-toolbar">
      <input v-model="userId" class="admin-input admin-input-inline" placeholder="用户ID（可选）" />
      <button type="button" class="admin-btn" @click="applyFilter">筛选</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap admin-table-scroll">
      <table class="admin-table admin-table-sm">
        <thead>
          <tr>
            <th>ID</th>
            <th>来源</th>
            <th>任务类型</th>
            <th>明细</th>
            <th>用户ID</th>
            <th>昵称</th>
            <th>状态</th>
            <th>消耗点</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="`${r.recordSource}-${r.id}`">
            <td class="mono">{{ r.id }}</td>
            <td>{{ adminTaskRecordSourceText(r.recordSource) }}</td>
            <td>{{ adminUnifiedTaskTypeText(r) }}</td>
            <td>{{ adminTaskDetailTagText(r) }}</td>
            <td>{{ r.userId ?? '—' }}</td>
            <td>{{ r.userNickname || '—' }}</td>
            <td>{{ taskStatusText(r.status) }}</td>
            <td>{{ r.consumeCredits ?? '—' }}</td>
            <td>{{ fmtDt(r.createdAt) }}</td>
            <td>
              <button type="button" class="admin-link-btn" @click="openPreview(r)">查看</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!data.records?.length" class="admin-empty">暂无数据</p>
    </div>
    <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />

    <div v-if="previewOpen" class="admin-modal-overlay" @click.self="closePreview">
      <div class="admin-modal admin-preview-modal admin-tasks-preview-modal">
        <div class="admin-preview-head">
          <h3 class="admin-modal-title">
            任务详情
            <span v-if="previewListRow" class="admin-preview-sub">
              · {{ adminTaskRecordSourceText(previewListRow.recordSource) }} #{{ previewListRow.id }}
            </span>
          </h3>
          <button type="button" class="admin-preview-close" aria-label="关闭" @click="closePreview">×</button>
        </div>
        <p v-if="previewLoading" class="admin-muted">加载中…</p>
        <p v-else-if="previewErr" class="admin-error">{{ previewErr }}</p>

        <!-- 试穿 -->
        <template v-else-if="tryonVo">
          <p class="admin-preview-user">
            用户：<strong>{{ tryonVo.userNickname || '—' }}</strong>
            <span class="admin-preview-uid">（ID {{ tryonVo.userId }}）</span>
          </p>
          <p class="admin-preview-meta">
            {{ taskStatusText(tryonVo.status) }} · {{ tryonCategoryText(tryonVo.category) }} · {{ tryonProviderText(tryonVo.provider) }}
          </p>
          <div v-if="tryonUploadImages.length" class="admin-preview-cell full" style="margin-bottom: 1rem">
            <div class="admin-preview-label">用户上传（{{ tryonUploadImages.length }} 张）</div>
            <div class="admin-preview-gallery">
              <button
                v-for="(item, i) in tryonUploadImages"
                :key="`tryon-upload-${i}-${item.url}`"
                type="button"
                class="admin-preview-thumb-btn"
                :title="item.label"
                @click="openLightbox(item.url)"
              >
                <img :src="item.url" :alt="item.label" class="admin-preview-thumb-img" loading="lazy" />
                <span class="admin-preview-thumb-caption">{{ item.label }}</span>
              </button>
            </div>
          </div>
          <div class="admin-preview-grid">
          <div v-if="tryonResultImages.length" class="admin-preview-cell full">
            <div class="admin-preview-label">试穿效果</div>
            <div class="admin-preview-gallery">
              <button
                v-for="(item, i) in tryonResultImages"
                :key="`tryon-result-${i}-${item.url}`"
                type="button"
                class="admin-preview-thumb-btn wide"
                :title="item.label || '试穿效果'"
                @click="openLightbox(item.url)"
              >
                <img :src="item.url" :alt="item.label || '试穿效果'" class="admin-preview-thumb-img" />
                <span v-if="item.label" class="admin-preview-thumb-caption">{{ item.label }}</span>
              </button>
            </div>
          </div>
          <span v-else class="admin-preview-missing">暂无结果图</span>
          </div>
          <p v-if="tryonVo.errorMsg" class="admin-error admin-preview-err">失败原因：{{ tryonVo.errorMsg }}</p>
        </template>

        <!-- Gemini 图任务 -->
        <template v-else-if="geminiVo">
          <p class="admin-preview-user">
            用户：<strong>{{ geminiVo.userNickname || '—' }}</strong>
            <span class="admin-preview-uid">（ID {{ geminiVo.userId ?? '—' }}）</span>
          </p>
          <p class="admin-preview-meta">
            {{ taskStatusText(geminiVo.status) }} · {{ geminiTaskTypeText(geminiVo.taskType) }}
          </p>
          <div v-if="geminiUploadImages.length" class="admin-preview-cell full" style="margin-bottom: 1rem">
            <div class="admin-preview-label">用户上传（{{ geminiUploadImages.length }} 张）</div>
            <div class="admin-preview-gallery">
              <button
                v-for="(item, i) in geminiUploadImages"
                :key="`upload-${i}-${item.url}`"
                type="button"
                class="admin-preview-thumb-btn"
                :title="item.label"
                @click="openLightbox(item.url)"
              >
                <img :src="item.url" :alt="item.label" class="admin-preview-thumb-img" loading="lazy" />
                <span class="admin-preview-thumb-caption">{{ item.label }}</span>
              </button>
            </div>
          </div>
          <div v-if="geminiResultImages.length" class="admin-preview-cell full" style="margin-bottom: 1rem">
            <div class="admin-preview-label">生成结果（{{ geminiResultImages.length }} 张）</div>
            <div class="admin-preview-gallery">
              <button
                v-for="(item, i) in geminiResultImages"
                :key="`result-${i}-${item.url}`"
                type="button"
                class="admin-preview-thumb-btn"
                :title="item.label || `结果 ${i + 1}`"
                @click="openLightbox(item.url)"
              >
                <img :src="item.url" :alt="item.label || `结果 ${i + 1}`" class="admin-preview-thumb-img" loading="lazy" />
                <span v-if="item.label" class="admin-preview-thumb-caption">{{ item.label }}</span>
                <span v-else class="admin-preview-thumb-caption">结果 {{ i + 1 }}</span>
              </button>
            </div>
          </div>
          <p v-else-if="geminiVo.status === 2" class="admin-preview-missing" style="margin-bottom: 1rem">暂无结果图</p>
          <p v-if="!geminiUploadImages.length && geminiVo.payloadJson" class="admin-muted" style="font-size: 0.85rem; margin-bottom: 0.75rem">
            payload 中未解析到 http(s) 图片地址，可展开下方 JSON 核对原始数据。
          </p>
          <details class="admin-json-details">
            <summary>payload_json</summary>
            <pre class="admin-json-pre">{{ geminiVo.payloadJson || '—' }}</pre>
          </details>
          <details class="admin-json-details">
            <summary>result_json</summary>
            <pre class="admin-json-pre">{{ geminiVo.resultJson || '—' }}</pre>
          </details>
          <p v-if="geminiVo.errorMsg" class="admin-error admin-preview-err">失败原因：{{ geminiVo.errorMsg }}</p>
        </template>

        <!-- 模特视频 -->
        <template v-else-if="videoVo">
          <p class="admin-preview-user">
            用户：<strong>{{ videoVo.userNickname || '—' }}</strong>
            <span class="admin-preview-uid">（ID {{ videoVo.userId }}）</span>
          </p>
          <p class="admin-preview-meta">
            {{ taskStatusText(videoVo.status) }} · {{ formatVideoDetailTag(`${videoVo.resolution || ''} ${videoVo.aspectRatio || ''}`.trim()) }} ·
            {{ videoVo.durationSeconds ?? '—' }} 秒
          </p>
          <p v-if="videoVo.prompt" class="admin-muted" style="font-size: 0.88rem">提示词：{{ videoVo.prompt }}</p>
          <div v-if="videoVo.resultVideoUrl" class="admin-preview-video-wrap">
            <video class="admin-preview-video" controls :src="videoVo.resultVideoUrl" />
          </div>
          <div v-else class="admin-preview-missing">暂无成片</div>
          <div class="admin-preview-grid" style="margin-top: 1rem">
            <div v-if="videoUploadImages.length" class="admin-preview-cell full">
              <div class="admin-preview-label">起始帧图</div>
              <div class="admin-preview-gallery">
                <button
                  v-for="(item, i) in videoUploadImages"
                  :key="`video-upload-${i}-${item.url}`"
                  type="button"
                  class="admin-preview-thumb-btn wide"
                  :title="item.label"
                  @click="openLightbox(item.url)"
                >
                  <img :src="item.url" :alt="item.label" class="admin-preview-thumb-img" />
                  <span class="admin-preview-thumb-caption">{{ item.label }}</span>
                </button>
              </div>
            </div>
            <div v-else class="admin-preview-cell full">
              <div class="admin-preview-label">起始帧图</div>
              <span class="admin-preview-missing">无</span>
            </div>
          </div>
          <p class="admin-muted mono" style="font-size: 0.85rem; margin-top: 0.75rem">
            源试穿任务 ID：{{ videoVo.sourceTryonTaskId ?? '—' }} · 源改款任务 ID：{{ videoVo.sourceImageEditTaskId ?? '—' }}
          </p>
          <p v-if="videoVo.errorMsg" class="admin-error admin-preview-err">失败原因：{{ videoVo.errorMsg }}</p>
        </template>
      </div>
    </div>

    <div
      v-if="lightboxUrl"
      class="admin-lightbox-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="大图预览"
      @click.self="closeLightbox"
    >
      <button type="button" class="admin-lightbox-close" aria-label="关闭" @click="closeLightbox">×</button>
      <div class="admin-lightbox-inner" @click.stop>
        <img :src="lightboxUrl" alt="" class="admin-lightbox-img" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-preview-sub {
  font-weight: normal;
  font-size: 0.88rem;
  color: #64748b;
}
.admin-json-details {
  margin: 0.5rem 0;
  font-size: 0.88rem;
}
.admin-json-pre {
  max-height: 200px;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  padding: 0.75rem;
  border-radius: 6px;
  font-size: 0.75rem;
  white-space: pre-wrap;
  word-break: break-all;
}
.admin-preview-video-wrap {
  max-width: 100%;
  margin-top: 0.5rem;
}
.admin-preview-video {
  width: 100%;
  max-height: 360px;
  border-radius: 8px;
  background: #000;
}
.admin-preview-err {
  margin-top: 0.75rem;
  font-size: 0.9rem;
}
</style>
