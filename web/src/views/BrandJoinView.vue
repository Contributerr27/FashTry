<script setup>
import { ref, onMounted, onActivated, onUnmounted } from 'vue';
import { i18n } from '../lib/i18n.js';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';

const STORAGE_KEY = 'fashtry_brand_intake_code';

const localeKey = ref(0);
const companyName = ref('');
const brandName = ref('');
const brandNameEn = ref('');
const contactName = ref('');
const contactPhone = ref('');
const fileInput = ref(null);
const fileName = ref('');
const submitting = ref(false);
const err = ref('');

const intake = ref(null);
const statusLoading = ref(false);
const syncing = ref(false);

function auditLabel(as) {
  const map = {
    0: i18n.t('brandJoinAudit0'),
    1: i18n.t('brandJoinAudit1'),
    2: i18n.t('brandJoinAudit2'),
    3: i18n.t('brandJoinAudit3'),
  };
  return map[as] ?? String(as);
}

async function loadIntakeStatus(mode = 'full') {
  if (mode === 'full') statusLoading.value = true;
  else syncing.value = true;
  err.value = '';
  try {
    const token = localStorage.getItem('accessToken');
    if (token) {
      try {
        const mine = await api.getMyBrandIntake();
        if (mine?.code) {
          intake.value = mine;
          localStorage.setItem(STORAGE_KEY, mine.code);
          return;
        }
      } catch {
        /* 回落到本地编号 */
      }
    }
    const code = localStorage.getItem(STORAGE_KEY);
    if (code) {
      intake.value = await api.getBrandIntakeStatus(code);
    } else {
      intake.value = null;
    }
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    intake.value = null;
  } finally {
    if (mode === 'full') statusLoading.value = false;
    else syncing.value = false;
  }
}

async function refreshStatus() {
  await loadIntakeStatus('silent');
}

function onLocaleChange() {
  localeKey.value++;
}

function onPickFile() {
  fileInput.value?.click();
}

function onFileChange(e) {
  const f = e.target.files?.[0];
  fileName.value = f ? f.name : '';
}

async function onSubmit(e) {
  e.preventDefault();
  err.value = '';
  if (!auth.requireLogin()) return;
  const logo = fileInput.value?.files?.[0];
  if (!logo) {
    err.value = i18n.t('brandJoinErrLogo');
    return;
  }
  const fd = new FormData();
  fd.append('companyName', companyName.value.trim());
  fd.append('brandName', brandName.value.trim());
  fd.append('brandNameEn', brandNameEn.value.trim());
  fd.append('contactName', contactName.value.trim());
  fd.append('contactPhone', contactPhone.value.trim());
  fd.append('logo', logo);
  submitting.value = true;
  try {
    const vo = await api.submitBrandIntake(fd);
    localStorage.setItem(STORAGE_KEY, vo.code);
    intake.value = vo;
  } catch (e) {
    err.value = e.message || 'Error';
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  window.addEventListener('localechange', onLocaleChange);
  void loadIntakeStatus('full');
});
onActivated(() => {
  void loadIntakeStatus('full');
});
onUnmounted(() => {
  window.removeEventListener('localechange', onLocaleChange);
});
</script>

