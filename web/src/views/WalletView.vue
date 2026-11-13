<script setup>
import { computed, onMounted, ref } from 'vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { useT } from '../lib/useT.js';
import { i18n } from '../lib/i18n.js';
import { userState, refreshCredits } from '../lib/userStore.js';

const PAGE_SIZE = 10;
const { t, locale } = useT();

const records = ref([]);
const page = ref(1);
const pages = ref(1);
const loading = ref(false);
const failed = ref(false);

const balance = computed(() => userState.info?.totalCredits ?? 0);

function statusTag(s) {
  if (s === 1) return 'tag--success';
  if (s === 2) return 'tag--error';
  return 'tag--pending';
}

function formatTime(d) {
  if (!d) return '-';
  void locale.value;
  return new Date(d).toLocaleString(i18n.getLocale() === 'en' ? 'en-US' : 'zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

async function load(p = 1) {
  loading.value = true;
  failed.value = false;
  try {
    const data = await api.getMyOrders(p, PAGE_SIZE);
    records.value = data.records || [];
    page.value = p;
    pages.value = data.pages || 1;
  } catch {
    failed.value = true;
    records.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (!auth.isLoggedIn()) return;
  refreshCredits();
  load(1);
});
</script>

<template>
  <div class="page wl">
    <header class="page-head">
      <h1 class="page-title">{{ t('walletTitle') }}</h1>
    </header>

    <div v-if="!auth.isLoggedIn()" class="wl__login">
      <span class="wl__login-icon"><Icon name="wallet" :size="26" /></span>
      <p v-html="t('loginToViewWallet')"></p>
      <RouterLink :to="'/login?return=' + encodeURIComponent('/wallet')" class="btn btn-primary">{{ t('login') }}</RouterLink>
    </div>

    <template v-else>
      <section class="wl__hero">
        <div>
          <p class="wl__label">{{ t('currentCredits') }}</p>
          <p class="wl__balance"><Icon name="bolt" :size="26" />{{ balance }}</p>
        </div>
        <div class="wl__actions">
          <RouterLink to="/recharge" class="btn btn-primary"><Icon name="plus" :size="16" />{{ t('goRecharge') }}</RouterLink>
          <RouterLink to="/invite" class="btn btn-secondary"><Icon name="gift" :size="16" />{{ t('navInvite') }}</RouterLink>
          <RouterLink to="/works" class="btn btn-ghost"><Icon name="images" :size="16" />{{ t('uiNavWorks') }}</RouterLink>
        </div>
      </section>

      <section class="wl__records">
        <h2 class="section-title">{{ t('rechargeRecords') }}</h2>
        <div v-if="loading && !records.length" class="wl__list">
          <div v-for="i in 3" :key="i" class="skeleton" style="height: 58px; border-radius: 10px"></div>
        </div>
        <p v-else-if="failed" class="empty-hint">{{ t('loadFail') }}</p>
        <p v-else-if="!records.length" class="empty-hint">{{ t('noRecords') }}</p>
        <div v-else class="wl__list">
          <div v-for="r in records" :key="r.orderNo || r.id" class="wl__row">
            <span class="wl__row-icon"><Icon name="card" :size="18" /></span>
            <div class="wl__row-main">
              <div class="wl__row-title">{{ r.description || t('navRecharge') }}</div>
              <div class="wl__row-meta">{{ formatTime(r.createdAt) }} · {{ t('recordOrderNo') }} {{ r.orderNo || '' }}</div>
            </div>
            <div class="wl__row-side">
              <div class="wl__row-credits">+{{ r.creditsBought ?? 0 }}</div>
              <span class="tag" :class="statusTag(r.status)">{{ r.statusText || '' }}</span>
            </div>
          </div>
        </div>
        <div v-if="pages > 1" class="wl__pager">
          <button type="button" class="btn btn-secondary btn-sm" :disabled="page <= 1 || loading" @click="load(page - 1)">
            <Icon name="chevronLeft" :size="15" />{{ t('prevPage') }}
          </button>
          <span>{{ page }} / {{ pages }}</span>
          <button type="button" class="btn btn-secondary btn-sm" :disabled="page >= pages || loading" @click="load(page + 1)">
            {{ t('nextPage') }}<Icon name="chevronRight" :size="15" />
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.wl {
  max-width: 860px;
}
.wl__login {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 56px 20px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
  text-align: center;
  color: var(--text-2);
}
.wl__login-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: var(--brand-soft);
  color: var(--brand);
}
.wl__login p {
  margin: 0;
}
.wl__hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 26px 28px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(108, 76, 246, 0.3);
  background:
    radial-gradient(120% 140% at 100% 0%, rgba(108, 76, 246, 0.16), transparent 55%),
    var(--panel);
}
.wl__label {
  margin: 0;
  font-size: 0.85rem;
  color: var(--text-2);
}
.wl__balance {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 0;
  font-size: 2.6rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.wl__balance :deep(svg) {
  color: var(--brand);
}
.wl__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.wl__records {
  margin-top: 28px;
}
.wl__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.wl__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--line);
}
.wl__row-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--panel-2);
  color: var(--text-2);
  flex-shrink: 0;
}
.wl__row-main {
  flex: 1;
  min-width: 0;
}
.wl__row-title {
  font-size: 0.9rem;
  font-weight: 500;
}
.wl__row-meta {
  margin-top: 2px;
  font-size: 0.75rem;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wl__row-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.wl__row-credits {
  font-weight: 700;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}
.wl__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
  font-size: 0.85rem;
  color: var(--text-3);
}
@media (max-width: 560px) {
  .wl__hero {
    padding: 20px;
  }
  .wl__balance {
    font-size: 2.1rem;
  }
}
</style>
