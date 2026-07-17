<script setup lang="ts">
import type { Component } from 'vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'

defineProps<{
  title: string
  icon: Component
  error?: string | null
  itemCount: number
  emptyTitle: string
  currentPage: number
  totalPages: number
  loading?: boolean
  withBottomSpacing?: boolean
}>()

const emit = defineEmits<{
  pageChange: [page: number]
  retry: []
}>()
</script>

<template>
  <BaseCard
    no-padding
    :class="withBottomSpacing ? 'mb-6 pb-6' : ''"
    :aria-busy="loading"
  >
    <div class="px-4 py-4 sm:py-5 sm:px-6 border-b nv-border flex items-center">
      <component :is="icon" class="h-5 w-5 nv-text-subtle mr-2 flex-shrink-0" />
      <h3 class="text-lg leading-6 font-medium nv-title">{{ title }}</h3>
    </div>

    <ErrorState
      v-if="error"
      title-tag="h4"
      :message="error"
      :show-icon="false"
      show-retry
      @retry="emit('retry')"
    />
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
    <EmptyState v-else title-tag="h4" :title="emptyTitle" :icon="icon" />
  </BaseCard>
</template>
