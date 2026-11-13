<script setup>
import { computed, onMounted, ref } from 'vue';
import { adminRegisterGift, adminUpdateRegisterGift } from '../lib/api.js';

const data = ref(null);
const perUserCredits = ref('1');
const dailyTotalCredits = ref('20');
const err = ref('');
const ok = ref('');
const loading = ref(false);
const saving = ref(false);

const remainingPeople = computed(() => {
  const per = Number.parseInt(data.value?.perUserCredits, 10) || 0;
  const remain = data.value?.todayRemainingCredits ?? 0;
  if (per <= 0) return 0;
  return Math.floor(remain / per);
});

async function load() {
  loading.value = true;
  err.value = '';
  try {
    const vo = await adminRegisterGift();
    data.value = vo;
    perUserCredits.value = String(vo.perUserCredits ?? 1);
    dailyTotalCredits.value = String(vo.dailyTotalCredits ?? 20);
  } catch (e) {
    err.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

async function save() {
  err.value = '';
  ok.value = '';
  const per = Number.parseInt(perUserCredits.value, 10);
  const daily = Number.parseInt(dailyTotalCredits.value, 10);
  if (!Number.isFinite(per) || per < 0) {
    err.value = '每人赠送点数须为不小于 0 的整数';
    return;
  }
  if (!Number.isFinite(daily) || daily < 0) {
    err.value = '每天赠送总额度须为不小于 0 的整数';
    return;
  }
  saving.value = true;
  try {
    const vo = await adminUpdateRegisterGift({ perUserCredits: per, dailyTotalCredits: daily });
    data.value = vo;
    perUserCredits.value = String(vo.perUserCredits ?? per);
    dailyTotalCredits.value = String(vo.dailyTotalCredits ?? daily);
    ok.value = '已保存';
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
      <h2 class="admin-page-title">注册赠送</h2>
    </div>
    <p class="admin-page-lead">
      每天总额度用完后，当天再注册的用户不再获得点数。按北京时间切日。
    </p>

    <p v-if="err" class="admin-error">{{ err }}</p>
    <p v-if="ok" class="admin-note">{{ ok }}</p>

    <div v-if="data" class="admin-gift-stats">
      <div class="admin-gift-stat">
        <div class="admin-gift-stat-label">今日已赠送</div>
        <div class="admin-gift-stat-value">{{ data.todayUsedCredits }} / {{ data.dailyTotalCredits }}</div>
      </div>
      <div class="admin-gift-stat">
        <div class="admin-gift-stat-label">今日剩余</div>
        <div class="admin-gift-stat-value">{{ data.todayRemainingCredits }}</div>
      </div>
      <div class="admin-gift-stat">
        <div class="admin-gift-stat-label">约还可赠人数</div>
        <div class="admin-gift-stat-value">{{ remainingPeople }}</div>
      </div>
    </div>
    <p v-else-if="loading" class="admin-hint">加载中…</p>

    <form class="admin-pass-form" @submit.prevent="save">
      <label class="admin-label">每人注册赠送点数</label>
      <input v-model="perUserCredits" class="admin-input" type="number" min="0" step="1" inputmode="numeric" />

      <label class="admin-label">每天赠送总额度</label>
      <input v-model="dailyTotalCredits" class="admin-input" type="number" min="0" step="1" inputmode="numeric" />

      <p class="admin-hint">例如总额度 20、每人 1 点，第 21 个注册用户将拿不到点数。</p>

      <button type="submit" class="admin-btn admin-btn-primary admin-pass-submit" :disabled="saving || loading">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </form>
  </div>
</template>
