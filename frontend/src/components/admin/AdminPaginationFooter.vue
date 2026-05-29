<script setup lang="ts">
import { useSlots } from 'vue'
import Pagination from '@/components/common/ui/Pagination.vue'

withDefaults(defineProps<{
  page: number
  totalPages: number
  totalElements?: number
  loading?: boolean
  summary?: string
  loadingText?: string
}>(), {
  totalElements: undefined,
  loading: false,
  summary: '',
  loadingText: '로딩 중...',
})

const emit = defineEmits<{
  'page-change': [page: number]
}>()

const slots = useSlots()
</script>

<template>
  <div
    v-if="summary || totalElements !== undefined || totalPages > 0 || slots.actions || slots.description"
    class="mt-4 flex flex-col gap-3 rounded-lg border nv-border nv-surface px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between"
  >
    <div class="nv-text-muted">
      <div v-if="summary">{{ summary }}</div>
      <div v-else-if="totalElements !== undefined">총 {{ totalElements.toLocaleString() }}건</div>
      <p v-if="loading" class="mt-1 text-xs nv-text-subtle">{{ loadingText }}</p>
      <slot name="description" />
    </div>

    <div class="flex flex-wrap items-center gap-2">
      <slot name="actions" />
      <Pagination
        v-if="totalPages > 0"
        :current-page="page"
        :total-pages="totalPages"
        @page-change="emit('page-change', $event)"
      />
    </div>
  </div>
</template>
