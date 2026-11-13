<script setup>
import { ref, watch, computed } from 'vue';
import {
  adminPagePackages,
  adminCreatePackage,
  adminUpdatePackage,
  adminDeletePackage,
} from '../lib/api.js';
import AdminPagination from '../components/AdminPagination.vue';
import { fmtDt } from '../lib/format.js';
import { packageCodeOptionsForForm } from '../lib/adminEnums.js';

const page = ref(1);
const pageSize = ref(50);
const data = ref({ records: [], total: 0 });
const err = ref('');

const modalOpen = ref(false);
const editingId = ref(null);
const form = ref(emptyForm());
const formErr = ref('');
const saving = ref(false);

const packageCodeSelectOptions = computed(() => packageCodeOptionsForForm(form.value.code));

function emptyForm() {
  return {
    code: '',
    name: '',
    nameEn: '',
    price: '',
    priceUsd: '',
    credits: '',
    unitPrice: '',
    cost: '',
    firstPurchaseOnly: false,
    expirationDays: '',
    startTime: '',
    endTime: '',
    sortOrder: '0',
    active: true,
    strategyTag: '',
    strategyTagEn: '',
  };
}

function dec(s) {
  if (s === '' || s == null) return undefined;
  const n = Number(s);
  return Number.isFinite(n) ? n : undefined;
}

function intOrUndef(s) {
  if (s === '' || s == null) return undefined;
  const n = parseInt(s, 10);
  return Number.isFinite(n) ? n : undefined;
}

function toPayload() {
  const f = form.value;
  const p = {
    code: f.code.trim(),
    name: f.name.trim(),
    nameEn: f.nameEn.trim() || undefined,
    price: dec(f.price),
    priceUsd: dec(f.priceUsd),
    credits: intOrUndef(f.credits),
    unitPrice: dec(f.unitPrice),
    cost: dec(f.cost),
    firstPurchaseOnly: !!f.firstPurchaseOnly,
    expirationDays: intOrUndef(f.expirationDays),
    startTime: f.startTime ? `${f.startTime}:00` : undefined,
    endTime: f.endTime ? `${f.endTime}:00` : undefined,
    sortOrder: intOrUndef(f.sortOrder) ?? 0,
    active: !!f.active,
    strategyTag: f.strategyTag.trim() || undefined,
    strategyTagEn: f.strategyTagEn.trim() || undefined,
  };
  if (!f.code.trim()) throw new Error('请选择套餐代号');
  if (p.credits == null) throw new Error('请填写获得点数');
  return p;
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
    code: row.code ?? '',
    name: row.name ?? '',
    nameEn: row.nameEn ?? '',
    price: row.price != null ? String(row.price) : '',
    priceUsd: row.priceUsd != null ? String(row.priceUsd) : '',
    credits: row.credits != null ? String(row.credits) : '',
    unitPrice: row.unitPrice != null ? String(row.unitPrice) : '',
    cost: row.cost != null ? String(row.cost) : '',
    firstPurchaseOnly: !!row.firstPurchaseOnly,
    expirationDays: row.expirationDays != null ? String(row.expirationDays) : '',
    startTime: row.startTime ? String(row.startTime).slice(0, 16) : '',
    endTime: row.endTime ? String(row.endTime).slice(0, 16) : '',
    sortOrder: row.sortOrder != null ? String(row.sortOrder) : '0',
    active: !!row.active,
    strategyTag: row.strategyTag ?? '',
    strategyTagEn: row.strategyTagEn ?? '',
  };
  formErr.value = '';
  modalOpen.value = true;
}

function closeModal() {
  modalOpen.value = false;
}

async function save() {
  formErr.value = '';
  saving.value = true;
  try {
    const body = toPayload();
    if (editingId.value == null) {
      await adminCreatePackage(body);
    } else {
      await adminUpdatePackage(editingId.value, body);
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
  if (!confirm(`删除套餐 #${row.id} ${row.code}？`)) return;
  try {
    await adminDeletePackage(row.id);
    await load();
  } catch (e) {
    err.value = e.message || '删除失败';
  }
}

async function load() {
  err.value = '';
  try {
    data.value = await adminPagePackages(page.value, pageSize.value);
  } catch (e) {
    err.value = e.message || '加载失败';
  }
}

watch([page, pageSize], load, { immediate: true });
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <h2 class="admin-page-title">充值套餐</h2>
      <button type="button" class="admin-btn admin-btn-primary" @click="openCreate">新增套餐</button>
    </div>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <div v-else class="admin-table-wrap admin-table-scroll">
      <table class="admin-table admin-table-sm">
        <thead>
          <tr>
            <th>ID</th>
            <th>套餐代号</th>
            <th>名称</th>
            <th>点数</th>
            <th>人民币价</th>
            <th>美元价</th>
            <th>上架</th>
            <th>排序</th>
            <th>更新</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in data.records" :key="r.id">
            <td>{{ r.id }}</td>
            <td class="mono">{{ r.code }}</td>
            <td>{{ r.name }}</td>
            <td>{{ r.credits }}</td>
            <td>{{ r.price ?? '—' }}</td>
            <td>{{ r.priceUsd ?? '—' }}</td>
            <td>{{ r.active ? '是' : '否' }}</td>
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

    <div v-if="modalOpen" class="admin-modal-overlay" @click.self="closeModal">
      <div class="admin-modal">
        <h3 class="admin-modal-title">{{ editingId == null ? '新增套餐' : `编辑 #${editingId}` }}</h3>
        <p v-if="formErr" class="admin-error">{{ formErr }}</p>
        <div class="admin-form-grid">
          <label>套餐代号*
            <select v-model="form.code" class="admin-input" required>
              <option value="" disabled>{{ editingId == null ? '请选择' : '请选择套餐代号' }}</option>
              <option v-for="o in packageCodeSelectOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </label>
          <label>名称 <input v-model="form.name" class="admin-input" placeholder="中文展示名" /></label>
          <label>英文名称 <input v-model="form.nameEn" class="admin-input" placeholder="可选" /></label>
          <label>获得点数* <input v-model="form.credits" class="admin-input" type="number" min="0" /></label>
          <label>人民币价格 <input v-model="form.price" class="admin-input" placeholder="元，可选" /></label>
          <label>美元价格 <input v-model="form.priceUsd" class="admin-input" placeholder="可选" /></label>
          <label>单价 <input v-model="form.unitPrice" class="admin-input" placeholder="可选" /></label>
          <label>成本 <input v-model="form.cost" class="admin-input" placeholder="可选" /></label>
          <label>排序 <input v-model="form.sortOrder" class="admin-input" type="number" /></label>
          <label>有效天数 <input v-model="form.expirationDays" class="admin-input" placeholder="留空表示永久" /></label>
          <label>生效开始 <input v-model="form.startTime" class="admin-input" type="datetime-local" /></label>
          <label>生效结束 <input v-model="form.endTime" class="admin-input" type="datetime-local" /></label>
          <label class="admin-check"><input v-model="form.firstPurchaseOnly" type="checkbox" /> 仅限首充</label>
          <label class="admin-check"><input v-model="form.active" type="checkbox" /> 上架销售</label>
          <label class="full">策略说明（中文） <input v-model="form.strategyTag" class="admin-input" placeholder="可选，展示在套餐卡片" /></label>
          <label class="full">策略说明（英文） <input v-model="form.strategyTagEn" class="admin-input" placeholder="可选" /></label>
        </div>
        <div class="admin-modal-actions">
          <button type="button" class="admin-btn" @click="closeModal">取消</button>
          <button type="button" class="admin-btn admin-btn-primary" :disabled="saving" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
