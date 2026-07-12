<script setup lang="ts">
import { useI18n } from 'vue-i18n'

withDefaults(defineProps<{
  count?: number
  ranked?: boolean
}>(), {
  count: 10,
  ranked: false,
})

const { t } = useI18n()
</script>

<template>
  <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4"
    role="status" aria-live="polite" aria-busy="true" :aria-label="t('common.loading')">
    <div
      v-for="i in count"
      :key="i"
      aria-hidden="true"
      class="relative nv-surface rounded-lg shadow-sm border nv-border overflow-hidden animate-pulse"
    >
      <div v-if="ranked" class="absolute top-2 left-2 z-10 w-6 h-6 nv-surface-muted rounded-full"></div>
      <div class="aspect-square nv-surface-muted"></div>
      <div class="p-3 space-y-2">
        <div class="h-4 nv-surface-muted rounded w-3/4"></div>
        <div class="h-3 nv-surface-muted rounded w-1/2"></div>
        <div class="h-3 nv-surface-muted rounded w-2/3"></div>
      </div>
    </div>
  </div>
</template>
