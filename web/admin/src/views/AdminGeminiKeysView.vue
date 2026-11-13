<script setup>
import { onMounted, ref } from 'vue';
import {
  adminClearGeminiCooldown,
  adminCreateGeminiKey,
  adminDeleteGeminiKey,
  adminGeminiKeys,
  adminGeminiStrategy,
  adminProbeGeminiKey,
  adminUpdateGeminiKey,
} from '../lib/api.js';

const STRATEGIES = [
  { id: 'ROUND_ROBIN', label: '轮询', hint: '按顺序轮流使用' },
  { id: 'FAILOVER', label: '故障转移', hint: '优先用排序最小的，失败再换' },
  { id: 'RANDOM', label: '随机', hint: '每次随机挑一把' },
];

const data = ref(null);
const err = ref('');
const loading = ref(false);
const modalOpen = ref(false);
const editingId = ref(null);
const form = ref(emptyForm());
const formErr = ref('');
const saving = ref(false);
const probeMsg = ref('');

function emptyForm() {
  return { name: '', apiKey: '', enabled: true, sortOrder: '0' };
}

async function load() {
  loading.value = true;
  err.value = '';
  try {
    data.value = await adminGeminiKeys();
  } catch (e) {
    err.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

async function setStrategy(id) {
  if (!data.value || data.value.strategy === id) return;
  try {
    await adminGeminiStrategy(id);
    data.value.strategy = id;
  } catch (e) {
    err.value = e.message || '保存策略失败';
  }
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
    name: row.name || '',
    apiKey: '',
    enabled: !!row.enabled,
    sortOrder: String(row.sortOrder ?? 0),
  };
  formErr.value = '';
  modalOpen.value = true;
}

async function save() {
  formErr.value = '';
  const name = form.value.name.trim();
  if (!name) {
    formErr.value = '请填写名称';
    return;
  }
  const body = {
    name,
    enabled: !!form.value.enabled,
    sortOrder: Number.parseInt(form.value.sortOrder, 10) || 0,
  };
  if (form.value.apiKey.trim()) body.apiKey = form.value.apiKey.trim();
  else if (editingId.value == null) {
    formErr.value = '请填写 API Key';
    return;
  }
  saving.value = true;
  try {
    if (editingId.value == null) await adminCreateGeminiKey(body);
    else await adminUpdateGeminiKey(editingId.value, body);
    modalOpen.value = false;
    await load();
  } catch (e) {
    formErr.value = e.message || '保存失败';
  } finally {
    saving.value = false;
  }
}

async function remove(row) {
  if (!window.confirm(`删除「${row.name}」？`)) return;
  try {
    await adminDeleteGeminiKey(row.id);
    await load();
  } catch (e) {
    err.value = e.message || '删除失败';
  }
}

async function probe(row) {
  probeMsg.value = '';
  try {
    const r = await adminProbeGeminiKey(row.id);
    probeMsg.value = `${row.name}：${r.message}`;
    await load();
  } catch (e) {
    probeMsg.value = e.message || '检测失败';
  }
}

async function clearCool(row) {
  try {
    await adminClearGeminiCooldown(row.id);
    await load();
  } catch (e) {
    err.value = e.message || '操作失败';
  }
}

onMounted(load);
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <h2 class="admin-page-title">Gemini Key</h2>
      <button type="button" class="admin-btn admin-btn-primary" @click="openCreate">添加 Key</button>
    </div>
    <p class="admin-page-lead">多把 Key 按策略调用。额度用尽、Key 无效或 429 时自动换下一把。</p>
    <p v-if="data" class="admin-note">{{ data.balanceNote }}</p>
    <p v-if="data?.yamlFallback" class="admin-note">
      还没有启用的数据库 Key，当前仍使用配置文件里的 Key。添加并启用后，将只走这里的密钥池。
    </p>
    <p v-if="err" class="admin-error">{{ err }}</p>
    <p v-if="probeMsg" class="admin-page-lead">{{ probeMsg }}</p>

    <div v-if="data" class="admin-seg">
      <button
        v-for="s in STRATEGIES"
        :key="s.id"
        type="button"
        class="admin-btn"
        :class="{ 'admin-btn-primary': data.strategy === s.id }"
        :title="s.hint"
        @click="setStrategy(s.id)"
      >
        {{ s.label }}
      </button>
    </div>

    <div class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>名称</th>
            <th>Key</th>
            <th>状态</th>
            <th>排序</th>
            <th>成功 / 失败</th>
            <th>最近错误</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="admin-empty">加载中…</td>
          </tr>
          <tr v-else-if="!data?.keys?.length">
            <td colspan="7" class="admin-empty">还没有 Key</td>
          </tr>
          <tr v-for="row in data?.keys || []" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="mono">{{ row.keyMask }}</td>
            <td>
              <span class="admin-pill" :class="row.enabled ? 'admin-pill--ok' : 'admin-pill--off'">
                {{ row.enabled ? '启用' : '停用' }}
              </span>
              <span v-if="row.cooldownUntil" class="admin-pill admin-pill--off">冷却至 {{ row.cooldownUntil }}</span>
            </td>
            <td>{{ row.sortOrder }}</td>
            <td>{{ row.successCount }} / {{ row.failCount }}</td>
            <td>{{ row.lastError || '—' }}</td>
            <td class="admin-actions">
              <button type="button" class="admin-link-btn" @click="probe(row)">检测</button>
              <button type="button" class="admin-link-btn" @click="openEdit(row)">编辑</button>
              <button v-if="row.cooldownUntil" type="button" class="admin-link-btn" @click="clearCool(row)">解除冷却</button>
              <button type="button" class="admin-link-btn danger" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="modalOpen" class="admin-modal-overlay" @click.self="modalOpen = false">
      <div class="admin-modal" style="max-width: 460px">
        <h3 class="admin-modal-title">{{ editingId == null ? '添加 Key' : '编辑 Key' }}</h3>
        <div class="admin-form">
          <label class="admin-label">名称</label>
          <input v-model="form.name" class="admin-input" maxlength="64" placeholder="例如 主 Key / 备用" />
          <label class="admin-label">API Key</label>
          <input
            v-model="form.apiKey"
            class="admin-input"
            type="password"
            autocomplete="off"
            :placeholder="editingId == null ? 'AIza…' : '留空则不修改'"
          />
          <label class="admin-label">排序（故障转移时小的优先）</label>
          <input v-model="form.sortOrder" class="admin-input" inputmode="numeric" />
          <label class="admin-label">
            <input v-model="form.enabled" type="checkbox" />
            启用
          </label>
          <p v-if="formErr" class="admin-error">{{ formErr }}</p>
          <div class="admin-modal-actions">
            <button type="button" class="admin-btn" @click="modalOpen = false">取消</button>
            <button type="button" class="admin-btn admin-btn-primary" :disabled="saving" @click="save">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
