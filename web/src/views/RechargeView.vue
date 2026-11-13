<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import QRCode from 'qrcode';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';
import { TOOLS } from '../lib/navItems.js';
import { userState, refreshCredits } from '../lib/userStore.js';

const PAY_KEY = 'fashtry_pay_method';
const METHODS = [
  { id: 'alipay', labelKey: 'alipay', icon: '/static/alipay.svg', color: '#1677ff' },
  { id: 'wxpay', labelKey: 'wechatPay', icon: '/static/wcpay.svg', color: '#07c160' },
  { id: 'usdt', labelKey: 'payUsdt', icon: '/static/usdt.svg', color: '#26a17b' },
  { id: 'paypal', labelKey: 'paypal', icon: '/static/paypal.svg', color: '#0070ba' },
];

const { t, locale } = useT();
const router = useRouter();

const packages = ref([]);
const loading = ref(true);
const loadFailed = ref(false);
const method = ref(localStorage.getItem(PAY_KEY) || (i18n.getLocale() === 'en' ? 'paypal' : 'alipay'));
const buyingId = ref(null);

const usdtPkg = ref(null);
const pay = ref(null); // { orderNo, amount, currency, hint, channel }
const payStatus = ref('pending'); // pending | paid | timeout
const payStatusText = ref('');
const payCountdown = ref('');
const qrSrc = ref('');

let pollTimer = null;
let countdownTimer = null;

const balance = computed(() => userState.info?.totalCredits ?? null);

const cards = computed(() => {
  void locale.value;
  const list = packages.value.map((p) => {
    const unit = p.price != null && p.credits ? p.price / p.credits : null;
    return { ...p, unit };
  });
  const candidates = list.filter((p) => p.unit != null && p.purchasable !== false && !p.firstPurchaseOnly);
  const best = candidates.length > 1 ? candidates.reduce((a, b) => (b.unit < a.unit ? b : a)) : null;
  return list.map((p) => ({
    ...p,
    best: best && p.id === best.id,
    priceText: p.price != null ? i18n.formatAmount(p.price, p.currency || 'CNY') : t('negotiate'),
    unitText: p.unit != null ? i18n.formatAmount(Math.round(p.unit * 1000) / 1000, p.currency || 'CNY') : '',
  }));
});

const guide = computed(() =>
  TOOLS.map((tool) => ({ key: tool.key, icon: tool.icon, title: t(tool.titleKey), cost: tool.cost, path: tool.path }))
);

watch(method, (v) => localStorage.setItem(PAY_KEY, v));

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer);
  if (countdownTimer) clearInterval(countdownTimer);
  pollTimer = null;
  countdownTimer = null;
}

function startCountdown(expireAt) {
  if (countdownTimer) clearInterval(countdownTimer);
  if (!expireAt) return;
  const end = new Date(expireAt).getTime();
  const tick = () => {
    const diff = Math.max(0, Math.floor((end - Date.now()) / 1000));
    if (diff <= 0) {
      payCountdown.value = '';
      clearInterval(countdownTimer);
      countdownTimer = null;
      return;
    }
    const m = Math.floor(diff / 60);
    const s = String(diff % 60).padStart(2, '0');
    payCountdown.value = i18n.getLocale() === 'en' ? `${m}m${s}s left` : `剩余 ${m}分${s}秒`;
  };
  tick();
  countdownTimer = setInterval(tick, 1000);
}

async function pollOrderStatus(orderId, expireAt) {
  stopPolling();
  payStatus.value = 'pending';
  payStatusText.value = t('pendingPay');
  startCountdown(expireAt);
  const doPoll = async () => {
    try {
      const res = await api.getOrderStatus(orderId);
      payStatusText.value = res.statusText || t('pendingPay');
      if (res.status === 1) {
        payStatus.value = 'paid';
        payCountdown.value = '';
        stopPolling();
        toast(t('paySuccess'), 'success');
        refreshCredits();
        loadPackages();
      } else if (res.status === 2) {
        payStatus.value = 'timeout';
        payCountdown.value = '';
        stopPolling();
        toast(t('orderTimeout'), 'error');
      } else if (res.expireAt && !countdownTimer) {
        startCountdown(res.expireAt);
      }
    } catch (e) {
      console.warn(e);
    }
  };
  await doPoll();
  pollTimer = setInterval(doPoll, 4000);
}

function formatUsdtPayAmount(n) {
  if (n == null) return '';
  const num = typeof n === 'number' ? n : parseFloat(String(n));
  if (Number.isNaN(num)) return String(n);
  return num.toFixed(8).replace(/\.?0+$/, '') || '0';
}

function channelName(ch) {
  return (
    { wxpay: t('wechatPay'), alipay: t('alipay'), usdt_tron: t('payUsdtTron'), usdt_bsc: t('payUsdtBsc'), paypal: t('paypal') }[
      ch
    ] || ch
  );
}

