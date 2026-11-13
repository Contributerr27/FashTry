<script setup>
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { adminPageOrders } from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt, orderStatusText, payChannelText, currencyText } from '../lib/format.js';

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
    data.value = await adminPageOrders(page.value, pageSize.value, uid);
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
    <h2 class="admin-page-title">充值订单</h2>
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
            <th>金额</th>
            <th>渠道</th>
            <th>状态</th>
            <th>套餐</th>
            <th>创建</th>
            <th>支付时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="r.id">
            <td>{{ r.id }}</td>
            <td>{{ r.userId }}</td>
            <td>{{ r.amount }}{{ r.currency != null && r.currency !== '' ? ' ' + currencyText(r.currency) : '' }}</td>
            <td>{{ payChannelText(r.payChannel) }}</td>
            <td>{{ orderStatusText(r.status) }}</td>
            <td>{{ r.packageId ?? '—' }}</td>
            <td>{{ fmtDt(r.createdAt) }}</td>
            <td>{{ fmtDt(r.paidAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!data.records?.length" class="admin-empty">暂无数据</p>
    </div>
    <AdminPagination v-if="data.total > 0" v-model:page="page" :page-size="pageSize" :total="data.total" />
  </div>
</template>
