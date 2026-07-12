<script setup lang="ts" generic="T extends object">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseSpinner from './BaseSpinner.vue'
import {
    getBaseTableRowActionLabel,
    getTableAlignButtonClass,
    getTableAlignClass,
    getTableAriaSort,
    getTableCellValue,
    getTableResponsiveClass,
    getTableSortButtonLabel,
    getTableSortIndicator,
    resolveBaseTableRowKey,
    type RowActionLabelResolver,
    type RowActivationEvent,
    type RowKeyResolver,
    type TableColumn,
} from './baseTableModel'
import logger from '@/utils/logger'

export type { TableColumn } from './baseTableModel'

const props = withDefaults(defineProps<{
    columns: TableColumn[]
    items: T[]
    loading?: boolean
    emptyText?: string
    density?: 'default' | 'compact'
    shadow?: boolean
    maxHeightClass?: string
    currentSortKey?: string | null
    currentSortDirection?: 'asc' | 'desc' | null
    rowClass?: (item: T) => string
    rowKey?: RowKeyResolver<T>
    interactiveRows?: boolean
    rowActionLabel?: RowActionLabelResolver<T>
    rowActivationEvent?: RowActivationEvent
    minWidthClass?: string
    caption?: string
    scrollLabel?: string
}>(), {
    loading: false,
    emptyText: undefined,
    density: 'default',
    shadow: true,
    maxHeightClass: undefined,
    currentSortKey: null,
    currentSortDirection: null,
    rowClass: undefined,
    rowKey: undefined,
    interactiveRows: false,
    rowActionLabel: undefined,
    rowActivationEvent: 'row-click',
    minWidthClass: 'min-w-[48rem]',
    caption: undefined,
    scrollLabel: undefined,
})

const emit = defineEmits<{
    (e: 'sort', key: string): void
    (e: 'row-click', item: T): void
    (e: 'row-dblclick', item: T): void
}>()

const { t } = useI18n()

const effectiveEmptyText = computed(() => props.emptyText ?? t('common.noData'))
const effectiveScrollLabel = computed(() => props.scrollLabel ?? props.caption)

const getAriaSort = (column: TableColumn): 'ascending' | 'descending' | 'none' | undefined => {
    return getTableAriaSort(column, props.currentSortKey, props.currentSortDirection)
}

const getSortIndicator = (column: TableColumn): string => {
    return getTableSortIndicator(column, props.currentSortKey, props.currentSortDirection)
}

const getSortButtonLabel = (column: TableColumn): string => {
    return getTableSortButtonLabel(column, props.currentSortKey, props.currentSortDirection, t('common.sortOrder'))
}

const getRowKey = (item: T, index: number): string | number => {
    return resolveBaseTableRowKey({
        item,
        index,
        rowKey: props.rowKey,
        onIndexFallback: import.meta.env.DEV
            ? (fallbackItem) => {
                logger.warn('[BaseTable] Falling back to index row key. Provide rowKey for stable list rendering.', fallbackItem)
            }
            : undefined,
    })
}

const getRowActionLabel = (item: T, index: number): string | undefined => {
    return getBaseTableRowActionLabel(item, index, props.interactiveRows, props.rowActionLabel)
}

const emitRowActivation = (item: T) => {
    if (props.rowActivationEvent === 'row-dblclick') {
        emit('row-dblclick', item)
        return
    }

    emit('row-click', item)
}

const handleRowClick = (item: T) => {
    if (!props.interactiveRows || props.rowActivationEvent !== 'row-click') {
        return
    }

    emit('row-click', item)
}

const handleRowDblclick = (item: T) => {
    if (!props.interactiveRows || props.rowActivationEvent !== 'row-dblclick') {
        return
    }

    emit('row-dblclick', item)
}

const handleRowKeydown = (event: KeyboardEvent, item: T) => {
    if (!props.interactiveRows || (event.key !== 'Enter' && event.key !== ' ')) {
        return
    }

    event.preventDefault()
    emitRowActivation(item)
}

const rootClasses = computed(() => [
    'nv-base-table overflow-hidden',
    props.shadow ? 'shadow' : '',
])

const scrollContainerClasses = computed(() => [
    'overflow-x-auto',
    props.maxHeightClass || '',
    props.maxHeightClass ? 'overflow-y-auto' : '',
])

const headerCellClasses = computed(() => [
    'nv-base-table-header text-xs font-medium uppercase tracking-wider whitespace-nowrap',
    props.density === 'compact'
        ? 'px-2 py-2'
        : 'px-3 sm:px-6 py-2 sm:py-3',
])

const bodyCellClasses = computed(() => [
    'nv-base-table-cell whitespace-nowrap text-xs sm:text-sm min-w-0 overflow-hidden align-middle',
    props.density === 'compact'
        ? 'px-2 py-1.5'
        : 'px-3 sm:px-6 py-3 sm:py-4',
])
</script>

