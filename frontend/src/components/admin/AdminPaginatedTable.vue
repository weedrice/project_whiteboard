<script setup lang="ts" generic="T extends object">
import { computed, useSlots } from 'vue'
import BaseTable, { type TableColumn } from '@/components/common/ui/BaseTable.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'

type AdminTableRowKeyResolver<TItem> = Extract<keyof TItem, string> | ((item: TItem, index: number) => string | number)

const props = withDefaults(defineProps<{
  columns: TableColumn[]
  items: T[]
  loading?: boolean
  emptyText?: string
  rowKey?: AdminTableRowKeyResolver<T>
  rowClass?: (item: T) => string
  page?: number
  totalPages?: number
  totalElements?: number
  summary?: string
  loadingText?: string
  footerLoading?: boolean
  tableClass?: string
  showFooter?: boolean
}>(), {
  loading: false,
  emptyText: 'No data available',
  rowKey: undefined,
  rowClass: undefined,
  page: 0,
  totalPages: 0,
  totalElements: undefined,
  summary: '',
  loadingText: undefined,
  footerLoading: undefined,
  tableClass: 'mt-4',
  showFooter: true,
})

const emit = defineEmits<{
  sort: [key: string]
  rowClick: [item: T]
  rowDblclick: [item: T]
  pageChange: [page: number]
}>()

const slots = useSlots()
const tableSlotNames = computed(() => Object.keys(slots).filter((name) => !name.startsWith('footer-')))
</script>

<template>
  <BaseTable
    :class="tableClass"
    :columns="columns"
    :items="items"
    :loading="loading"
    :empty-text="emptyText"
    :row-key="rowKey"
    :row-class="rowClass"
    @sort="emit('sort', $event)"
    @row-click="emit('rowClick', $event)"
    @row-dblclick="emit('rowDblclick', $event)"
  >
    <template v-for="slotName in tableSlotNames" #[slotName]="slotProps">
      <slot :name="slotName" v-bind="slotProps" />
    </template>
  </BaseTable>

  <AdminPaginationFooter
    v-if="showFooter"
    :page="page"
    :total-pages="totalPages"
    :total-elements="totalElements"
    :summary="summary"
    :loading="footerLoading ?? loading"
    :loading-text="loadingText"
    @page-change="emit('pageChange', $event)"
  >
    <template v-if="$slots['footer-description']" #description>
      <slot name="footer-description" />
    </template>
    <template v-if="$slots['footer-actions']" #actions>
      <slot name="footer-actions" />
    </template>
  </AdminPaginationFooter>
</template>
