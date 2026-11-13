<script setup>
import { ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { adminPageUsers, adminSetUserCredits } from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt } from '../lib/format.js';

const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const data = ref({ records: [], total: 0 });
const err = ref('');
const loading = ref(false);

const creditsOpen = ref(false);
const creditsUser = ref(null);
const creditsInput = ref('');
const creditsErr = ref('');
const creditsSaving = ref(false);

async function load() {
  err.value = '';
  loading.value = true;
  try {
    data.value = await adminPageUsers(page.value, pageSize.value, keyword.value.trim() || undefined);
  } catch (e) {
    err.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

watch([page, pageSize], load, { immediate: true });

function search() {
  page.value = 1;
  load();
}

function openCredits(row) {
  creditsUser.value = row;
  creditsInput.value = String(row.totalCredits ?? 0);
  creditsErr.value = '';
  creditsOpen.value = true;
}

function closeCredits() {
  creditsOpen.value = false;
  creditsUser.value = null;
}

function registerSourceLabel(code) {
  if (!code) return '—';
  if (code === 'web') return '网页';
  if (code === 'mp_weixin') return '小程序';
  if (code === 'app') return 'App';
  return code;
}

async function saveCredits() {
  creditsErr.value = '';
  const u = creditsUser.value;
  if (!u) return;
  const n = Number.parseInt(String(creditsInput.value).trim(), 10);
  if (!Number.isFinite(n) || n < 0) {
    creditsErr.value = '请输入有效的非负整数';
    return;
  }
  const frozen = u.frozenCredits ?? 0;
  if (n < frozen) {
    creditsErr.value = `目标点数不能小于冻结点数（${frozen}）`;
    return;
  }
  creditsSaving.value = true;
  try {
    await adminSetUserCredits(u.id, n);
    closeCredits();
    await load();
  } catch (e) {
    creditsErr.value = e.message || '保存失败';
  } finally {
    creditsSaving.value = false;
  }
}
</script>

<template>
  <div class="admin-page">
    <h2 class="admin-page-title">用户列表</h2>
    <div class="admin-toolbar">
      <input v-model="keyword" class="admin-input admin-input-inline" placeholder="昵称 / 手机 / 邮箱 / 用户ID" @keyup.enter="search" />
      <button type="button" class="admin-btn" @click="search">搜索</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>昵称</th>
            <th>手机</th>
            <th>状态</th>
            <th>来源</th>
            <th>点数</th>
            <th>冻结</th>
            <th>注册时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in data.records" :key="u.id">
            <td>{{ u.id }}</td>
            <td>{{ u.nickname || '—' }}</td>
            <td>{{ u.mobile || '—' }}</td>
            <td>{{ u.status === 1 ? '正常' : '禁用' }}</td>
            <td>{{ registerSourceLabel(u.registerSource) }}</td>
            <td>{{ u.totalCredits ?? 0 }}</td>
            <td>{{ u.frozenCredits ?? 0 }}</td>
            <td>{{ fmtDt(u.createdAt) }}</td>
            <td class="admin-actions">
              <button type="button" class="admin-link admin-link-btn" @click="openCredits(u)">调点数</button>
              <RouterLink class="admin-link" :to="{ path: '/tasks', query: { userId: u.id } }">任务</RouterLink>
              <RouterLink class="admin-link" :to="{ path: '/orders', query: { userId: u.id } }">订单</RouterLink>
              <RouterLink class="admin-link" :to="{ path: '/credit-records', query: { userId: u.id } }">点数</RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && !data.records?.length" class="admin-empty">暂无数据</p>
    </div>
    <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />

    <div v-if="creditsOpen && creditsUser" class="admin-modal-overlay" @click.self="closeCredits">
      <div class="admin-modal" role="dialog" aria-modal="true" @click.stop>
        <h3 class="admin-modal-title">调整点数 — {{ creditsUser.nickname || creditsUser.id }}</h3>
        <p class="admin-muted">用户 ID：{{ creditsUser.id }}</p>
        <p v-if="creditsErr" class="admin-error">{{ creditsErr }}</p>
        <label class="admin-block-label">目标总点数（当前可用：{{ creditsUser.totalCredits ?? 0 }}，冻结：{{ creditsUser.frozenCredits ?? 0 }}）
          <input v-model="creditsInput" type="number" min="0" class="admin-input" inputmode="numeric" />
        </label>
        <p class="admin-muted" style="font-size: 0.85rem;">扣减点数时仅更新钱包；增加点数会写入一条「点数记录」便于追溯。</p>
        <div class="admin-modal-actions">
          <button type="button" class="admin-btn" @click="closeCredits">取消</button>
          <button type="button" class="admin-btn admin-btn-primary" :disabled="creditsSaving" @click="saveCredits">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-muted {
  color: var(--admin-muted, #94a3b8);
  margin: 0.35rem 0 0.75rem;
  font-size: 0.9rem;
}
.admin-block-label {
  display: block;
  margin-bottom: 1rem;
}
.admin-block-label .admin-input {
  width: 100%;
  margin-top: 0.35rem;
}
.admin-link-btn {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  font: inherit;
}
</style>
