<script setup lang="ts">
import type { Component } from 'vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import Pagination from '@/components/common/ui/Pagination.vue'

defineProps<{
  title: string
  icon: Component
  error?: string | null
  itemCount: number
  emptyTitle: string
  currentPage: number
  totalPages: number
  withBottomSpacing?: boolean
}>()

const emit = defineEmits<{
  pageChange: [page: number]
}>()
</script>

<template>
  <div
    class="nv-surface shadow overflow-hidden sm:rounded-lg transition-colors duration-200"
    :class="withBottomSpacing ? 'mb-6 pb-6' : ''"
  >
    <div class="px-4 py-4 sm:py-5 sm:px-6 border-b nv-border flex items-center">
      <component :is="icon" class="h-5 w-5 nv-text-subtle mr-2 flex-shrink-0" />
      <h3 class="text-lg leading-6 font-medium nv-title">{{ title }}</h3>
    </div>

    <div
      v-if="error"
      class="mx-4 mt-4 rounded border px-4 py-3 text-sm nv-danger-surface"
    >
      {{ error }}
    </div>
    <div v-else-if="itemCount > 0">
      <slot />
      <div v-if="totalPages > 0" class="nv-surface-muted px-4 py-4 sm:px-6 flex justify-center">
        <Pagination
          :current-page="currentPage"
          :total-pages="totalPages"
          @page-change="emit('pageChange', $event)"
        />
      </div>
    </div>
    <EmptyState v-else :title="emptyTitle" :icon="icon" />
  </div>
</template>
