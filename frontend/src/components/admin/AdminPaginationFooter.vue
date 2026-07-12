<script setup lang="ts">
import { useSlots } from 'vue'
import { useI18n } from 'vue-i18n'
import Pagination from '@/components/common/ui/Pagination.vue'
import { formatInteger } from '@/utils/numberFormat'

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
})
const { t } = useI18n()

const emit = defineEmits<{
  'page-change': [page: number]
}>()

const slots = useSlots()
</script>

<template>
  <div
    v-if="summary || totalElements !== undefined || totalPages > 0 || slots.actions || slots.description"
    class="nv-admin-pagination mt-4 flex flex-col gap-3 border nv-border nv-surface px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between"
  >
    <div class="nv-text-muted">
      <div v-if="summary">{{ summary }}</div>
      <div v-else-if="totalElements !== undefined">{{ t('common.paginationSummary.total', { count: formatInteger(totalElements), unit: t('common.paginationSummary.itemUnit') }) }}</div>
      <p v-if="loading" class="mt-1 text-xs nv-text-subtle">{{ loadingText ?? t('common.loading') }}</p>
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

<style scoped>
.nv-admin-pagination {
  border-radius: var(--nv-radius-lg);
}
</style>
