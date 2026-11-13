<script setup>
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { adminPageCreditRecords } from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt, creditRecordStatus } from '../lib/format.js';

const route = useRoute();
const page = ref(1);
const pageSize = ref(20);
const userId = ref(route.query.userId ? String(route.query.userId) : '');
const data = ref({ records: [], total: 0 });
const err = ref('');

async function load() {
  err.value = '';
  try {
    const uid = userId.value.trim() || undefined;
    data.value = await adminPageCreditRecords(page.value, pageSize.value, uid);
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
  }
);

function applyFilter() {
  page.value = 1;
  load();
}
</script>

<template>
  <div class="admin-page">
    <h2 class="admin-page-title">用户点数记录</h2>
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
            <th>用户</th>
            <th>获得</th>
            <th>剩余</th>
            <th>状态</th>
            <th>过期时间</th>
            <th>创建</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="r.id">
            <td>{{ r.id }}</td>
            <td>{{ r.userId }}</td>
            <td>{{ r.credits }}</td>
            <td>{{ r.creditsRemaining }}</td>
            <td>{{ creditRecordStatus(r.status) }}</td>
            <td>{{ fmtDt(r.expireAt) }}</td>
            <td>{{ fmtDt(r.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!data.records?.length" class="admin-empty">暂无数据</p>
    </div>
    <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />
  </div>
</template>
