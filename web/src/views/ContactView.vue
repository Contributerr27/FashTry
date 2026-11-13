<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue';
import '../assets/css/contact.css';
import { i18n } from '../lib/i18n.js';
import Icon from '../components/ui/Icon.vue';

const WHATSAPP_URL = 'https://wa.me/message/NLP2MEBAVRYVL1';
const WECHAT_QR = '/static/wechat_qrcode_zh.png';
const EMAIL = 'kerten@ketengwang.ntesmail.com';

const localeKey = ref(0);
const wechatModalOpen = ref(false);

function onLocaleChange() {
  localeKey.value++;
}

let escHandler = null;

function closeWechatModal() {
  wechatModalOpen.value = false;
}

watch(wechatModalOpen, (open) => {
  if (typeof document === 'undefined') return;
  document.body.style.overflow = open ? 'hidden' : '';
  if (open) {
    escHandler = (e) => {
      if (e.key === 'Escape') closeWechatModal();
    };
    window.addEventListener('keydown', escHandler);
  } else if (escHandler) {
    window.removeEventListener('keydown', escHandler);
    escHandler = null;
  }
});

onMounted(() => {
  window.addEventListener('localechange', onLocaleChange);
});
onUnmounted(() => {
  window.removeEventListener('localechange', onLocaleChange);
  if (escHandler) window.removeEventListener('keydown', escHandler);
  document.body.style.overflow = '';
});
</script>

<template>
  <main :key="localeKey" class="page contact-main">
    <section class="contact-hero">
      <h1>{{ i18n.t('contactTitle') }}</h1>
      <p class="contact-brand">{{ i18n.t('contactBrand') }}</p>
      <p class="contact-intro">{{ i18n.t('contactIntro') }}</p>
    </section>

    <div class="contact-grid">
      <article class="contact-card card">
        <span class="contact-card-icon"><Icon name="message" :size="20" /></span>
        <p class="contact-card-label">{{ i18n.t('contactCardWaSection') }}</p>
        <h2>{{ i18n.t('contactCardWaTitle') }}</h2>
        <p class="contact-card-desc">{{ i18n.t('contactCardWaDesc') }}</p>
        <a
          class="btn btn-primary"
          :href="WHATSAPP_URL"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ i18n.t('contactCardWaCta') }}
        </a>
      </article>

      <article class="contact-card card contact-card-wechat">
        <span class="contact-card-icon"><Icon name="user" :size="20" /></span>
        <p class="contact-card-label">{{ i18n.t('contactCardWechatSection') }}</p>
        <h2>{{ i18n.t('contactCardWechatTitle') }}</h2>
        <p class="contact-card-desc">{{ i18n.t('contactCardWechatDesc') }}</p>
        <button type="button" class="btn btn-secondary" @click="wechatModalOpen = true">
          {{ i18n.t('contactCardWechatHint') }}
        </button>
      </article>

      <Teleport to="body">
        <div
          v-show="wechatModalOpen"
          class="contact-modal-root"
          role="dialog"
          aria-modal="true"
          :aria-label="i18n.t('contactWechatModalTitle')"
        >
          <div class="contact-modal-backdrop" aria-hidden="true" @click="closeWechatModal"></div>
          <div class="contact-modal-panel card">
            <button
              type="button"
              class="contact-modal-close"
              :aria-label="i18n.t('close')"
              @click="closeWechatModal"
            >
              &times;
            </button>
            <h3 class="contact-modal-title">{{ i18n.t('contactWechatModalTitle') }}</h3>
            <p class="contact-modal-hint">{{ i18n.t('contactWechatModalHint') }}</p>
            <div class="contact-qr-wrap contact-qr-wrap--modal">
              <img
                :src="WECHAT_QR"
                :alt="i18n.t('contactWechatQrAlt')"
                width="1190"
                height="662"
                loading="lazy"
                decoding="async"
              />
            </div>
          </div>
        </div>
      </Teleport>

      <article class="contact-card card">
        <span class="contact-card-icon"><Icon name="mail" :size="20" /></span>
        <p class="contact-card-label">{{ i18n.t('contactCardEmailSection') }}</p>
        <h2>{{ i18n.t('contactCardEmailTitle') }}</h2>
        <p class="contact-card-desc">{{ i18n.t('contactCardEmailDesc') }}</p>
        <p class="contact-email-addr contact-email-addr--solo">{{ EMAIL }}</p>
      </article>
    </div>
  </main>
</template>
