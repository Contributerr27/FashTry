<script setup>
import { RouterLink, useRoute } from 'vue-router';
import Icon from '../ui/Icon.vue';
import { useT } from '../../lib/useT.js';

/**
 * historyItems: [{ key, thumb, status?: 'done'|'loading'|'failed', badge?: string, active?: boolean }]
 */
defineProps({
  rootId: { type: String, default: '' },
  titleKey: { type: String, required: true },
  leadKey: { type: String, default: '' },
  inputsTitle: { type: String, default: '' },
  paramsTitle: { type: String, default: '' },
  canvasTitle: { type: String, default: '' },
  historyTitleKey: { type: String, default: 'uiHistory' },
  historyItems: { type: Array, default: null },
  historyLoggedOut: { type: Boolean, default: false },
  worksKind: { type: String, default: '' },
  hideHistory: { type: Boolean, default: false },
  hideParams: { type: Boolean, default: false },
});
const emit = defineEmits(['history-select']);
const { t } = useT();
const route = useRoute();
</script>

<template>
  <div class="wb" :id="rootId || undefined">
    <header class="wb__head">
      <div>
        <h1 class="wb__title">{{ t(titleKey) }}</h1>
        <p v-if="leadKey" class="wb__lead">{{ t(leadKey) }}</p>
      </div>
      <slot name="head-extra" />
    </header>

    <div
      class="wb__grid"
      :class="{ 'wb__grid--no-inputs': !$slots.inputs, 'wb__grid--no-params': hideParams }"
    >
      <section v-if="$slots.inputs" class="wb__inputs wb-panel">
        <div class="wb-panel__head">
          <h2 class="wb-panel__title"><Icon name="upload" :size="15" class="wb-panel__ico" />{{ inputsTitle || t('uiInputs') }}</h2>
          <slot name="inputs-extra" />
        </div>
        <div class="wb-panel__body wf-scrollbar">
          <slot name="inputs" />
        </div>
        <div v-if="hideParams && $slots.actions" class="wb-panel__foot">
          <slot name="actions" />
        </div>
      </section>

      <section class="wb__canvas wb-panel">
        <div class="wb-panel__head">
          <h2 class="wb-panel__title"><Icon name="images" :size="15" class="wb-panel__ico" />{{ canvasTitle || t('uiResult') }}</h2>
          <div class="wb-toolbar"><slot name="toolbar" /></div>
        </div>
        <div class="wb-panel__body">
          <slot name="main" />
        </div>
      </section>

      <section v-if="!hideParams" class="wb__params wb-panel">
        <div class="wb-panel__head">
          <h2 class="wb-panel__title"><Icon name="palette" :size="15" class="wb-panel__ico" />{{ paramsTitle || t('uiParams') }}</h2>
          <slot name="params-extra" />
        </div>
        <div class="wb-panel__body wf-scrollbar">
          <slot name="params" />
        </div>
        <div v-if="$slots.actions" class="wb-panel__foot">
          <slot name="actions" />
        </div>
      </section>
    </div>

    <section v-if="!hideHistory && (historyItems || $slots.history)" class="wb-history" aria-label="history">
      <div class="wb-history__head">
        <h3 class="wb-history__title"><Icon name="clock" :size="15" class="wb-panel__ico" />{{ t(historyTitleKey) }}</h3>
        <RouterLink :to="worksKind ? `/works?kind=${worksKind}` : '/works'" class="wb-history__link">
          {{ t('uiViewAllWorks') }} <Icon name="chevronRight" :size="14" />
        </RouterLink>
      </div>
      <slot name="history">
        <div v-if="historyItems?.length" class="wb-history__strip wf-scrollbar">
          <button
            v-for="it in historyItems"
            :key="it.key"
            type="button"
            class="wb-history__item"
            :class="{ 'wb-history__item--active': it.active }"
            @click="emit('history-select', it)"
          >
            <img v-if="it.thumb" :src="it.thumb" alt="" loading="lazy" />
            <span
              v-if="it.badge"
              class="wb-history__badge"
              :class="it.status ? 'wb-history__badge--' + it.status : ''"
            >{{ it.badge }}</span>
          </button>
        </div>
        <p v-else class="wb-history__empty">
          <template v-if="historyLoggedOut">
            <RouterLink :to="'/login?return=' + encodeURIComponent(route.fullPath)">{{ t('login') }}</RouterLink> ·
            {{ t('uiNeedLogin') }}
          </template>
          <template v-else>{{ t('uiHistoryEmpty') }}</template>
        </p>
      </slot>
    </section>

    <slot />
  </div>
</template>
