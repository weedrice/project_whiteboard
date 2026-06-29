<template>
  <button
    :type="type"
    :class="[
    btnClass,
    sizeClass,
    'flex justify-center items-center',
    loading ? 'gap-2' : '',
    isDisabled ? 'opacity-50 cursor-not-allowed' : ''
  ]"
    :disabled="isDisabled"
    :aria-busy="loading ? 'true' : undefined"
    @click="$emit('click', $event)"
  >
    <BaseSpinner
      v-if="loading"
      size="sm"
      color="border-current"
      aria-hidden="true"
      class="shrink-0"
    />
    <slot></slot>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

type ButtonType = 'button' | 'submit' | 'reset'
type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'

const props = withDefaults(defineProps<{
  type?: ButtonType
  variant?: ButtonVariant
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
  fullWidth?: boolean
  loading?: boolean
}>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  disabled: false,
  fullWidth: false,
  loading: false
})

defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()

const btnClass = computed(() => {
  const base = props.fullWidth ? 'w-full ' : ''
  switch (props.variant) {
    case 'primary':
      return base + 'btn-primary'
    case 'secondary':
      return base + 'btn-secondary'
    case 'danger':
      return base + 'btn-danger'
    case 'ghost':
      return base + 'btn-ghost'
    default:
      return base + 'btn-primary'
  }
})

const isDisabled = computed(() => props.disabled || props.loading)

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm':
      return 'btn-sm'
    case 'lg':
      return 'px-6 py-3 text-base'
    default:
      return ''
  }
})
</script>
