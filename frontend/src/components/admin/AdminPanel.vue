<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  padding?: 'none' | 'sm' | 'md'
  shadow?: boolean
  maxWidthClass?: string
}>(), {
  padding: 'md',
  shadow: true,
  maxWidthClass: '',
})

const paddingClass = computed(() => {
  if (props.padding === 'none') return ''
  return props.padding === 'sm' ? 'p-4' : 'p-5'
})
const shadowClass = computed(() => (props.shadow ? 'shadow' : ''))
</script>

<template>
  <div
    :class="[
      maxWidthClass,
      paddingClass,
      shadowClass,
      'rounded-lg border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800',
    ]"
  >
    <slot />
  </div>
</template>
