<script setup lang="ts" generic="T extends Record<string, any>">
import { computed } from 'vue'

export interface TableColumn {
    key: string
    label: string
    align?: 'left' | 'center' | 'right'
    width?: string
    sortable?: boolean
}

const props = withDefaults(defineProps<{
    columns: TableColumn[]
    items: T[]
    loading?: boolean
    emptyText?: string
}>(), {
    loading: false,
    emptyText: 'No data available'
})

const emit = defineEmits<{
    (e: 'sort', key: string): void
    (e: 'row-click', item: T): void
}>()

const alignClass = (align?: string) => {
    switch (align) {
        case 'center': return 'text-center'
        case 'right': return 'text-right'
        default: return 'text-left'
    }
}
</script>

<template>
    <div
        class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg border border-gray-200 dark:border-gray-700">
        <div class="overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
                <thead class="bg-gray-50 dark:bg-gray-700">
                    <tr>
                        <th v-for="col in columns" :key="col.key" scope="col"
                            class="px-6 py-3 text-xs font-medium text-gray-500 dark:text-gray-300 uppercase tracking-wider"
                            :class="[alignClass(col.align), { 'cursor-pointer hover:text-gray-700 dark:hover:text-gray-100': col.sortable }]"
                            :style="{ width: col.width }" @click="col.sortable && emit('sort', col.key)">
                            {{ col.label }}
                        </th>
                    </tr>
                </thead>
                <tbody class="bg-white dark:bg-gray-800 divide-y divide-gray-200 dark:divide-gray-700">
                    <tr v-if="loading">
                        <td :colspan="columns.length"
                            class="px-6 py-10 text-center text-sm text-gray-500 dark:text-gray-400">
                            <div class="flex justify-center">
                                <slot name="loading">
                                    <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600"></div>
                                </slot>
                            </div>
                        </td>
                    </tr>
                    <tr v-else-if="items.length === 0">
                        <td :colspan="columns.length"
                            class="px-6 py-10 text-center text-sm text-gray-500 dark:text-gray-400">
                            {{ emptyText }}
                        </td>
                    </tr>
                    <tr v-else v-for="(item, index) in items" :key="index"
                        class="hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors duration-150"
                        @click="emit('row-click', item)">
                        <td v-for="col in columns" :key="col.key"
                            class="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-white"
                            :class="alignClass(col.align)">
                            <slot :name="`cell-${col.key}`" :item="item" :value="item[col.key]">
                                {{ item[col.key] }}
                            </slot>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</template>
