<script setup>
import { ref, watch, computed } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import {
  adminGetBrand,
  adminPageBrandUsers,
  adminCreateBrandUser,
  adminUpdateBrandUser,
  adminResetBrandUserPassword,
  adminDeleteBrandUser,
} from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt } from '../lib/format.js';

const route = useRoute();
const brandId = computed(() => String(route.params.brandId || ''));

const brandName = ref('');
const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const data = ref({ records: [], total: 0 });
const err = ref('');

const modalOpen = ref(false);
const editingId = ref(null);
const form = ref(emptyForm());
const formErr = ref('');
const saving = ref(false);

const pwdModal = ref(false);
const pwdUserId = ref(null);
const pwdForm = ref({ newPassword: '' });
const pwdErr = ref('');

const ROLE_OPTIONS = [
  { value: 'owner', label: '负责人' },
  { value: 'admin', label: '管理员' },
  { value: 'editor', label: '编辑' },
  { value: 'viewer', label: '只读' },
];

function roleLabel(v) {
  const o = ROLE_OPTIONS.find((x) => x.value === v);
  return o ? o.label : v;
}

function emptyForm() {
  return {
    username: '',
    password: '',
    displayName: '',
    mobile: '',
    role: 'editor',
    status: '1',
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
    username: row.username ?? '',
    password: '',
    displayName: row.displayName ?? '',
    mobile: row.mobile ?? '',
    role: row.role ?? 'editor',
    status: String(row.status ?? 1),
  };
  formErr.value = '';
  modalOpen.value = true;
}

function closeModal() {
  modalOpen.value = false;
}

async function save() {
  formErr.value = '';
  const id = brandId.value;
  if (!id) return;
  if (editingId.value == null) {
    if (!form.value.username?.trim() || !form.value.password) {
      formErr.value = '请填写登录名与初始密码';
      return;
    }
  }
  saving.value = true;
  try {
    if (editingId.value == null) {
      await adminCreateBrandUser(id, {
        username: form.value.username.trim(),
        password: form.value.password,
        displayName: form.value.displayName.trim() || undefined,
        mobile: form.value.mobile.trim() || undefined,
        role: form.value.role,
        status: parseInt(form.value.status, 10),
      });
    } else {
      await adminUpdateBrandUser(id, editingId.value, {
        displayName: form.value.displayName.trim() || undefined,
        mobile: form.value.mobile.trim() || undefined,
        role: form.value.role,
        status: parseInt(form.value.status, 10),
      });
    }
    closeModal();
    await load();
  } catch (e) {
    formErr.value = e.message || '保存失败';
  } finally {
    saving.value = false;
  }
}

function openPwd(row) {
  pwdUserId.value = row.id;
  pwdForm.value = { newPassword: '' };
  pwdErr.value = '';
  pwdModal.value = true;
}

function closePwd() {
  pwdModal.value = false;
  pwdUserId.value = null;
}

async function savePwd() {
  pwdErr.value = '';
  const id = brandId.value;
  const uid = pwdUserId.value;
  if (!id || !uid || !pwdForm.value.newPassword || pwdForm.value.newPassword.length < 6) {
    pwdErr.value = '密码至少 6 位';
    return;
  }
  try {
    await adminResetBrandUserPassword(id, uid, { newPassword: pwdForm.value.newPassword });
    closePwd();
  } catch (e) {
    pwdErr.value = e.message || '重置失败';
  }
}

async function remove(row) {
  const id = brandId.value;
  if (!id) return;
  if (!confirm(`删除账号 ${row.username}？`)) return;
  try {
    await adminDeleteBrandUser(id, row.id);
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
    data.value = await adminPageBrandUsers(id, page.value, pageSize.value, keyword.value.trim() || undefined);
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
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <div>
        <RouterLink to="/brands" class="admin-back-link">← 品牌列表</RouterLink>
        <h2 class="admin-page-title">品牌账号 · {{ brandName || brandId }}</h2>
      </div>
      <button type="button" class="admin-btn admin-btn-primary" @click="openCreate">新建账号</button>
    </div>
    <div class="admin-toolbar">
      <input v-model="keyword" class="admin-input admin-input-inline" placeholder="登录名 / 显示名 / 手机" @keyup.enter="search" />
      <button type="button" class="admin-btn" @click="search">搜索</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap admin-table-scroll">
      <table class="admin-table admin-table-sm">
        <thead>
          <tr>
            <th>ID</th>
            <th>登录名</th>
            <th>显示名</th>
            <th>手机</th>
            <th>角色</th>
            <th>状态</th>
            <th>更新</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="r.id">
            <td>{{ r.id }}</td>
            <td class="mono">{{ r.username }}</td>
            <td>{{ r.displayName || '—' }}</td>
            <td>{{ r.mobile || '—' }}</td>
            <td>{{ roleLabel(r.role) }}</td>
            <td>{{ r.status === 1 ? '启用' : '禁用' }}</td>
            <td>{{ fmtDt(r.updatedAt) }}</td>
            <td class="admin-actions">
              <button type="button" class="admin-link-btn" @click="openEdit(r)">编辑</button>
              <button type="button" class="admin-link-btn" @click="openPwd(r)">重置密码</button>
              <button type="button" class="admin-link-btn danger" @click="remove(r)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!data.records?.length" class="admin-empty">暂无数据</p>
    </div>
    <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />

    <div v-if="modalOpen" class="admin-modal-overlay">
      <div class="admin-modal">
        <h3 class="admin-modal-title">{{ editingId == null ? '新建账号' : `编辑 #${editingId}` }}</h3>
        <p v-if="formErr" class="admin-error">{{ formErr }}</p>
        <div class="admin-form-grid">
          <label v-if="editingId == null">登录名* <input v-model="form.username" class="admin-input" autocomplete="off" /></label>
          <label v-if="editingId == null">初始密码* <input v-model="form.password" type="password" class="admin-input" autocomplete="new-password" /></label>
          <label>显示名 <input v-model="form.displayName" class="admin-input" /></label>
          <label>手机 <input v-model="form.mobile" class="admin-input" /></label>
          <label>角色
            <select v-model="form.role" class="admin-input">
              <option v-for="o in ROLE_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>状态
            <select v-model="form.status" class="admin-input">
              <option value="1">启用</option>
              <option value="0">禁用</option>
            </select>
          </label>
        </div>
        <div class="admin-modal-actions">
          <button type="button" class="admin-btn" @click="closeModal">取消</button>
          <button type="button" class="admin-btn admin-btn-primary" :disabled="saving" @click="save">保存</button>
        </div>
      </div>
    </div>

    <div v-if="pwdModal" class="admin-modal-overlay">
      <div class="admin-modal">
        <h3 class="admin-modal-title">重置密码</h3>
        <p v-if="pwdErr" class="admin-error">{{ pwdErr }}</p>
        <label class="admin-block-label">新密码（至少 6 位）
          <input v-model="pwdForm.newPassword" type="password" class="admin-input" autocomplete="new-password" />
        </label>
        <div class="admin-modal-actions">
          <button type="button" class="admin-btn" @click="closePwd">取消</button>
          <button type="button" class="admin-btn admin-btn-primary" @click="savePwd">确定</button>
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
.admin-block-label {
  display: block;
  margin-bottom: 1rem;
}
.admin-block-label .admin-input {
  width: 100%;
  margin-top: 0.35rem;
}
</style>
