<script setup>
import { ref, watch } from 'vue';
import { adminPageFeedback } from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt } from '../lib/format.js';

const page = ref(1);
const pageSize = ref(20);
const keyword = ref('');
const userIdFilter = ref('');
const data = ref({ records: [], total: 0 });
const err = ref('');
const loading = ref(false);

const CAT_LABEL = {
  tryon: '试衣/换装',
  local_edit: '局部改款',
  model_shot: '模特出图',
  clothing_suite: '服装出图',
  billing: '充值/点数',
  account: '账号/登录',
  other: '其他',
};

function categoryLabel(code) {
  return CAT_LABEL[code] || code || '—';
}

function clip(s, n) {
  if (s == null || s === '') return '—';
  const t = String(s);
  return t.length <= n ? t : t.slice(0, n) + '…';
}

async function load() {
  err.value = '';
  loading.value = true;
  let uid;
  const raw = userIdFilter.value.trim();
  if (raw !== '') {
    const n = Number.parseInt(raw, 10);
    uid = Number.isFinite(n) ? n : undefined;
  }
  try {
    data.value = await adminPageFeedback(page.value, pageSize.value, keyword.value.trim() || undefined, uid);
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
</script>

<template>
  <div class="admin-page">
    <h2 class="admin-page-title">问题反馈</h2>
    <div class="admin-toolbar">
      <input
        v-model="keyword"
        class="admin-input admin-input-inline"
        placeholder="内容 / 联系方式 / 路径 / 反馈ID / 用户ID"
        @keyup.enter="search"
      />
      <input
        v-model="userIdFilter"
        class="admin-input admin-input-inline"
        style="max-width: 140px"
        placeholder="用户ID"
        @keyup.enter="search"
      />
      <button type="button" class="admin-btn" @click="search">搜索</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户</th>
            <th>类型</th>
            <th>内容</th>
            <th>联系方式</th>
            <th>页面</th>
            <th>IP</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in data.records" :key="row.id">
            <td>{{ row.id }}</td>
            <td>
              <span v-if="row.userId">{{ row.userId }}</span>
              <span v-else class="admin-muted">访客</span>
              <div v-if="row.userNickname" class="admin-subtle">{{ row.userNickname }}</div>
            </td>
            <td>{{ categoryLabel(row.category) }}</td>
            <td :title="row.content">{{ clip(row.content, 120) }}</td>
            <td>{{ row.contact || '—' }}</td>
            <td :title="row.pagePath || ''">{{ clip(row.pagePath, 40) }}</td>
            <td>{{ row.clientIp || '—' }}</td>
            <td>{{ fmtDt(row.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading && !data.records?.length" class="admin-empty">暂无数据</p>
      <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />
    </div>
  </div>
</template>

<style scoped>
.admin-subtle {
  font-size: 0.8rem;
  color: var(--admin-muted, #64748b);
}
.admin-muted {
  color: var(--admin-muted, #64748b);
}
</style>
