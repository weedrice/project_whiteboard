<script setup lang="ts">
import { computed, useSlots, type Component } from 'vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'

const props = withDefaults(defineProps<{
  title: string
  icon: Component
  itemsCount: number
  loading: boolean
  error: string | null
  emptyTitle: string
  page: number
  size: number
  totalPages: number
  maxWidthClass?: string
  headerClass?: string
  pageSizeOptions?: number[]
  actionsVisibility?: 'desktop' | 'always'
  titleTag?: 'h1' | 'h2' | 'h3'
}>(), {
  maxWidthClass: 'max-w-4xl',
  headerClass: 'px-4 py-4 sm:py-5 sm:px-6 gap-3',
  pageSizeOptions: () => [15, 30, 50],
  actionsVisibility: 'desktop',
  titleTag: 'h3',
})

const emit = defineEmits<{
  retry: []
  'page-change': [page: number]
  'size-change': [size: number]
}>()

const slots = useSlots()
const headerActionsClass = computed(() => props.actionsVisibility === 'always'
  ? 'flex items-center gap-2'
  : 'hidden sm:flex sm:items-center sm:gap-2')
</script>

<template>
  <div :class="[maxWidthClass, 'mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8']">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div
        :class="[
          headerClass,
          'flex flex-col sm:flex-row sm:justify-between sm:items-center border-b border-gray-200 dark:border-gray-700',
        ]"
      >
        <component :is="titleTag" class="text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center">
          <component :is="icon" class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
          {{ title }}
        </component>
        <div class="flex items-center gap-2">
          <PageSizeSelector
            :model-value="size"
            :options="pageSizeOptions"
            class="hidden sm:flex"
            @update:modelValue="emit('size-change', $event)"
          />
          <div v-if="slots['header-actions']" :class="headerActionsClass">
            <slot name="header-actions" />
          </div>
        </div>
      </div>

      <div v-if="slots.subheader" class="border-b border-gray-200 dark:border-gray-700">
        <div class="px-4 py-3 sm:px-6">
          <slot name="subheader" />
        </div>
      </div>

      <slot v-if="loading && itemsCount === 0" name="loading" />
      <ErrorState v-else-if="error" :message="error" show-retry @retry="emit('retry')" />
      <EmptyState v-else-if="itemsCount === 0" :title="emptyTitle" :icon="icon" />
      <slot v-else />

      <div v-if="itemsCount > 0" class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex flex-col items-center">
        <slot name="footer-meta" />
        <Pagination :current-page="page" :total-pages="totalPages" @page-change="emit('page-change', $event)" />
      </div>
    </div>
  </div>
</template>
