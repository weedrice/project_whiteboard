<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  padding?: 'none' | 'sm' | 'md'
  shadow?: boolean | 'sm'
  border?: 'default' | 'transparent'
  overflow?: 'visible' | 'hidden'
  maxWidthClass?: string
}>(), {
  padding: 'md',
  shadow: true,
  border: 'default',
  overflow: 'visible',
  maxWidthClass: '',
})

const paddingClass = computed(() => {
  if (props.padding === 'none') return ''
  return props.padding === 'sm' ? 'p-4' : 'p-5'
})
const shadowClass = computed(() => {
  if (props.shadow === 'sm') return 'shadow-sm'
  return props.shadow ? 'shadow' : ''
})
const borderClass = computed(() => (props.border === 'transparent'
  ? 'border-transparent dark:border-gray-700'
  : 'border-gray-200 dark:border-gray-700'))
const overflowClass = computed(() => (props.overflow === 'hidden' ? 'overflow-hidden' : ''))
</script>

<template>
  <div
    :class="[
      maxWidthClass,
      paddingClass,
      shadowClass,
      borderClass,
      overflowClass,
      'rounded-lg border bg-white dark:bg-gray-800',
    ]"
  >
    <slot />
  </div>
</template>
