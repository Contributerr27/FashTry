<script setup>
import { onMounted, ref } from 'vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';

const { t } = useT();

const loggedIn = ref(auth.isLoggedIn());
const loading = ref(loggedIn.value);
const code = ref('');
const link = ref('');
const rule = ref('');
const totalInvites = ref(0);
const totalRewards = ref(0);

function copyText(text) {
  if (navigator.clipboard?.writeText) return navigator.clipboard.writeText(text);
  const ta = document.createElement('textarea');
  ta.value = text;
  ta.style.position = 'fixed';
  ta.style.opacity = '0';
  document.body.appendChild(ta);
  ta.select();
  try {
    document.execCommand('copy');
    return Promise.resolve();
  } finally {
    document.body.removeChild(ta);
  }
}

async function copy(value, okKey, emptyKey) {
  if (!value) return toast(t(emptyKey), 'error');
  try {
    await copyText(value);
    toast(t(okKey), 'success');
  } catch {
    toast(t('errCopyFail'), 'error');
  }
}

function escapeHtml(s) {
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

async function load() {
  if (!auth.isLoggedIn()) return;
  try {
    const data = await api.getInviteInfo();
    code.value = data.invitationCode || '';
    link.value = code.value ? `${window.location.origin}/login?invite=${encodeURIComponent(code.value)}` : '';
    rule.value = data.rewardRule ? escapeHtml(data.rewardRule).replace(/(\d+)/g, '<strong>$1</strong>') : '';
    totalInvites.value = data.totalInvites ?? 0;
    totalRewards.value = data.totalRewards ?? 0;
  } catch {
    toast(t('errGetInviteFail'), 'error');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page iv">
    <section class="iv__hero">
      <span class="iv__hero-icon"><Icon name="gift" :size="28" /></span>
      <div>
        <h1 class="page-title">{{ t('inviteTitle') }}</h1>
        <p class="page-lead" v-html="rule || t('inviteDesc')"></p>
      </div>
    </section>

    <div v-if="!loggedIn" class="iv__login">
      <p v-html="t('loginToInvite')"></p>
      <RouterLink :to="'/login?return=' + encodeURIComponent('/invite')" class="btn btn-primary">{{ t('login') }}</RouterLink>
    </div>

    <template v-else>
      <div class="iv__stats">
        <div class="iv__stat">
          <span class="iv__stat-icon"><Icon name="user" :size="18" /></span>
          <div>
            <strong>{{ loading ? '—' : totalInvites }}</strong>
            <small>{{ t('uiInvTotal') }}</small>
          </div>
        </div>
        <div class="iv__stat">
          <span class="iv__stat-icon"><Icon name="bolt" :size="18" /></span>
          <div>
            <strong>{{ loading ? '—' : totalRewards }}</strong>
            <small>{{ t('uiInvRewards') }}</small>
          </div>
        </div>
      </div>

      <section class="iv__card">
        <label class="iv__label">{{ t('yourCode') }}</label>
        <div class="iv__row">
          <div class="iv__code">{{ code || '—' }}</div>
          <button type="button" class="btn btn-secondary" @click="copy(code, 'copyCode', 'errNoInviteCode')">
            <Icon name="copy" :size="16" />{{ t('copyCode') }}
          </button>
        </div>
        <label class="iv__label">{{ t('inviteLink') }}</label>
        <div class="iv__row">
          <input type="text" :value="link" readonly @focus="$event.target.select()" />
          <button type="button" class="btn btn-primary" @click="copy(link, 'copyLink', 'errNoInviteLink')">
            <Icon name="copy" :size="16" />{{ t('copyLink') }}
          </button>
        </div>
      </section>

      <section class="iv__card">
        <h2 class="section-title">{{ t('inviteRules') }}</h2>
        <ol class="iv__rules">
          <li v-for="k in ['rule1', 'rule2', 'rule3', 'rule4']" :key="k">{{ t(k) }}</li>
        </ol>
      </section>
    </template>
  </div>
</template>

<style scoped>
.iv {
  max-width: 760px;
}
.iv__hero {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 24px;
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  border: 1px solid rgba(108, 76, 246, 0.28);
  background:
    radial-gradient(120% 140% at 0% 0%, rgba(108, 76, 246, 0.14), transparent 55%),
    var(--panel);
}
.iv__hero .page-title {
  margin: 0;
}
.iv__hero .page-lead {
  margin: 6px 0 0;
}
.iv__hero .page-lead :deep(strong) {
  color: var(--brand);
}
.iv__hero-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 14px;
  background: var(--brand-soft);
  color: var(--brand);
}
.iv__login {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 20px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
  color: var(--text-2);
  text-align: center;
}
.iv__login p {
  margin: 0;
}
.iv__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}
.iv__stat {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
}
.iv__stat-icon {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--panel-2);
  color: var(--brand);
}
.iv__stat strong {
  display: block;
  font-size: 1.4rem;
  font-variant-numeric: tabular-nums;
}
.iv__stat small {
  font-size: 0.75rem;
  color: var(--text-3);
}
.iv__card {
  padding: 20px;
  margin-bottom: 16px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
}
.iv__label {
  display: block;
  margin-bottom: 8px;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-2);
}
.iv__row {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.iv__row:last-child {
  margin-bottom: 0;
}
.iv__row input {
  flex: 1;
  min-width: 0;
}
.iv__code {
  flex: 1;
  display: flex;
  align-items: center;
  padding: 0 14px;
  border-radius: var(--radius);
  background: var(--bg-elev);
  border: 1px dashed var(--line-strong);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 1.15rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--brand);
}
.iv__rules {
  margin: 10px 0 0;
  padding-left: 1.2em;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 0.86rem;
  line-height: 1.6;
  color: var(--text-2);
}
@media (max-width: 560px) {
  .iv__row {
    flex-direction: column;
  }
  .iv__code {
    min-height: 44px;
  }
}
</style>
