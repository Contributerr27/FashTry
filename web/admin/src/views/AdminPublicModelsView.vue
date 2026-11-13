<script setup>
import { ref, watch } from 'vue';
import {
  adminPagePublicModels,
  adminCreatePublicModel,
  adminUpdatePublicModel,
  adminDeletePublicModel,
  adminUploadPublicModelImage,
} from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt } from '../lib/format.js';
import {
  PUBLIC_MODEL_GENDER_OPTIONS,
  PUBLIC_MODEL_REGION_OPTIONS,
  PUBLIC_MODEL_AGE_OPTIONS,
  PUBLIC_MODEL_STYLE_OPTIONS,
  publicModelGenderLabel,
  publicModelRegionLabel,
  publicModelAgeLabel,
  publicModelStyleLabel,
} from '../lib/adminEnums.js';

const page = ref(1);
const pageSize = ref(50);
const keyword = ref('');
const data = ref({ records: [], total: 0 });
const err = ref('');

const modalOpen = ref(false);
const editingId = ref(null);
const form = ref(emptyForm());
const formErr = ref('');
const saving = ref(false);
const uploading = ref(false);
const fileInputRef = ref(null);

function emptyForm() {
  return {
    name: '',
    gender: 'female',
    region: '',
    ageGroup: '',
    style: '',
    imageUrl: '',
    thumbUrl: '',
    isActive: '1',
    sortOrder: '0',
  };
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
    gender: row.gender ?? 'female',
    region: row.region ?? '',
    ageGroup: row.ageGroup ?? '',
    style: row.style ?? '',
    imageUrl: row.imageUrl ?? '',
    thumbUrl: row.thumbUrl ?? '',
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
    gender: f.gender.trim(),
    region: f.region.trim() || undefined,
    ageGroup: f.ageGroup.trim() || undefined,
    style: f.style.trim() || undefined,
    imageUrl: f.imageUrl.trim(),
    thumbUrl: f.thumbUrl.trim() || undefined,
    isActive: parseInt(f.isActive, 10) || 0,
    sortOrder: parseInt(f.sortOrder, 10) || 0,
  };
}

async function save() {
  formErr.value = '';
  if (!form.value.imageUrl?.trim()) {
    formErr.value = '请先上传模特图片';
    return;
  }
  saving.value = true;
  try {
    const body = toPayload();
    if (editingId.value == null) {
      await adminCreatePublicModel(body);
    } else {
      await adminUpdatePublicModel(editingId.value, body);
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
  if (!confirm(`删除模特 #${row.id} ${row.name}？`)) return;
  try {
    await adminDeletePublicModel(row.id);
    await load();
  } catch (e) {
    err.value = e.message || '删除失败';
  }
}

async function load() {
  err.value = '';
  try {
    data.value = await adminPagePublicModels(page.value, pageSize.value, keyword.value.trim() || undefined);
  } catch (e) {
    err.value = e.message || '加载失败';
  }
}

watch([page, pageSize], load, { immediate: true });

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
  formErr.value = '';
  uploading.value = true;
  try {
    const data = await adminUploadPublicModelImage(file);
    form.value.imageUrl = data.imageUrl || '';
    form.value.thumbUrl = data.thumbUrl || '';
  } catch (err) {
    formErr.value = err.message || '上传失败';
  } finally {
    uploading.value = false;
  }
}
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <h2 class="admin-page-title">公共模特库</h2>
      <button type="button" class="admin-btn admin-btn-primary" @click="openCreate">新增</button>
    </div>
    <div class="admin-toolbar">
      <input v-model="keyword" class="admin-input admin-input-inline" placeholder="代号 / 性别 / 地区" @keyup.enter="search" />
      <button type="button" class="admin-btn" @click="search">搜索</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap admin-table-scroll">
      <table class="admin-table admin-table-sm">
        <thead>
          <tr>
            <th>ID</th>
            <th>代号</th>
            <th>性别</th>
            <th>地区</th>
            <th>年龄段</th>
            <th>风格</th>
            <th>启用</th>
            <th>排序</th>
            <th>创建</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="r.id">
            <td>{{ r.id }}</td>
            <td class="mono">{{ r.name }}</td>
            <td>{{ publicModelGenderLabel(r.gender) }}</td>
            <td>{{ publicModelRegionLabel(r.region) }}</td>
            <td>{{ publicModelAgeLabel(r.ageGroup) }}</td>
            <td>{{ publicModelStyleLabel(r.style) }}</td>
            <td>{{ r.isActive === 1 ? '是' : '否' }}</td>
            <td>{{ r.sortOrder }}</td>
            <td>{{ fmtDt(r.createdAt) }}</td>
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

    <div v-if="modalOpen" class="admin-modal-overlay" @click.self="closeModal">
      <div class="admin-modal">
        <h3 class="admin-modal-title">{{ editingId == null ? '新增模特' : `编辑 #${editingId}` }}</h3>
        <p v-if="formErr" class="admin-error">{{ formErr }}</p>
        <div class="admin-form-grid">
          <label>代号* <input v-model="form.name" class="admin-input" placeholder="如 Asian_Female_01" /></label>
          <label>性别*
            <select v-model="form.gender" class="admin-input">
              <option v-for="o in PUBLIC_MODEL_GENDER_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>地区
            <select v-model="form.region" class="admin-input">
              <option v-for="o in PUBLIC_MODEL_REGION_OPTIONS" :key="o.label + o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>年龄段
            <select v-model="form.ageGroup" class="admin-input">
              <option v-for="o in PUBLIC_MODEL_AGE_OPTIONS" :key="o.label + o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>风格
            <select v-model="form.style" class="admin-input">
              <option v-for="o in PUBLIC_MODEL_STYLE_OPTIONS" :key="o.label + o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>排序 <input v-model="form.sortOrder" class="admin-input" type="number" /></label>
          <label>启用 <select v-model="form.isActive" class="admin-input"><option value="1">是</option><option value="0">否</option></select></label>
          <div class="full admin-upload-block">
            <span class="admin-upload-label">模特图片*</span>
            <input
              ref="fileInputRef"
              type="file"
              class="admin-file-input"
              accept="image/jpeg,image/png,image/gif,image/webp"
              @change="onImageFileChange"
            />
            <div class="admin-upload-row">
              <button type="button" class="admin-btn" :disabled="uploading" @click="triggerPickImage">
                {{ uploading ? '上传中…' : form.imageUrl ? '重新上传图片' : '选择图片上传（腾讯云）' }}
              </button>
              <span v-if="form.imageUrl" class="admin-upload-hint">缩略图已自动生成</span>
            </div>
            <div v-if="form.imageUrl || form.thumbUrl" class="admin-upload-previews">
              <div v-if="form.thumbUrl" class="admin-upload-preview">
                <span>列表缩略图</span>
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
