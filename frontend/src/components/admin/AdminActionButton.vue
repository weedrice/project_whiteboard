<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  title?: string
  tone?: 'neutral' | 'accent' | 'success' | 'danger'
  iconOnly?: boolean
  disabled?: boolean
  type?: 'button' | 'submit' | 'reset'
}>(), {
  title: undefined,
  tone: 'neutral',
  iconOnly: false,
  disabled: false,
  type: 'button',
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const toneClasses = {
  neutral: 'nv-text-subtle hover:text-[var(--nv-ink)]',
  accent: 'nv-accent-text hover:brightness-95',
  success: 'text-[var(--nv-success-text)] hover:text-[var(--nv-success-text)]',
  danger: 'text-[var(--nv-danger-text)] hover:brightness-95',
}
</script>

<template>
  <button
    :type="type"
    :title="title || label"
    :aria-label="label"
    :disabled="disabled"
    :class="[
      'admin-action-button inline-flex items-center justify-center rounded transition-colors disabled:cursor-not-allowed disabled:opacity-50',
      iconOnly ? 'btn-icon p-1' : 'px-2 py-1 text-sm font-medium',
      toneClasses[tone],
    ]"
    @click="emit('click', $event)"
  >
    <slot />
    <span v-if="!iconOnly && !$slots.default">{{ label }}</span>
  </button>
</template>
