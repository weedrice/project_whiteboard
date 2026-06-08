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
  error: undefined,
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
      <div class="flex flex-col items-center justify-center gap-2 text-sm nv-text-subtle">
        <BaseSpinner size="lg" />
        <span v-if="loadingText">{{ loadingText }}</span>
      </div>
    </slot>
  </div>

  <div v-else-if="error" :class="paddingClass">
    <slot name="error">
      <div class="rounded nv-status-danger px-4 py-3 text-sm">
        {{ errorText }}
      </div>
    </slot>
  </div>

  <div v-else-if="empty" :class="paddingClass">
    <slot name="empty">
      <p v-if="emptyText" class="text-center text-sm nv-text-subtle">{{ emptyText }}</p>
    </slot>
  </div>

  <slot v-else />
</template>