async function createOrder(pkg, payChannel) {
  if (!auth.isLoggedIn()) {
    toast(t('pleaseLogin'), 'error');
    router.push('/login?return=' + encodeURIComponent('/recharge'));
    return;
  }
  buyingId.value = pkg.id;
  try {
    const data = await api.createOrder({ packageId: pkg.id, payChannel });
    const url = data.codeUrl || '';
    if (/^https?:\/\//.test(url) && (payChannel === 'paypal' || payChannel.startsWith('usdt_'))) {
      window.location.href = url;
      return;
    }
    const name = channelName(payChannel);
    let amount;
    let currency = '';
    let hint;
    if (payChannel.startsWith('usdt_') && data.cryptoPayAmount != null) {
      amount = formatUsdtPayAmount(data.cryptoPayAmount);
      currency = 'USDT';
      hint = t('payHintUsdt', { amount, network: name });
    } else if (data.currency === 'USD') {
      amount = i18n.formatAmount(data.amount, 'USD');
      hint = t('payHint', { channel: name });
    } else {
      amount = data.amount;
      currency = t('yuan');
      hint = t('payHint', { channel: name });
    }
    pay.value = { orderNo: data.orderNo, amount, currency, hint, channel: payChannel, pkgName: pkg.name };
    qrSrc.value = '';
    try {
      qrSrc.value = await QRCode.toDataURL(url, { width: 220, margin: 1 });
    } catch {
      qrSrc.value = '';
    }
    await nextTick();
    await pollOrderStatus(data.orderId, data.expireAt);
  } catch (e) {
    toast(e.message || t('createOrderFail'), 'error');
  } finally {
    buyingId.value = null;
  }
}

function buy(pkg) {
  if (pkg.purchasable === false) return;
  if (method.value === 'usdt') {
    if (pkg.usdtTronAvailable === false) createOrder(pkg, 'usdt_bsc');
    else usdtPkg.value = pkg;
    return;
  }
  createOrder(pkg, method.value);
}

function pickUsdt(ch) {
  const pkg = usdtPkg.value;
  usdtPkg.value = null;
  if (pkg) createOrder(pkg, ch);
}

function closePay() {
  stopPolling();
  pay.value = null;
  loadPackages();
}

