<script setup>
import { ref, watch, computed, onUnmounted } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import {
  adminGetBrand,
  adminPageBrandGarments,
  adminCreateBrandGarment,
  adminUpdateBrandGarment,
  adminDeleteBrandGarment,
  adminUploadBrandGarmentImage,
} from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt } from '../lib/format.js';
import {
  PUBLIC_GARMENT_SCENE_OPTIONS,
  PUBLIC_GARMENT_TYPE_OPTIONS,
  PUBLIC_MODEL_GENDER_OPTIONS,
  publicGarmentSceneLabel,
  publicGarmentTypeLabel,
  publicModelGenderLabel,
} from '../lib/adminEnums.js';

const route = useRoute();
const brandId = computed(() => String(route.params.brandId || ''));

const brandName = ref('');
const page = ref(1);
const pageSize = ref(50);
const keyword = ref('');
const sceneFilter = ref('');
const genderFilter = ref('');
const data = ref({ records: [], total: 0 });
const err = ref('');

const modalOpen = ref(false);
const editingId = ref(null);
const form = ref(emptyForm());
const formErr = ref('');
const saving = ref(false);
const uploading = ref(false);
const fileInputRef = ref(null);

const previewUrl = ref(null);
let previewEscHandler = null;

function detachPreviewEsc() {
  if (previewEscHandler) {
    window.removeEventListener('keydown', previewEscHandler);
    previewEscHandler = null;
  }
}

function openPreview(row) {
  const url = (row.imageUrl || row.thumbUrl || '').trim();
  if (!url) return;
  detachPreviewEsc();
  previewUrl.value = url;
  previewEscHandler = (e) => {
    if (e.key === 'Escape') closePreview();
  };
  window.addEventListener('keydown', previewEscHandler);
}

function closePreview() {
  previewUrl.value = null;
  detachPreviewEsc();
}

onUnmounted(() => {
  detachPreviewEsc();
});

function emptyForm() {
  return {
    name: '',
    nameEn: '',
    sceneCategory: 'wedding',
    gender: 'unisex',
    garmentType: '',
    imageUrl: '',
    thumbUrl: '',
    remark: '',
    purchaseUrl: '',
    auditStatus: '2',
    isActive: '1',
    sortOrder: '0',
  };
}

function auditLabel(v) {
  const m = { 0: '草稿', 1: '待审', 2: '已上架', 3: '驳回' };
  return m[v] ?? v;
}

function openCreate() {
  editingId.value = null;
  form.value = emptyForm();
  formErr.value = '';
  modalOpen.value = true;
}

function openEdit(row) {
  editingId.value = row.id;
  form.value = {
    name: row.name ?? '',
    nameEn: row.nameEn ?? '',
    sceneCategory: row.sceneCategory ?? 'wedding',
    gender: row.gender ?? 'unisex',
    garmentType: row.garmentType ?? '',
    imageUrl: row.imageUrl ?? '',
    thumbUrl: row.thumbUrl ?? '',
    remark: row.remark ?? '',
    purchaseUrl: row.purchaseUrl ?? '',
    auditStatus: String(row.auditStatus ?? 2),
    isActive: String(row.isActive ?? 1),
    sortOrder: row.sortOrder != null ? String(row.sortOrder) : '0',
  };
  formErr.value = '';
  modalOpen.value = true;
}

function closeModal() {
  modalOpen.value = false;
}

function toPayload() {
  const f = form.value;
  return {
    name: f.name.trim(),
    nameEn: f.nameEn.trim() || undefined,
    sceneCategory: f.sceneCategory.trim(),
    gender: f.gender.trim(),
    garmentType: f.garmentType.trim() || undefined,
    imageUrl: f.imageUrl.trim(),
    thumbUrl: f.thumbUrl.trim() || undefined,
    remark: f.remark.trim() || undefined,
    purchaseUrl: f.purchaseUrl.trim() || undefined,
    auditStatus: parseInt(f.auditStatus, 10),
    isActive: parseInt(f.isActive, 10) || 0,
    sortOrder: parseInt(f.sortOrder, 10) || 0,
  };
}

