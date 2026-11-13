<script setup>
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import Icon from '../ui/Icon.vue';
import { useT } from '../../lib/useT.js';
import { userState } from '../../lib/userStore.js';

const props = defineProps({
  cost: { type: Number, default: null },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  label: { type: String, default: '' },
  loadingLabel: { type: String, default: '' },
});
const emit = defineEmits(['click']);
const { t } = useT();

const balance = computed(() => (userState.loggedIn ? Number(userState.info?.totalCredits ?? 0) : null));
const insufficient = computed(() => balance.value != null && props.cost != null && props.cost > balance.value);
</script>

<template>
  <button
    type="button"
    class="btn btn-primary wb-gen"
    :disabled="disabled || loading"
    @click="emit('click')"
  >
    <span v-if="loading" class="spinner" style="width: 16px; height: 16px; border-color: rgba(3, 36, 32, 0.25); border-top-color: var(--brand-ink)"></span>
    <Icon v-else name="sparkles" :size="17" />
    <span>{{ loading ? loadingLabel || t('uiGenerating') : label || t('uiGenerate') }}</span>
    <span v-if="cost != null && !loading" class="wb-gen__cost">{{ cost }} {{ t('credits') }}</span>
  </button>
  <div v-if="balance != null" class="wb-gen-meta" :class="{ 'wb-gen-meta--warn': insufficient }">
    <span>{{ t('uiCreditsLeft') }} {{ balance }}</span>
    <RouterLink to="/recharge">{{ t('uiRechargeNow') }}</RouterLink>
  </div>
  <div v-else class="wb-gen-meta">
    <span>{{ t('uiFreeTrialTip') }}</span>
  </div>
</template>
