<script setup>
import { ref, computed, onMounted } from 'vue';
import '../assets/css/developer-console.css';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { toast } from '../lib/toast.js';
import {
  resolveOpenApiBase,
  OPEN_API_MODELS,
  buildSdkExamples,
  SDK_LANGS,
} from '../lib/openApiDocs.js';

const loginPrompt = ref(!auth.isLoggedIn());
const panelVisible = ref(false);
const activeTab = ref('keys');
const keys = ref([]);
const keyName = ref('');
const loadingKeys = ref(false);
const creating = ref(false);
const newKeyModal = ref(null);
const sdkModel = ref('virtual_tryon');
const sdkLang = ref('curl');
const apiBase = computed(() => resolveOpenApiBase());
const sdkExamples = computed(() =>
  buildSdkExamples(apiBase.value, newKeyModal.value?.apiKey || 'fsk_your_api_key_here', sdkModel.value)
);
const currentSdkCode = computed(() => sdkExamples.value[sdkLang.value] || '');

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

function formatTime(d) {
  if (!d) return '-';
  const dt = new Date(d);
  const locale = i18n.getLocale();
  return dt.toLocaleString(locale === 'en' ? 'en-US' : 'zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

async function loadKeys() {
  if (!auth.isLoggedIn()) {
    loginPrompt.value = true;
    panelVisible.value = false;
    return;
  }
  loginPrompt.value = false;
  panelVisible.value = true;
  loadingKeys.value = true;
  try {
    keys.value = (await api.listApiKeys()) || [];
  } catch (e) {
    keys.value = [];
    toast(e.message || i18n.t('devLoadKeysFail'), 'error');
  } finally {
    loadingKeys.value = false;
  }
}

async function onCreateKey() {
  if (creating.value) return;
  creating.value = true;
  try {
    const created = await api.createApiKey(keyName.value.trim() || undefined);
    newKeyModal.value = created;
    keyName.value = '';
    await loadKeys();
    toast(i18n.t('devKeyCreated'), 'success');
  } catch (e) {
    toast(e.message || i18n.t('devCreateKeyFail'), 'error');
  } finally {
    creating.value = false;
  }
}

async function onRevoke(id) {
  if (!window.confirm(i18n.t('devRevokeConfirm'))) return;
  try {
    await api.revokeApiKey(id);
    await loadKeys();
    toast(i18n.t('devKeyRevoked'), 'success');
  } catch (e) {
    toast(e.message || i18n.t('devRevokeFail'), 'error');
  }
}

function onCopySecret() {
  if (!newKeyModal.value?.apiKey) return;
  copyText(newKeyModal.value.apiKey)
    .then(() => toast(i18n.t('copyCode'), 'info'))
    .catch(() => toast(i18n.t('errCopyFail'), 'error'));
}

function onCopySdk() {
  copyText(currentSdkCode.value)
    .then(() => toast(i18n.t('copyCode'), 'info'))
    .catch(() => toast(i18n.t('errCopyFail'), 'error'));
}

function closeNewKeyModal() {
  newKeyModal.value = null;
}

onMounted(loadKeys);
</script>

<template>
  <main class="main dev-main">
    <h1>{{ i18n.t('devConsoleTitle') }}</h1>
    <p class="dev-sub">{{ i18n.t('devConsoleDesc') }}</p>

    <div v-if="loginPrompt" class="login-prompt card" v-html="i18n.t('devLoginPrompt')"></div>

    <template v-else-if="panelVisible">
      <div class="dev-tabs" role="tablist">
        <button
          type="button"
          class="dev-tab"
          :class="{ active: activeTab === 'keys' }"
          role="tab"
          @click="activeTab = 'keys'"
        >
          {{ i18n.t('devTabKeys') }}
        </button>
        <button
          type="button"
          class="dev-tab"
          :class="{ active: activeTab === 'docs' }"
          role="tab"
          @click="activeTab = 'docs'"
        >
          {{ i18n.t('devTabDocs') }}
        </button>
      </div>

      <section v-show="activeTab === 'keys'" class="dev-panel active">
        <div class="dev-card card">
          <h2>{{ i18n.t('devKeysTitle') }}</h2>
          <p class="dev-hint">{{ i18n.t('devKeysHint') }}</p>
          <div class="dev-row">
            <input
              v-model="keyName"
              type="text"
              class="dev-input"
              :placeholder="i18n.t('devKeyNamePlaceholder')"
              maxlength="64"
            />
            <button type="button" class="btn btn-primary" :disabled="creating" @click="onCreateKey">
              {{ creating ? i18n.t('devCreating') : i18n.t('devCreateKey') }}
            </button>
          </div>

          <div v-if="loadingKeys" class="dev-empty">{{ i18n.t('loading') }}</div>
          <div v-else-if="keys.length === 0" class="dev-empty">{{ i18n.t('devNoKeys') }}</div>
          <div v-else class="dev-key-list">
            <div v-for="k in keys" :key="k.id" class="dev-key-item">
              <div class="dev-key-meta">
                <div class="dev-key-name">{{ k.name || 'Default' }}</div>
                <div class="dev-key-prefix">{{ k.keyPrefix }}</div>
                <div class="dev-key-time">
                  {{ i18n.t('devKeyCreatedAt') }} {{ formatTime(k.createdAt) }}
                  · {{ i18n.t('devKeyLastUsed') }} {{ formatTime(k.lastUsedAt) }}
                </div>
              </div>
              <button type="button" class="btn btn-secondary btn-sm" @click="onRevoke(k.id)">
                {{ i18n.t('devRevokeKey') }}
              </button>
            </div>
          </div>
        </div>
      </section>

      <section v-show="activeTab === 'docs'" class="dev-panel active">
        <div class="dev-card card">
          <h2>{{ i18n.t('devAuthTitle') }}</h2>
          <p class="dev-hint">{{ i18n.t('devAuthHint') }}</p>
          <div class="dev-endpoint">
            <code>Authorization: Bearer fsk_...</code>
          </div>
          <div class="dev-endpoint">
            <code>X-Api-Key: fsk_...</code>
          </div>
          <p class="dev-hint">{{ i18n.t('devBillingHint') }}</p>
        </div>

        <div class="dev-card card">
          <h2>{{ i18n.t('devBaseUrlTitle') }}</h2>
          <div class="dev-base-url">
            <code>{{ apiBase }}</code>
          </div>
        </div>

        <div class="dev-card card">
          <h2>{{ i18n.t('devEndpointsTitle') }}</h2>

          <h3>POST /submit</h3>
          <p class="dev-hint">{{ i18n.t('devSubmitDesc') }}</p>
          <div class="dev-endpoint">
            <span class="dev-method post">POST</span>
            <code>{{ apiBase }}/submit</code>
          </div>
          <table class="dev-table">
            <thead>
              <tr>
                <th>{{ i18n.t('devParam') }}</th>
                <th>{{ i18n.t('devType') }}</th>
                <th>{{ i18n.t('devDesc') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>model</code></td>
                <td>string</td>
                <td>{{ i18n.t('devParamModel') }}</td>
              </tr>
              <tr>
                <td><code>payload</code></td>
                <td>JSON string</td>
                <td>{{ i18n.t('devParamPayload') }}</td>
              </tr>
              <tr>
                <td><code>file / files</code></td>
                <td>file</td>
                <td>{{ i18n.t('devParamFiles') }}</td>
              </tr>
              <tr>
                <td><code>faceReference</code></td>
                <td>file</td>
                <td>{{ i18n.t('devParamFace') }}</td>
              </tr>
            </tbody>
          </table>

          <h3>GET /task</h3>
          <p class="dev-hint">{{ i18n.t('devTaskDesc') }}</p>
          <div class="dev-endpoint">
            <span class="dev-method get">GET</span>
            <code>{{ apiBase }}/task?model=virtual_tryon&amp;taskId=123456</code>
          </div>
          <table class="dev-table">
            <thead>
              <tr>
                <th>{{ i18n.t('devParam') }}</th>
                <th>{{ i18n.t('devType') }}</th>
                <th>{{ i18n.t('devDesc') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>model</code></td>
                <td>string</td>
                <td>{{ i18n.t('devParamModelQuery') }}</td>
              </tr>
              <tr>
                <td><code>taskId</code></td>
                <td>number</td>
                <td>{{ i18n.t('devParamTaskId') }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="dev-card card">
          <h2>{{ i18n.t('devModelsTitle') }}</h2>
          <table class="dev-table">
            <thead>
              <tr>
                <th><code>model</code></th>
                <th>{{ i18n.t('devDesc') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in OPEN_API_MODELS" :key="m.id">
                <td><code>{{ m.id }}</code></td>
                <td>{{ i18n.getLocale() === 'en' ? m.en : m.zh }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="dev-card card">
          <h2>{{ i18n.t('devResponseTitle') }}</h2>
          <p class="dev-hint">{{ i18n.t('devResponseHint') }}</p>
          <div class="dev-code">
            <pre>{
  "code": 200,
  "message": "操作成功",
  "data": {
    "model": "virtual_tryon",
    "taskId": "123456"
  }
}</pre>
          </div>
          <h3>{{ i18n.t('devTaskResponseTitle') }}</h3>
          <div class="dev-code">
            <pre>{
  "code": 200,
  "data": {
    "model": "virtual_tryon",
    "taskKind": "tryon",
    "tryon": { "status": "COMPLETED", "resultUrl": "..." }
  }
}</pre>
          </div>
        </div>

        <div class="dev-card card">
          <h2>{{ i18n.t('devSdkTitle') }}</h2>
          <p class="dev-hint">{{ i18n.t('devSdkHint') }}</p>
          <div class="dev-model-picker">
            <span class="dev-model-picker__label">{{ i18n.t('devSdkModel') }}</span>
            <div class="dev-model-tabs" role="listbox" :aria-label="i18n.t('devSdkModel')">
              <button
                v-for="m in OPEN_API_MODELS"
                :key="m.id"
                type="button"
                class="dev-model-tab"
                :class="{ active: sdkModel === m.id }"
                role="option"
                :aria-selected="sdkModel === m.id"
                @click="sdkModel = m.id"
              >
                <span class="dev-model-tab__id">{{ m.id }}</span>
                <span class="dev-model-tab__name">{{ i18n.getLocale() === 'en' ? m.en : m.zh }}</span>
              </button>
            </div>
          </div>
          <div class="dev-lang-tabs">
            <button
              v-for="lang in SDK_LANGS"
              :key="lang.id"
              type="button"
              class="dev-lang-tab"
              :class="{ active: sdkLang === lang.id }"
              @click="sdkLang = lang.id"
            >
              {{ lang.label }}
            </button>
          </div>
          <div class="dev-code">
            <button type="button" class="btn btn-secondary dev-copy-btn" @click="onCopySdk">
              {{ i18n.t('copyCode') }}
            </button>
            <pre>{{ currentSdkCode }}</pre>
          </div>
        </div>
      </section>
    </template>

    <div
      v-if="newKeyModal"
      class="dev-modal-backdrop"
      role="dialog"
      aria-modal="true"
      @click.self="closeNewKeyModal"
    >
      <div class="dev-modal card">
        <h3>{{ i18n.t('devNewKeyTitle') }}</h3>
        <p class="dev-hint">{{ i18n.t('devNewKeyHint') }}</p>
        <code class="dev-secret">{{ newKeyModal.apiKey }}</code>
        <div class="dev-row">
          <button type="button" class="btn btn-primary" @click="onCopySecret">
            {{ i18n.t('copyCode') }}
          </button>
          <button type="button" class="btn btn-secondary" @click="closeNewKeyModal">
            {{ i18n.t('devKeySaved') }}
          </button>
        </div>
      </div>
    </div>

  </main>
</template>