async function save() {
  formErr.value = '';
  if (!form.value.imageUrl?.trim()) {
    formErr.value = '请先上传服装图片';
    return;
  }
  const id = brandId.value;
  if (!id) return;
  saving.value = true;
  try {
    const body = toPayload();
    if (editingId.value == null) {
      await adminCreateBrandGarment(id, body);
    } else {
      await adminUpdateBrandGarment(id, editingId.value, body);
    }
    closeModal();
    await load();
  } catch (e) {
    formErr.value = e.message || '保存失败';
  } finally {
    saving.value = false;
  }
}

async function remove(row) {
  const id = brandId.value;
  if (!id) return;
  if (!confirm(`删除服装 #${row.id} ${row.name}？`)) return;
  try {
    await adminDeleteBrandGarment(id, row.id);
    await load();
  } catch (e) {
    err.value = e.message || '删除失败';
  }
}

async function loadBrand() {
  const id = brandId.value;
  if (!id) return;
  try {
    const b = await adminGetBrand(id);
    brandName.value = b.brandName || '';
  } catch {
    brandName.value = '';
  }
}

async function load() {
  err.value = '';
  const id = brandId.value;
  if (!id) {
    err.value = '无效的品牌 ID';
    return;
  }
  try {
    data.value = await adminPageBrandGarments(
      id,
      page.value,
      pageSize.value,
      keyword.value.trim() || undefined,
      sceneFilter.value.trim() || undefined,
      genderFilter.value.trim() || undefined,
    );
  } catch (e) {
    err.value = e.message || '加载失败';
  }
}

watch([page, pageSize, brandId], () => {
  void loadBrand();
  load();
});

watch(brandId, () => {
  page.value = 1;
});

function search() {
  page.value = 1;
  load();
}

function triggerPickImage() {
  fileInputRef.value?.click();
}

