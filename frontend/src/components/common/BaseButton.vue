<template>
  <button :type="type" :class="[
    btnClass,
    sizeClass,
    'flex justify-center items-center',
    disabled ? 'opacity-50 cursor-not-allowed' : ''
  ]" :disabled="disabled" @click="$emit('click', $event)">
    <slot></slot>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type ButtonType = 'button' | 'submit' | 'reset'
type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'

const props = withDefaults(defineProps<{
  type?: ButtonType
  variant?: ButtonVariant
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
}>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  disabled: false
})

defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()

const btnClass = computed(() => {
  switch (props.variant) {
    case 'primary':
      return 'btn-primary'
    case 'secondary':
      return 'btn-secondary'
    case 'danger':
      return 'btn-danger'
    case 'ghost':
      return 'btn-ghost'
    default:
      return 'btn-primary'
  }
})

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
