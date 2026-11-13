<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import ChipGroup from '../components/ui/ChipGroup.vue';
import Icon from '../components/ui/Icon.vue';
import { api } from '../lib/api.js';
import { auth } from '../lib/auth.js';
import { i18n } from '../lib/i18n.js';
import { useT } from '../lib/useT.js';
import { toast } from '../lib/toast.js';

const { t } = useT();
const router = useRouter();

const CATEGORIES = [
  { value: 'tryon', labelKey: 'feedbackCatTryon' },
  { value: 'local_edit', labelKey: 'feedbackCatLocalEdit' },
  { value: 'model_shot', labelKey: 'feedbackCatModelShot' },
  { value: 'clothing_suite', labelKey: 'feedbackCatClothingSuite' },
  { value: 'billing', labelKey: 'feedbackCatBilling' },
  { value: 'account', labelKey: 'feedbackCatAccount' },
  { value: 'other', labelKey: 'feedbackCatOther' },
];

const category = ref('other');
const content = ref('');
const contact = ref('');
const submitting = ref(false);
const sent = ref(false);

const options = computed(() => CATEGORIES.map((c) => ({ value: c.value, label: t(c.labelKey) })));

async function submit() {
  if (!auth.isLoggedIn()) {
    toast(t('feedbackLoginRequired'), 'info');
    return router.push('/login?return=' + encodeURIComponent('/feedback'));
  }
  if (!content.value.trim()) return toast(t('feedbackErrContent'), 'error');
  submitting.value = true;
  try {
    await api.submitFeedback({
      category: category.value,
      content: content.value.trim(),
      contact: contact.value.trim() || undefined,
    });
    toast(t('feedbackThanks'), 'success');
    content.value = '';
    contact.value = '';
    sent.value = true;
  } catch (e) {
    toast(i18n.formatApiError(e.message) || t('errRequestFail'), 'error');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="page fb">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ t('feedbackTitle') }}</h1>
        <p class="page-lead">{{ t('feedbackLead') }}</p>
      </div>
    </header>

    <div v-if="sent" class="fb__sent">
      <Icon name="checkCircle" :size="20" />
      <span>{{ t('feedbackThanks') }}</span>
      <button type="button" class="btn btn-ghost btn-sm" @click="sent = false">{{ t('uiFbAgain') }}</button>
    </div>

    <form class="fb__card" @submit.prevent="submit">
      <div class="fb__field">
        <label>{{ t('feedbackCategory') }}</label>
        <ChipGroup v-model="category" :options="options" />
      </div>
      <div class="fb__field">
        <label for="fb-content">{{ t('feedbackContent') }}</label>
        <textarea
          id="fb-content"
          v-model="content"
          rows="6"
          maxlength="4000"
          :placeholder="t('feedbackContentPh')"
        ></textarea>
        <small class="fb__count">{{ content.length }} / 4000</small>
      </div>
      <div class="fb__field">
        <label for="fb-contact">{{ t('feedbackContact') }}</label>
        <input id="fb-contact" v-model="contact" type="text" :placeholder="t('feedbackContactPh')" />
        <small>{{ t('feedbackContactHintLoggedIn') }}</small>
      </div>
      <div class="fb__foot">
        <RouterLink to="/contact" class="btn btn-ghost"><Icon name="message" :size="16" />{{ t('navContact') }}</RouterLink>
        <button type="submit" class="btn btn-primary" :disabled="submitting">
          <span v-if="submitting" class="spinner" style="width: 15px; height: 15px"></span>{{ t('feedbackSubmit') }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.fb {
  max-width: 720px;
}
.fb__card {
  padding: 22px;
  border-radius: var(--radius-lg);
  background: var(--panel);
  border: 1px solid var(--line);
}
.fb__field {
  position: relative;
  margin-bottom: 18px;
}
.fb__field > label {
  display: block;
  margin-bottom: 8px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-2);
}
.fb__field textarea,
.fb__field input {
  width: 100%;
}
.fb__field textarea {
  min-height: 140px;
  resize: vertical;
}
.fb__field small {
  display: block;
  margin-top: 6px;
  font-size: 0.75rem;
  color: var(--text-3);
}
.fb__count {
  text-align: right;
}
.fb__foot {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.fb__sent {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  padding: 12px 16px;
  border-radius: var(--radius);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 0.88rem;
  font-weight: 500;
}
.fb__sent .btn {
  margin-left: auto;
}
</style>