async function onImageFileChange(e) {
  const input = e.target;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  const id = brandId.value;
  if (!id) return;
  formErr.value = '';
  uploading.value = true;
  try {
    const res = await adminUploadBrandGarmentImage(id, file);
    form.value.imageUrl = res.imageUrl || '';
    form.value.thumbUrl = res.thumbUrl || '';
  } catch (uploadErr) {
    formErr.value = uploadErr.message || '上传失败';
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <RouterLink to="/brands" class="admin-back-link">← 品牌列表</RouterLink>
        <h2 class="admin-page-title">品牌服装库 · {{ brandName || brandId }}</h2>
      </div>
      <button type="button" class="admin-btn admin-btn-primary" @click="openCreate">新增服装</button>
    </div>
    <div class="admin-toolbar">
      <select v-model="sceneFilter" class="admin-input admin-input-inline" @change="search">
        <option value="">全部分类</option>
        <option v-for="o in PUBLIC_GARMENT_SCENE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
      <select v-model="genderFilter" class="admin-input admin-input-inline" @change="search">
        <option value="">全部性别</option>
        <option v-for="o in PUBLIC_MODEL_GENDER_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
      <input v-model="keyword" class="admin-input admin-input-inline" placeholder="名称 / 备注" @keyup.enter="search" />
      <button type="button" class="admin-btn" @click="search">搜索</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap admin-table-scroll">
      <table class="admin-table admin-table-sm">
        <thead>
          <tr>
            <th>ID</th>
            <th>缩略图</th>
            <th>名称</th>
            <th>场景</th>
            <th>性别</th>
            <th>服装类型</th>
            <th>审核</th>
            <th>启用</th>
            <th>排序</th>
            <th>更新</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="r.id">
            <td>{{ r.id }}</td>
            <td>
              <button
                v-if="r.thumbUrl || r.imageUrl"
                type="button"
                class="admin-thumb-btn"
                title="点击放大"
                @click="openPreview(r)"
              >
                <img :src="r.thumbUrl || r.imageUrl" alt="" class="admin-thumb-40" />
              </button>
              <span v-else>—</span>
            </td>
            <td class="mono">{{ r.name }}</td>
            <td>{{ publicGarmentSceneLabel(r.sceneCategory) }}</td>
            <td>{{ publicModelGenderLabel(r.gender) }}</td>
            <td>{{ publicGarmentTypeLabel(r.garmentType) }}</td>
            <td>{{ auditLabel(r.auditStatus) }}</td>
            <td>{{ r.isActive === 1 ? '是' : '否' }}</td>
            <td>{{ r.sortOrder }}</td>
            <td>{{ fmtDt(r.updatedAt) }}</td>
            <td class="admin-actions">
              <button type="button" class="admin-link-btn" @click="openEdit(r)">编辑</button>
              <button type="button" class="admin-link-btn danger" @click="remove(r)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!data.records?.length" class="admin-empty">暂无数据</p>
    </div>
    <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />

    <div
      v-if="previewUrl"
      class="garment-img-preview-overlay"
      role="dialog"
      aria-modal="true"
      @click.self="closePreview"
    >
      <button type="button" class="garment-img-preview-close" @click="closePreview">×</button>
      <img :src="previewUrl" alt="预览" class="garment-img-preview-img" @click.stop />
    </div>

    <div v-if="modalOpen" class="admin-modal-overlay">
      <div class="admin-modal">
        <h3 class="admin-modal-title">{{ editingId == null ? '新增服装' : `编辑 #${editingId}` }}</h3>
        <p v-if="formErr" class="admin-error">{{ formErr }}</p>
        <div class="admin-form-grid">
          <label>名称* <input v-model="form.name" class="admin-input" /></label>
          <label>英文名称 <input v-model="form.nameEn" class="admin-input" /></label>
          <label>场景分类*
            <select v-model="form.sceneCategory" class="admin-input">
              <option v-for="o in PUBLIC_GARMENT_SCENE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>性别*
            <select v-model="form.gender" class="admin-input">
              <option v-for="o in PUBLIC_MODEL_GENDER_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>服装类型
            <select v-model="form.garmentType" class="admin-input">
              <option v-for="o in PUBLIC_GARMENT_TYPE_OPTIONS" :key="o.label + o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>上架审核
            <select v-model="form.auditStatus" class="admin-input">
              <option value="0">草稿</option>
              <option value="1">待审</option>
              <option value="2">已上架</option>
              <option value="3">驳回</option>
            </select>
          </label>
          <label>排序 <input v-model="form.sortOrder" class="admin-input" type="number" /></label>
          <label>启用 <select v-model="form.isActive" class="admin-input"><option value="1">是</option><option value="0">否</option></select></label>
          <label class="full">备注 <input v-model="form.remark" class="admin-input" /></label>
          <label class="full">购买链接 <input v-model="form.purchaseUrl" class="admin-input" /></label>
          <div class="full admin-upload-block">
            <span class="admin-upload-label">服装图片*</span>
            <input
              ref="fileInputRef"
              type="file"
              class="admin-file-input"
              accept="image/jpeg,image/png,image/gif,image/webp"
              @change="onImageFileChange"
            />
            <div class="admin-upload-row">
              <button type="button" class="admin-btn" :disabled="uploading" @click="triggerPickImage">
                {{ uploading ? '上传中…' : form.imageUrl ? '重新上传' : '上传图片' }}
              </button>
            </div>
            <div v-if="form.imageUrl || form.thumbUrl" class="admin-upload-previews">
              <div v-if="form.thumbUrl" class="admin-upload-preview">
                <span>缩略图</span>
                <img :src="form.thumbUrl" alt="thumb" />
              </div>
              <div v-if="form.imageUrl" class="admin-upload-preview">
                <span>原图</span>
                <img :src="form.imageUrl" alt="full" />
              </div>
            </div>
          </div>
        </div>
        <div class="admin-modal-actions">
          <button type="button" class="admin-btn" @click="closeModal">取消</button>
          <button type="button" class="admin-btn admin-btn-primary" :disabled="saving" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-back-link {
  display: inline-block;
  margin-bottom: 0.35rem;
  font-size: 0.88rem;
  color: var(--admin-muted, #94a3b8);
  text-decoration: none;
}
.admin-back-link:hover {
  color: var(--admin-accent, #06b6d4);
}
.admin-thumb-btn {
  padding: 0;
  border: none;
  background: transparent;
  cursor: zoom-in;
  border-radius: 4px;
  line-height: 0;
}
.admin-thumb-40 {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  display: block;
}
.garment-img-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2.5rem 1.5rem;
  background: rgba(0, 0, 0, 0.88);
  cursor: zoom-out;
}
.garment-img-preview-img {
  max-width: min(96vw, 1280px);
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
}
.garment-img-preview-close {
  position: fixed;
  top: 1rem;
  right: 1rem;
  z-index: 1;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 1.75rem;
  cursor: pointer;
}
</style>