async function loadPackages() {
  loadFailed.value = false;
  try {
    const list = await api.getPackages();
    // 后端异常返回（对象/字符串）时不能让 cards 的 .map 整页崩溃
    packages.value = Array.isArray(list) ? list : [];
  } catch (e) {
    console.warn('loadPackages', e);
    loadFailed.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(loadPackages);
onUnmounted(stopPolling);
</script>

<template>
  <div class="page rc">
    <header class="page-head rc__head">
      <div>
        <h1 class="page-title">{{ t('rechargeTitle') }}</h1>
        <p class="page-lead">{{ t('rechargeDesc') }}</p>
      </div>
      <RouterLink v-if="userState.loggedIn" to="/wallet" class="rc__balance">
        <span class="rc__balance-icon"><Icon name="bolt" :size="18" /></span>
        <span>
          <small>{{ t('uiRcBalance') }}</small>
          <strong>{{ balance ?? '—' }}</strong>
        </span>
        <Icon name="chevronRight" :size="16" class="rc__balance-chev" />
      </RouterLink>
    </header>

    <section class="rc__methods">
      <span class="rc__methods-label">{{ t('uiRcPayMethod') }}</span>
      <div class="rc__method-list">
        <button
          v-for="m in METHODS"
          :key="m.id"
          type="button"
          class="rc__method"
          :class="{ on: method === m.id }"
          :style="{ '--m': m.color }"
          @click="method = m.id"
        >
          <img :src="m.icon" alt="" />
          <span>{{ t(m.labelKey) }}</span>
        </button>
      </div>
    </section>

    <div v-if="loading" class="rc__grid">
      <div v-for="i in 3" :key="i" class="rc__card skeleton" style="height: 280px"></div>
    </div>
    <p v-else-if="loadFailed" class="empty-hint">{{ t('loadPackagesFail') }}</p>
    <p v-else-if="!cards.length" class="empty-hint">{{ t('noPackages') }}</p>
    <div v-else class="rc__grid">
      <article
        v-for="p in cards"
        :key="p.id"
        class="rc__card"
        :class="{ 'rc__card--best': p.best, 'rc__card--off': p.purchasable === false }"
      >
        <div class="rc__card-tags">
          <span v-if="p.best" class="tag tag--brand">{{ t('uiRcBestValue') }}</span>
          <span v-if="p.firstPurchaseOnly" class="tag tag--pending">{{ t('firstPurchaseTag') }}</span>
        </div>
        <h3 class="rc__name">{{ p.name }}</h3>
        <p v-if="p.strategyTag?.trim()" class="rc__desc">{{ p.strategyTag }}</p>
        <div class="rc__credits">
          <strong>{{ p.credits }}</strong>
          <span>{{ t('credits') }}</span>
        </div>
        <div class="rc__price" :class="{ 'rc__price--muted': p.price == null }">{{ p.priceText }}</div>
        <p v-if="p.unitText" class="rc__unit">{{ t('uiRcPerCredit', { p: p.unitText }) }}</p>
        <button
          type="button"
          class="btn btn-block"
          :class="p.best ? 'btn-primary' : 'btn-secondary'"
          :disabled="p.purchasable === false || buyingId != null"
          @click="buy(p)"
        >
          <span v-if="buyingId === p.id" class="spinner" style="width: 16px; height: 16px"></span>
          {{ t('uiRcBuyNow') }}
        </button>
      </article>
    </div>

    <section class="rc__guide">
      <h2 class="section-title">{{ t('uiRcGuideTitle') }}</h2>
      <p class="muted rc__guide-lead">{{ t('uiRcGuideLead') }}</p>
      <div class="rc__guide-grid">
        <RouterLink v-for="g in guide" :key="g.key" :to="g.path" class="rc__guide-item">
          <span class="rc__guide-icon"><Icon :name="g.icon" :size="18" /></span>
          <span class="rc__guide-title">{{ g.title }}</span>
          <span class="rc__guide-cost">{{ t('uiCostFrom', { n: g.cost }) }}</span>
        </RouterLink>
      </div>
      <ul class="rc__notes">
        <li><Icon name="checkCircle" :size="15" />{{ t('uiRcNoteInstant') }}</li>
        <li><Icon name="checkCircle" :size="15" />{{ t('uiRcNoteFail') }}</li>
        <li><Icon name="checkCircle" :size="15" />{{ t('uiRcNoteSupport') }}</li>
      </ul>
    </section>

    <Teleport to="body">
      <div v-if="usdtPkg" class="rc-modal" @click.self="usdtPkg = null">
        <div class="rc-modal__card">
          <h3>{{ t('chooseUsdtNetwork') }}</h3>
          <div class="rc-modal__actions">
            <button type="button" class="rc__method" style="--m: #c53027" @click="pickUsdt('usdt_tron')">
              <img src="/static/tron.svg" alt="" /><span>{{ t('payUsdtTron') }}</span>
            </button>
            <button type="button" class="rc__method" style="--m: #f0b90b" @click="pickUsdt('usdt_bsc')">
              <img src="/static/bsc.svg" alt="" /><span>{{ t('payUsdtBsc') }}</span>
            </button>
          </div>
          <button type="button" class="btn btn-ghost btn-block" @click="usdtPkg = null">{{ t('close') }}</button>
        </div>
      </div>

      <div v-if="pay" class="rc-modal">
        <div class="rc-modal__card rc-modal__card--pay">
          <button type="button" class="rc-modal__x" :aria-label="t('close')" @click="closePay">
            <Icon name="x" :size="18" />
          </button>
          <h3>{{ t('scanPay') }}</h3>
          <p class="rc-modal__amount">
            <strong>{{ pay.amount }}</strong> <span>{{ pay.currency }}</span>
          </p>
          <p class="rc-modal__meta">{{ pay.pkgName }} · {{ t('orderNo') }} {{ pay.orderNo }}</p>
          <div class="rc-modal__qr" :class="'rc-modal__qr--' + pay.channel.split('_')[0]">
            <img v-if="qrSrc" :src="qrSrc" alt="QR" />
            <div v-if="payStatus === 'paid'" class="rc-modal__qr-veil rc-modal__qr-veil--ok">
              <Icon name="checkCircle" :size="40" />
            </div>
            <div v-else-if="payStatus === 'timeout'" class="rc-modal__qr-veil">
              <Icon name="clock" :size="36" />
            </div>
          </div>
          <p class="rc-modal__status" :class="'is-' + payStatus">
            <span>{{ payStatusText }}</span>
            <span v-if="payCountdown" class="rc-modal__cd">{{ payCountdown }}</span>
          </p>
          <p class="rc-modal__hint" v-html="pay.hint"></p>
          <button type="button" class="btn btn-secondary btn-block" @click="closePay">{{ t('close') }}</button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.rc {
  max-width: 1080px;
}
.rc__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.rc__balance {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--text);
  text-decoration: none;
}
.rc__balance:hover {
  border-color: var(--line-strong);
}
.rc__balance-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--brand-soft);
  color: var(--brand);
}
.rc__balance small {
  display: block;
  font-size: 0.72rem;
  color: var(--text-3);
}
.rc__balance strong {
  font-size: 1.2rem;
  font-variant-numeric: tabular-nums;
}
.rc__balance-chev {
  color: var(--text-3);
}
.rc__methods {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
.rc__methods-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-2);
}
.rc__method-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.rc__method {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  padding: 0 16px;
  border-radius: var(--radius);
  border: 1.5px solid var(--line);
  background: var(--panel);
  color: var(--text-2);
  font: inherit;
  font-size: 0.86rem;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.rc__method img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.rc__method:hover {
  border-color: var(--m);
  color: var(--text);
}
.rc__method.on {
  border-color: var(--m);
  color: var(--text);
  background: color-mix(in srgb, var(--m) 14%, var(--panel));
}
.rc__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 14px;
}
.rc__card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 20px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
  transition: border-color 0.15s, transform 0.15s;
}
.rc__card:hover {
  border-color: var(--line-strong);
}
.rc__card--best {
  border-color: var(--brand);
  background: linear-gradient(180deg, rgba(108, 76, 246, 0.08), var(--panel) 45%);
}
.rc__card--off {
  opacity: 0.55;
}
.rc__card-tags {
  display: flex;
  gap: 6px;
  min-height: 22px;
  margin-bottom: 8px;
}
.rc__name {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}
.rc__desc {
  margin: 4px 0 0;
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--text-3);
}
.rc__credits {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 16px;
}
.rc__credits strong {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}
.rc__credits span {
  font-size: 0.85rem;
  color: var(--text-3);
}
.rc__price {
  margin-top: 2px;
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--brand);
}
.rc__price--muted {
  color: var(--text-2);
  font-size: 0.95rem;
}
.rc__unit {
  margin: 2px 0 0;
  font-size: 0.75rem;
  color: var(--text-3);
}
.rc__card .btn-block {
  width: 100%;
  margin-top: auto;
}
.rc__card .rc__unit + .btn-block,
.rc__card .rc__price + .btn-block {
  margin-top: 18px;
}
.rc__guide {
  margin-top: 40px;
}
.rc__guide-lead {
  margin: 4px 0 14px;
  font-size: 0.86rem;
}
.rc__guide-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}
.rc__guide-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-radius: var(--radius);
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--text);
  text-decoration: none;
  font-size: 0.86rem;
}
.rc__guide-item:hover {
  border-color: var(--line-strong);
}
.rc__guide-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: var(--panel-2);
  color: var(--brand);
  flex-shrink: 0;
}
.rc__guide-title {
  flex: 1;
  min-width: 0;
  font-weight: 500;
}
.rc__guide-cost {
  font-size: 0.75rem;
  color: var(--text-3);
  white-space: nowrap;
}
.rc__notes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
  font-size: 0.8rem;
  color: var(--text-2);
}
.rc__notes li {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.rc__notes :deep(svg) {
  color: var(--brand);
}

.rc-modal {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(4px);
}
.rc-modal__card {
  position: relative;
  width: 100%;
  max-width: 340px;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  padding: 24px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line-strong);
  box-shadow: var(--shadow-3);
  text-align: center;
}
.rc-modal__card h3 {
  margin: 0 0 14px;
  font-size: 1.05rem;
}
.rc-modal__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 10px;
}
.rc-modal__x {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: var(--text-3);
  cursor: pointer;
}
.rc-modal__x:hover {
  background: var(--panel-2);
  color: var(--text);
}
.rc-modal__amount {
  margin: 0;
}
.rc-modal__amount strong {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--brand);
}
.rc-modal__meta {
  margin: 4px 0 14px;
  font-size: 0.75rem;
  color: var(--text-3);
  word-break: break-all;
}
.rc-modal__qr {
  position: relative;
  display: inline-flex;
  padding: 12px;
  border-radius: var(--radius);
  background: #fff;
  border: 3px solid var(--line);
}
.rc-modal__qr--wxpay {
  border-color: #07c160;
}
.rc-modal__qr--alipay {
  border-color: #1677ff;
}
.rc-modal__qr--usdt {
  border-color: #26a17b;
}
.rc-modal__qr img {
  display: block;
  width: 200px;
  height: 200px;
}
.rc-modal__qr-veil {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: inherit;
  background: rgba(255, 255, 255, 0.9);
  color: var(--text-3);
}
.rc-modal__qr-veil--ok {
  color: #16a34a;
}
.rc-modal__status {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin: 14px 0 6px;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-2);
}
.rc-modal__status.is-paid {
  color: var(--success);
}
.rc-modal__status.is-timeout {
  color: var(--error);
}
.rc-modal__cd {
  color: var(--brand);
}
.rc-modal__hint {
  margin: 0 0 16px;
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--text-3);
}
</style>
