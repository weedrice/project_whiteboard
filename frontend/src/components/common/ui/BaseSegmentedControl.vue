<script setup lang="ts">
import type { Component } from 'vue'

export interface SegmentedControlOption {
  value: string
  label: string
  icon?: Component
}

withDefaults(defineProps<{
  modelValue: string
  options: SegmentedControlOption[]
  label: string
  variant?: 'joined' | 'underline' | 'pill'
  selectionMode?: 'pressed' | 'tab'
  disabled?: boolean
}>(), {
  variant: 'joined',
  selectionMode: 'pressed',
  disabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

function select(value: string) {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <div
    :class="[
      variant === 'joined' && 'isolate inline-flex rounded-lg sm:rounded-md shadow-sm w-full sm:w-auto',
      variant === 'underline' && 'flex border-b nv-border',
      variant === 'pill' && 'flex items-center gap-1 rounded-full border border-[var(--nv-line)] bg-[var(--nv-surface)] p-1',
    ]"
    :role="selectionMode === 'tab' ? 'tablist' : 'group'"
    :aria-label="label"
  >
    <button
      v-for="(option, index) in options"
      :key="option.value"
      type="button"
      :role="selectionMode === 'tab' ? 'tab' : undefined"
      :aria-selected="selectionMode === 'tab' ? modelValue === option.value : undefined"
      :aria-pressed="selectionMode === 'pressed' ? modelValue === option.value : undefined"
      :disabled="disabled"
      :class="[
        variant === 'joined' && [
          'flex-1 sm:flex-initial relative inline-flex items-center justify-center px-3 py-2.5 sm:py-2 text-sm font-medium ring-1 ring-inset min-h-[44px] sm:min-h-0 focus:z-10',
          index === 0 && 'rounded-l-lg sm:rounded-l-md',
          index === options.length - 1 && 'rounded-r-lg sm:rounded-r-md',
          index > 0 && '-ml-px',
          modelValue === option.value
            ? 'bg-[var(--nv-accent)] border-[var(--nv-accent)] text-white hover:brightness-95'
            : 'nv-surface border-[var(--nv-border-strong)] nv-text hover:nv-surface-hover',
        ],
        variant === 'underline' && [
          'flex-1 rounded-b-none border-b-2 px-4 py-2 inline-flex items-center justify-center text-sm font-medium transition-colors',
          modelValue === option.value
            ? 'border-[var(--nv-accent)] text-[var(--nv-accent)]'
            : 'border-transparent nv-text-subtle hover:text-[var(--nv-text)]',
        ],
        variant === 'pill' && [
          'rounded-full px-3 py-1.5 text-[11px] font-medium uppercase tracking-[0.12em] transition-colors',
          modelValue === option.value
            ? 'bg-[var(--nv-ink)] text-[var(--nv-bg)]'
            : 'text-[var(--nv-ink-soft)] hover:text-[var(--nv-ink)]',
        ],
      ]"
      @click="select(option.value)"
    >
      <component v-if="option.icon" :is="option.icon" class="mr-2 h-4 w-4" />
      {{ option.label }}
    </button>
  </div>
</template>
