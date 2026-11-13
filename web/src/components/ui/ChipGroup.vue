<script setup>
/**
 * options: [{ value, label, sub?, disabled? }]
 * variant: chips（默认）| seg（分段）| cards（卡片）
 */
defineProps({
  modelValue: { type: [String, Number, Boolean], default: null },
  options: { type: Array, default: () => [] },
  variant: { type: String, default: 'chips' },
  cols: { type: Number, default: 0 },
});
const emit = defineEmits(['update:modelValue', 'change']);

function pick(o) {
  if (o.disabled) return;
  emit('update:modelValue', o.value);
  emit('change', o.value);
}
</script>

<template>
  <div
    class="chip-group"
    :class="{ 'chip-group--seg': variant === 'seg', 'chip-group--cards': variant === 'cards' }"
    :style="cols ? { display: 'grid', gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` } : null"
    role="radiogroup"
  >
    <button
      v-for="o in options"
      :key="String(o.value)"
      type="button"
      class="chip"
      :class="{ 'chip--active': modelValue === o.value }"
      role="radio"
      :aria-checked="modelValue === o.value"
      :disabled="o.disabled"
      @click="pick(o)"
    >
      <slot name="option" :option="o" :active="modelValue === o.value">
        <span>{{ o.label }}</span>
        <small v-if="o.sub" class="chip__sub">{{ o.sub }}</small>
      </slot>
    </button>
  </div>
</template>
