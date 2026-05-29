<script setup lang="ts" generic="T extends object">
import { computed } from 'vue'

export interface TableColumn {
    key: string
    label: string
    align?: 'left' | 'center' | 'right'
    width?: string
    sortable?: boolean
}

type RowKeyResolver<TItem> = Extract<keyof TItem, string> | ((item: TItem, index: number) => string | number)

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
}>(), {
    loading: false,
    emptyText: 'No data available',
    density: 'default',
    shadow: true,
    maxHeightClass: undefined,
    currentSortKey: null,
    currentSortDirection: null,
    rowClass: undefined,
    rowKey: undefined
})

const emit = defineEmits<{
    (e: 'sort', key: string): void
    (e: 'row-click', item: T): void
    (e: 'row-dblclick', item: T): void
}>()

const alignClass = (align?: string) => {
    switch (align) {
        case 'center': return 'text-center'
        case 'right': return 'text-right'
        default: return 'text-left'
    }
}

const alignButtonClass = (align?: string) => {
    switch (align) {
        case 'center': return 'justify-center'
        case 'right': return 'justify-end'
        default: return 'justify-start'
    }
}

const getAriaSort = (column: TableColumn): 'ascending' | 'descending' | 'none' | undefined => {
    if (!column.sortable) {
        return undefined
    }

    if (props.currentSortKey !== column.key) {
        return 'none'
    }

    return props.currentSortDirection === 'asc' ? 'ascending' : 'descending'
}

const getSortIndicator = (column: TableColumn): string => {
    if (props.currentSortKey !== column.key) {
        return ''
    }

    return props.currentSortDirection === 'asc' ? '^' : 'v'
}

const getRowKey = (item: T, index: number): string | number => {
    if (typeof props.rowKey === 'function') {
        return props.rowKey(item, index)
    }

    const record = item as Record<string, unknown>

    if (typeof props.rowKey === 'string') {
        const resolvedKey = record[props.rowKey]
        if (typeof resolvedKey === 'string' || typeof resolvedKey === 'number') {
            return resolvedKey
        }
    }

    const defaultKey = record.id
        ?? record.postId
        ?? record.key
        ?? record.userId
        ?? record.reportId
        ?? record.ipAddress
        ?? record.adminId
        ?? record.errorLogId
    if (typeof defaultKey === 'string' || typeof defaultKey === 'number') {
        return defaultKey
    }

    if (import.meta.env.DEV) {
        console.warn('[BaseTable] Falling back to index row key. Provide rowKey for stable list rendering.', item)
    }

    return index
}

const getCellValue = (item: T, key: string): unknown => {
    const record = item as Record<string, unknown>
    return record[key]
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
    'nv-base-table-header text-[10px] sm:text-xs font-medium uppercase tracking-wider whitespace-nowrap',
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
        <div :class="scrollContainerClasses">
            <table class="min-w-full table-fixed nv-base-table-table" style="table-layout: fixed;">
                <colgroup>
                    <col v-for="col in columns" :key="col.key" :style="{ width: col.width || 'auto' }" />
                </colgroup>
                <thead class="nv-base-table-head">
                    <tr>
                        <th v-for="col in columns" :key="col.key" scope="col"
                            :aria-sort="getAriaSort(col)"
                            :class="[headerCellClasses, alignClass(col.align)]"
                            :style="{ width: col.width }">
                            <button
                                v-if="col.sortable"
                                type="button"
                                class="nv-base-table-header-button inline-flex w-full items-center gap-2 focus:outline-none focus-visible:ring-2"
                                :class="alignButtonClass(col.align)"
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
                                    <div class="nv-base-table-spinner animate-spin rounded-full h-6 w-6 border-b-2" aria-hidden="true"></div>
                                    <span class="sr-only">Loading...</span>
                                </slot>
                            </div>
                        </td>
                    </tr>
                    <tr v-else-if="items.length === 0">
                        <td :colspan="columns.length"
                            class="nv-base-table-status px-3 sm:px-6 py-6 sm:py-10 text-center text-xs sm:text-sm">
                            {{ emptyText }}
                        </td>
                    </tr>
                    <template v-else>
                        <tr v-for="(item, index) in items" :key="getRowKey(item, index)"
                            class="nv-base-table-row transition-colors duration-150"
                            :class="rowClass?.(item) || ''"
                            @click="emit('row-click', item)"
                            @dblclick="emit('row-dblclick', item)">
                            <td v-for="col in columns" :key="col.key"
                                :class="[bodyCellClasses, alignClass(col.align)]">
                                <slot :name="`cell-${col.key}`" :item="item" :value="getCellValue(item, col.key)">
                                    {{ getCellValue(item, col.key) }}
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
