<script setup lang="ts" generic="T extends object">
import { computed, useSlots } from 'vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import type {
  RowActionLabelResolver,
  RowActivationEvent,
  RowKeyResolver,
  SortDirection,
  TableColumn,
} from '@/components/common/ui/baseTableModel'

withDefaults(defineProps<{
  columns: TableColumn<Extract<keyof T, string> | string>[]
  items: T[]
  loading?: boolean
  emptyText?: string
  rowKey?: RowKeyResolver<T>
  rowClass?: (item: T) => string
  interactiveRows?: boolean
  rowActionLabel?: RowActionLabelResolver<T>
  rowActivationEvent?: RowActivationEvent
  currentSortKey?: string | null
  currentSortDirection?: SortDirection
  page?: number
  totalPages?: number
  totalElements?: number
  summary?: string
  loadingText?: string
  footerLoading?: boolean
  tableClass?: string
  showFooter?: boolean
  minWidthClass?: string
}>(), {
  loading: false,
  emptyText: undefined,
  rowKey: undefined,
  rowClass: undefined,
  interactiveRows: false,
  rowActionLabel: undefined,
  rowActivationEvent: 'row-click',
  currentSortKey: null,
  currentSortDirection: null,
  page: 0,
  totalPages: 0,
  totalElements: undefined,
  summary: '',
  loadingText: undefined,
  footerLoading: undefined,
  tableClass: 'mt-4',
  showFooter: true,
  minWidthClass: 'min-w-[48rem]',
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
    :interactive-rows="interactiveRows"
    :row-action-label="rowActionLabel"
    :row-activation-event="rowActivationEvent"
    :current-sort-key="currentSortKey"
    :current-sort-direction="currentSortDirection"
    :min-width-class="minWidthClass"
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
