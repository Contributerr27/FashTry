<script setup>
import { onMounted, ref } from 'vue';
import { adminRegisterAbuse, adminUpdateRegisterAbuse } from '../lib/api.js';

const ipWindowMinutes = ref('10');
const ipDenyFrom = ref('3');
const prefixWindowMinutes = ref('60');
const prefixDenyFrom = ref('3');
const globalWindowMinutes = ref('10');
const globalAllow = ref('8');
const smsWindowMinutes = ref('60');
const smsMaxPerIp = ref('5');
const err = ref('');
const ok = ref('');
const loading = ref(false);
const saving = ref(false);

function apply(vo) {
  ipWindowMinutes.value = String(vo.ipWindowMinutes ?? 10);
  ipDenyFrom.value = String(vo.ipDenyFrom ?? 3);
  prefixWindowMinutes.value = String(vo.prefixWindowMinutes ?? 60);
  prefixDenyFrom.value = String(vo.prefixDenyFrom ?? 3);
  globalWindowMinutes.value = String(vo.globalWindowMinutes ?? 10);
  globalAllow.value = String(vo.globalAllow ?? 8);
  smsWindowMinutes.value = String(vo.smsWindowMinutes ?? 60);
  smsMaxPerIp.value = String(vo.smsMaxPerIp ?? 5);
}

async function load() {
  loading.value = true;
  err.value = '';
  try {
    apply(await adminRegisterAbuse());
  } catch (e) {
    err.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

function parseIntField(raw, label, min, max) {
  const text = String(raw).trim();
  const n = Number.parseInt(text, 10);
  if (text === '' || String(n) !== text || n < min || n > max) {
    return { error: `${label}须为 ${min} 到 ${max} 的整数` };
  }
  return { value: n };
}

async function save() {
  err.value = '';
  ok.value = '';
  const fields = [
    parseIntField(ipWindowMinutes.value, '同一 IP 统计窗口', 1, 10080),
    parseIntField(ipDenyFrom.value, '同一 IP 从第几个起不送', 0, 100000),
    parseIntField(prefixWindowMinutes.value, '号段统计窗口', 1, 10080),
    parseIntField(prefixDenyFrom.value, '号段从第几个起不送', 0, 100000),
    parseIntField(globalWindowMinutes.value, '全站统计窗口', 1, 10080),
    parseIntField(globalAllow.value, '全站前几个仍送', 0, 100000),
    parseIntField(smsWindowMinutes.value, '短信统计窗口', 1, 10080),
    parseIntField(smsMaxPerIp.value, '同一 IP 短信条数', 0, 100000),
  ];
  const bad = fields.find((f) => f.error);
  if (bad) {
    err.value = bad.error;
    return;
  }
  const [ipWindow, ipDeny, prefixWindow, prefixDeny, globalWindow, globalKeep, smsWindow, smsMax] = fields.map((f) => f.value);
  saving.value = true;
  try {
    const vo = await adminUpdateRegisterAbuse({
      ipWindowMinutes: ipWindow,
      ipDenyFrom: ipDeny,
      prefixWindowMinutes: prefixWindow,
      prefixDenyFrom: prefixDeny,
      globalWindowMinutes: globalWindow,
      globalAllow: globalKeep,
      smsWindowMinutes: smsWindow,
      smsMaxPerIp: smsMax,
    });
    apply(vo);
    ok.value = '已保存，立即生效';
  } catch (e) {
    err.value = e.message || '保存失败';
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="admin-page">
    <div class="admin-page-head">
      <h2 class="admin-page-title">注册风控</h2>
    </div>
    <p class="admin-page-lead">
      注册仍然成功。命中任一条时不送试用点，接口不说明原因。次数填 0 表示关闭该条。保存后立即生效。
    </p>

    <p v-if="err" class="admin-error">{{ err }}</p>
    <p v-if="ok" class="admin-note">{{ ok }}</p>
    <p v-if="loading" class="admin-hint">加载中…</p>

    <form class="admin-pass-form admin-abuse-form" @submit.prevent="save">
      <h3 class="admin-form-section">同一 IP</h3>
      <label class="admin-label">统计窗口（分钟）</label>
      <input v-model="ipWindowMinutes" class="admin-input" type="number" min="1" step="1" inputmode="numeric" />
      <label class="admin-label">从第几个新账号起不送</label>
      <input v-model="ipDenyFrom" class="admin-input" type="number" min="0" step="1" inputmode="numeric" />
      <p class="admin-hint">默认 10 分钟内从第 3 个起不送。填 0 关闭。</p>

      <h3 class="admin-form-section">大陆手机号前 7 位</h3>
      <label class="admin-label">统计窗口（分钟）</label>
      <input v-model="prefixWindowMinutes" class="admin-input" type="number" min="1" step="1" inputmode="numeric" />
      <label class="admin-label">从第几个起不送</label>
      <input v-model="prefixDenyFrom" class="admin-input" type="number" min="0" step="1" inputmode="numeric" />
      <p class="admin-hint">例如 19054293560 与 19054293530 同属 1905429。默认 60 分钟内从第 3 个起不送。填 0 关闭。</p>

      <h3 class="admin-form-section">全站</h3>
      <label class="admin-label">统计窗口（分钟）</label>
      <input v-model="globalWindowMinutes" class="admin-input" type="number" min="1" step="1" inputmode="numeric" />
      <label class="admin-label">前几个仍送</label>
      <input v-model="globalAllow" class="admin-input" type="number" min="0" step="1" inputmode="numeric" />
      <p class="admin-hint">默认 10 分钟内前 8 个仍送，从第 9 个起不送。手机、邮箱、Google 都计入。填 0 关闭。</p>

      <h3 class="admin-form-section">短信</h3>
      <label class="admin-label">统计窗口（分钟）</label>
      <input v-model="smsWindowMinutes" class="admin-input" type="number" min="1" step="1" inputmode="numeric" />
      <label class="admin-label">同一 IP 最多条数</label>
      <input v-model="smsMaxPerIp" class="admin-input" type="number" min="0" step="1" inputmode="numeric" />
      <p class="admin-hint">默认 60 分钟内最多 5 条。同一手机号 60 秒内重复点击不占次数。填 0 关闭。</p>

      <button type="submit" class="admin-btn admin-btn-primary admin-pass-submit" :disabled="saving || loading">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </form>
  </div>
</template>
