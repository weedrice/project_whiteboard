<script setup lang="ts" generic="T extends object">

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
    currentSortKey?: string | null
    currentSortDirection?: 'asc' | 'desc' | null
    rowClass?: (item: T) => string
    rowKey?: RowKeyResolver<T>
}>(), {
    loading: false,
    emptyText: 'No data available',
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
        return '-'
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

    const defaultKey = record.id ?? record.postId
    if (typeof defaultKey === 'string' || typeof defaultKey === 'number') {
        return defaultKey
    }

    return index
}

const getCellValue = (item: T, key: string): unknown => {
    const record = item as Record<string, unknown>
    return record[key]
}
</script>

<template>
    <div
        class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg border border-gray-200 dark:border-gray-700">
        <div class="overflow-x-auto">
            <table class="min-w-full table-fixed divide-y divide-gray-200 dark:divide-gray-700" style="table-layout: fixed;">
                <colgroup>
                    <col v-for="col in columns" :key="col.key" :style="{ width: col.width || 'auto' }" />
                </colgroup>
                <thead class="bg-gray-50 dark:bg-gray-700">
                    <tr>
                        <th v-for="col in columns" :key="col.key" scope="col"
                            :aria-sort="getAriaSort(col)"
                            class="px-3 sm:px-6 py-2 sm:py-3 text-[10px] sm:text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider whitespace-nowrap"
                            :class="alignClass(col.align)"
                            :style="{ width: col.width }">
                            <button
                                v-if="col.sortable"
                                type="button"
                                class="inline-flex w-full items-center gap-2 hover:text-gray-700 dark:hover:text-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                                :class="alignButtonClass(col.align)"
                                @click="emit('sort', col.key)">
                                <span>{{ col.label }}</span>
                                <span aria-hidden="true" class="text-[9px] sm:text-[10px]">
                                    {{ getSortIndicator(col) }}
                                </span>
                            </button>
                            <span v-else>{{ col.label }}</span>
                        </th>
                    </tr>
                </thead>
                <tbody class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                    <tr v-if="loading">
                        <td :colspan="columns.length"
                            class="px-3 sm:px-6 py-6 sm:py-10 text-center text-xs sm:text-sm text-gray-500 dark:text-gray-400">
                            <div class="flex justify-center">
                                <slot name="loading">
                                    <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div>
                                </slot>
                            </div>
                        </td>
                    </tr>
                    <tr v-else-if="items.length === 0">
                        <td :colspan="columns.length"
                            class="px-3 sm:px-6 py-6 sm:py-10 text-center text-xs sm:text-sm text-gray-500 dark:text-gray-400">
                            {{ emptyText }}
                        </td>
                    </tr>
                    <tr v-else v-for="(item, index) in items" :key="getRowKey(item, index)"
                        class="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-150"
                        :class="rowClass?.(item) || ''"
                        @click="emit('row-click', item)"
                        @dblclick="emit('row-dblclick', item)">
                        <td v-for="col in columns" :key="col.key"
                            class="px-3 sm:px-6 py-3 sm:py-4 whitespace-nowrap text-xs sm:text-sm text-gray-900 dark:text-white min-w-0 overflow-hidden align-middle"
                            :class="alignClass(col.align)">
                            <slot :name="`cell-${col.key}`" :item="item" :value="getCellValue(item, col.key)">
                                {{ getCellValue(item, col.key) }}
                            </slot>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</template>