<template>
    <div :class="rootClasses">
        <div :class="scrollContainerClasses" :role="effectiveScrollLabel ? 'region' : undefined"
            :aria-label="effectiveScrollLabel" :tabindex="effectiveScrollLabel ? 0 : undefined">
            <table class="w-full table-fixed nv-base-table-table" :class="minWidthClass" style="table-layout: fixed;">
                <caption v-if="caption" class="sr-only">{{ caption }}</caption>
                <colgroup>
                    <col v-for="col in columns" :key="col.key" :class="getTableResponsiveClass(col.hideBelow)" :style="{ width: col.width || 'auto' }" />
                </colgroup>
                <thead class="nv-base-table-head">
                    <tr>
                        <th v-for="col in columns" :key="col.key" scope="col"
                            :aria-sort="getAriaSort(col)"
                            :class="[headerCellClasses, getTableAlignClass(col.align), getTableResponsiveClass(col.hideBelow)]"
                            :style="{ width: col.width }">
                            <button
                                v-if="col.sortable"
                                type="button"
                                class="nv-base-table-header-button inline-flex w-full items-center gap-2 focus:outline-none focus-visible:ring-2"
                                :class="getTableAlignButtonClass(col.align)"
                                :aria-label="getSortButtonLabel(col)"
                                @click="emit('sort', col.key)">
                                <span aria-hidden="true" class="text-[9px] sm:text-[10px]">
                                    {{ getSortIndicator(col) }}
                                </span>
                                <span>{{ col.label }}</span>
                            </button>
                            <span v-else>{{ col.label }}</span>
                        </th>
                    </tr>
                </thead>
                <tbody class="nv-base-table-body">
                    <tr v-if="loading">
                        <td :colspan="columns.length"
                            class="nv-base-table-status px-3 sm:px-6 py-6 sm:py-10 text-center text-xs sm:text-sm">
                            <div class="flex justify-center" role="status" aria-live="polite">
                                <slot name="loading">
                                    <div class="nv-base-table-spinner h-6 w-6 flex items-center justify-center" aria-hidden="true">
                                        <BaseSpinner size="sm" color="text-[var(--nv-accent)]" class="scale-150" />
                                    </div>
                                    <span class="sr-only">{{ t('common.loading') }}</span>
                                </slot>
                            </div>
                        </td>
                    </tr>
                    <tr v-else-if="items.length === 0">
                        <td :colspan="columns.length"
                            class="nv-base-table-status px-3 sm:px-6 py-6 sm:py-10 text-center text-xs sm:text-sm">
                            {{ effectiveEmptyText }}
                        </td>
                    </tr>
                    <template v-else>
                        <tr v-for="(item, index) in items" :key="getRowKey(item, index)"
                            class="nv-base-table-row transition-colors duration-150"
                            :class="rowClass?.(item) || ''"
                            :tabindex="interactiveRows ? 0 : undefined"
                            :aria-label="getRowActionLabel(item, index)"
                            @click="handleRowClick(item)"
                            @dblclick="handleRowDblclick(item)"
                            @keydown="handleRowKeydown($event, item)">
                            <td v-for="col in columns" :key="col.key"
                                :class="[bodyCellClasses, getTableAlignClass(col.align), getTableResponsiveClass(col.hideBelow)]">
                                <slot :name="`cell-${col.key}`" :item="item" :value="getTableCellValue(item, col.key)">
                                    {{ getTableCellValue(item, col.key) }}
                                </slot>
                            </td>
                        </tr>
                    </template>
                </tbody>
            </table>
        </div>
    </div>
</template>

<style scoped>
.nv-base-table {
    background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
    border: 1px solid var(--nv-line);
}

.nv-base-table-table {
    border-color: var(--nv-line);
}

.nv-base-table-head {
    background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
    border-radius: 0;
}

.nv-base-table-body {
    background: color-mix(in srgb, var(--nv-surface) 98%, transparent);
    border-top: 1px solid var(--nv-line);
}

.nv-base-table-header {
    color: var(--nv-muted);
}

.nv-base-table-header-button {
    color: inherit;
    transition: color 0.2s ease, box-shadow 0.2s ease;
}

.nv-base-table-header-button:hover {
    color: var(--nv-ink);
}

.nv-base-table-header-button:focus-visible {
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--nv-accent) 38%, transparent);
}

.nv-base-table-cell {
    color: var(--nv-ink);
}

.nv-base-table-status {
    color: var(--nv-muted);
}

.nv-base-table-spinner {
    border-color: color-mix(in srgb, var(--nv-accent) 24%, transparent);
    border-bottom-color: var(--nv-accent);
}

.nv-base-table-row:hover {
    background: color-mix(in srgb, var(--nv-surface-2) 78%, transparent);
}
</style>
