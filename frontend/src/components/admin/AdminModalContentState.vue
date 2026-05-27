<script setup lang="ts">
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'

withDefaults(defineProps<{
  loading?: boolean
  error?: unknown
  empty?: boolean
  loadingText?: string
  errorText?: string
  emptyText?: string
  paddingClass?: string
}>(), {
  loading: false,
  error: null,
  empty: false,
  loadingText: 'Loading...',
  errorText: 'Failed to load data.',
  emptyText: 'No data available.',
  paddingClass: 'py-10',
})
</script>

<template>
  <div v-if="loading" :class="paddingClass">
    <slot name="loading">
      <div class="flex flex-col items-center justify-center gap-2 text-sm text-gray-500 dark:text-gray-400">
        <BaseSpinner size="lg" />
        <span v-if="loadingText">{{ loadingText }}</span>
      </div>
    </slot>
  </div>

  <div v-else-if="error" :class="paddingClass">
    <slot name="error">
      <div class="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-600 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300">
        {{ errorText }}
      </div>
    </slot>
  </div>

  <div v-else-if="empty" :class="paddingClass">
    <slot name="empty">
      <p v-if="emptyText" class="text-center text-sm text-gray-500 dark:text-gray-400">{{ emptyText }}</p>
    </slot>
  </div>

  <slot v-else />
</template>
