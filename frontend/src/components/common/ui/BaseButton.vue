<template>
  <component
    :is="to ? RouterLink : 'button'"
    :to="to"
    :type="to ? undefined : type"
    :class="[
    btnClass,
    sizeClass,
    'flex justify-center items-center',
    loading ? 'gap-2' : '',
    isDisabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'
  ]"
    :disabled="to ? undefined : isDisabled"
    :aria-disabled="to && isDisabled ? 'true' : undefined"
    :tabindex="to && isDisabled ? -1 : undefined"
    :aria-busy="loading ? 'true' : undefined"
    @click="handleClick"
  >
    <BaseSpinner
      v-if="loading"
      size="sm"
      color="border-current"
      aria-hidden="true"
      class="shrink-0"
    />
    <slot></slot>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
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
  to?: RouteLocationRaw
}>(), {
  type: 'button',
  variant: 'primary',
  size: 'md',
  disabled: false,
  fullWidth: false,
  loading: false
})

const emit = defineEmits<{
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

const handleClick = (event: MouseEvent) => {
  if (isDisabled.value) {
    event.preventDefault()
    event.stopImmediatePropagation()
    return
  }
  emit('click', event)
}

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