<template>
  <main :key="localeKey" class="page brand-join-main">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ i18n.t('brandJoinTitle') }}</h1>
        <p class="page-lead">{{ i18n.t('brandJoinLead') }}</p>
      </div>
    </header>

    <div v-if="statusLoading" class="card brand-join-done">
      <p>{{ i18n.t('brandJoinStatusLoading') }}</p>
    </div>

    <div v-else-if="intake" class="card brand-join-status">
      <h2 class="brand-join-status-title">{{ i18n.t('brandJoinStatusTitle') }}</h2>
      <p class="brand-join-status-line">
        {{ i18n.t('brandJoinYourCompany') }}：{{ intake.companyName }}
      </p>
      <p class="brand-join-status-line">
        {{ i18n.t('brandJoinYourBrand') }}：{{ intake.brandName }}
      </p>
      <p v-if="intake.brandNameEn" class="brand-join-status-line">
        {{ i18n.t('brandJoinBrandEn') }}：{{ intake.brandNameEn }}
      </p>
      <p class="brand-join-status-audit">{{ auditLabel(intake.auditStatus) }}</p>
      <p v-if="intake.auditStatus === 3" class="brand-join-reject-reason">
        {{ i18n.t('brandJoinRejectReason') }}：{{
          intake.rejectReason || i18n.t('brandJoinRejectNoReason')
        }}
      </p>
      <button
        type="button"
        class="btn btn-secondary brand-join-refresh"
        :disabled="syncing"
        @click="refreshStatus"
      >
        {{ syncing ? i18n.t('brandJoinStatusLoading') : i18n.t('brandJoinRefreshStatus') }}
      </button>
      <p class="brand-join-status-hint">{{ i18n.t('brandJoinSuccess') }}</p>
    </div>

    <form v-else class="card brand-join-form" @submit="onSubmit">
      <p v-if="err" class="brand-join-error">{{ err }}</p>

      <div class="form-group">
        <label for="bj-company">{{ i18n.t('brandJoinCompany') }}</label>
        <input
          id="bj-company"
          v-model="companyName"
          type="text"
          required
          maxlength="256"
          autocomplete="organization"
        />
      </div>
      <div class="form-group">
        <label for="bj-brand">{{ i18n.t('brandJoinBrand') }}</label>
        <input id="bj-brand" v-model="brandName" type="text" required maxlength="128" />
      </div>
      <div class="form-group">
        <label for="bj-brand-en">{{ i18n.t('brandJoinBrandEn') }}</label>
        <p class="brand-join-hint">{{ i18n.t('brandJoinBrandEnHint') }}</p>
        <input id="bj-brand-en" v-model="brandNameEn" type="text" maxlength="128" />
      </div>
      <div class="form-group">
        <label>{{ i18n.t('brandJoinLogo') }}</label>
        <p class="brand-join-hint">{{ i18n.t('brandJoinLogoHint') }}</p>
        <input
          ref="fileInput"
          type="file"
          class="brand-join-file-input"
          accept="image/jpeg,image/png,image/gif,image/webp"
          @change="onFileChange"
        />
        <button type="button" class="btn btn-secondary" @click="onPickFile">
          {{ i18n.t('brandJoinPickLogo') }}
        </button>
        <span v-if="fileName" class="brand-join-file-name">{{ fileName }}</span>
      </div>
      <div class="form-group">
        <label for="bj-contact">{{ i18n.t('brandJoinContactName') }}</label>
        <input id="bj-contact" v-model="contactName" type="text" required maxlength="64" />
      </div>
      <div class="form-group">
        <label for="bj-phone">{{ i18n.t('brandJoinContactPhone') }}</label>
        <input
          id="bj-phone"
          v-model="contactPhone"
          type="tel"
          required
          maxlength="32"
          autocomplete="tel"
        />
      </div>

      <button type="submit" class="btn btn-primary brand-join-submit" :disabled="submitting">
        {{ submitting ? i18n.t('brandJoinSubmitting') : i18n.t('brandJoinSubmit') }}
      </button>
    </form>
  </main>
</template>

<style scoped>
.brand-join-main {
  max-width: 640px;
}
.brand-join-form {
  padding: 1.5rem;
}
.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-2);
}
.form-group input:not([type='file']) {
  width: 100%;
}
.brand-join-hint {
  font-size: 0.78rem;
  color: var(--text-3);
  margin: 0 0 0.5rem;
}
.brand-join-file-input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}
.brand-join-file-name {
  display: inline-block;
  margin-left: 0.75rem;
  font-size: 0.9rem;
  color: var(--text-3);
  word-break: break-all;
}
.brand-join-submit {
  margin-top: 0.5rem;
  width: 100%;
}
.brand-join-error {
  color: var(--error);
  font-size: 0.9rem;
  margin-bottom: 1rem;
}
.brand-join-done {
  padding: 1.5rem;
  text-align: center;
}
.brand-join-status {
  padding: 1.5rem;
}
.brand-join-status-title {
  font-size: 1.1rem;
  margin: 0 0 0.75rem;
}
.brand-join-status-line {
  margin: 0.35rem 0;
  font-size: 0.95rem;
  color: var(--text-3);
}
.brand-join-status-audit {
  margin: 1rem 0;
  font-size: 1rem;
  font-weight: 600;
}
.brand-join-reject-reason {
  margin: 0 0 1rem;
  font-size: 0.95rem;
  color: var(--error);
  line-height: 1.5;
  word-break: break-word;
}
.brand-join-refresh {
  width: 100%;
  margin-top: 0.25rem;
}
.brand-join-status-hint {
  margin: 1rem 0 0;
  font-size: 0.9rem;
  color: var(--text-3);
  text-align: center;
}
</style>
