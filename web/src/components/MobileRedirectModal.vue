<script setup>
import { RouterLink } from 'vue-router';
import { i18n } from '../lib/i18n.js';

import { markStayOnDesktop } from '../lib/mobileRedirect.js';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
});

const emit = defineEmits(['update:modelValue']);

function stay() {
  markStayOnDesktop();
  emit('update:modelValue', false);
}

function goMobile() {
  window.location.href = '/mobile/';
}
</script>

<template>
  <div v-if="modelValue" class="mobile-redirect-overlay" @click.self="stay">
    <div class="mobile-redirect-card" role="dialog" aria-modal="true">
      <h2>{{ i18n.t('mobileRedirectTitle') }}</h2>
      <p>{{ i18n.t('mobileRedirectLead') }}</p>
      <p>{{ i18n.t('mobileRedirectQuestion') }}</p>
      <p class="mobile-redirect-hint">{{ i18n.t('mobileRedirectBackdropHint') }}</p>
      <div class="mobile-redirect-actions">
        <button type="button" class="btn btn-secondary" @click="stay">
          {{ i18n.t('mobileRedirectStay') }}
        </button>
        <button type="button" class="btn btn-primary" @click="goMobile">
          {{ i18n.t('mobileRedirectGo') }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mobile-redirect-overlay {
  position: fixed;
  inset: 0;
  z-index: 1050;
  background: rgba(5, 5, 8, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.mobile-redirect-card {
  max-width: 400px;
  width: 100%;
  background: var(--bg-card-solid);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 1.25rem;
}

.mobile-redirect-card h2 {
  font-size: 1.05rem;
  margin-bottom: 0.5rem;
}

.mobile-redirect-card p {
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.5;
  margin-bottom: 0.35rem;
}

.mobile-redirect-hint {
  font-size: 0.8rem;
  opacity: 0.85;
}

.mobile-redirect-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
}

.mobile-redirect-actions .btn {
  flex: 1;
}
</style>
