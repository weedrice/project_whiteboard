<script setup lang="ts">
import { computed, useSlots, type Component } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import BaseCard from '@/components/common/ui/BaseCard.vue'

type LoadingPreset =
  | 'none'
  | 'post-list'
  | 'status-list'
  | 'compact-status-list'
  | 'notification-list'
  | 'avatar-action-list'
  | 'message-list'

const props = withDefaults(defineProps<{
  title: string
  icon: Component
  itemsCount: number
  loading: boolean
  error: string | null
  emptyTitle: string
  emptyDescription?: string
  emptyActionLabel?: string
  emptyActionTo?: RouteLocationRaw
  page: number
  size: number
  totalPages: number
  maxWidthClass?: string
  headerClass?: string
  pageSizeOptions?: number[]
  actionsVisibility?: 'desktop' | 'always'
  titleTag?: 'h1' | 'h2' | 'h3'
  loadingPreset?: LoadingPreset
  loadingRows?: number
  inset?: boolean
}>(), {
  maxWidthClass: 'max-w-4xl',
  headerClass: 'px-4 py-4 sm:py-5 sm:px-6 gap-3',
  pageSizeOptions: () => [15, 30, 50],
  actionsVisibility: 'desktop',
  titleTag: 'h3',
  loadingPreset: 'none',
  loadingRows: 5,
  inset: false,
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
const emptyTitleTag = computed<'h2' | 'h3' | 'h4'>(() => {
  if (props.titleTag === 'h1') return 'h2'
  if (props.titleTag === 'h2') return 'h3'
  return 'h4'
})

const getLoadingRowClass = (preset: LoadingPreset) => {
  switch (preset) {
    case 'compact-status-list':
      return 'px-3 py-2.5 sm:px-6 sm:py-4 flex justify-between items-center'
    case 'notification-list':
      return 'px-3 py-3 sm:px-6 sm:py-4 flex justify-between items-center'
    case 'message-list':
      return 'p-4 flex items-start'
    default:
      return 'px-4 py-4 sm:px-6 flex justify-between items-center'
  }
}
</script>

<template>
  <div
    :class="[
      maxWidthClass,
      inset
        ? 'mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8'
        : 'w-full',
    ]"
  >
    <BaseCard no-padding>
      <div
        :class="[
          headerClass,
          'flex flex-col sm:flex-row sm:justify-between sm:items-center border-b nv-border',
        ]"
      >
        <component :is="titleTag" class="text-lg leading-6 font-medium nv-title flex items-center">
          <component :is="icon" class="h-5 w-5 mr-2 nv-text-subtle flex-shrink-0" />
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

      <div v-if="slots.subheader" class="border-b nv-border">
        <div class="px-4 py-3 sm:px-6">
          <slot name="subheader" />
        </div>
      </div>

      <template v-if="loading && itemsCount === 0">
        <slot v-if="slots.loading" name="loading" />
        <div v-else-if="loadingPreset !== 'none'" class="divide-y divide-[var(--nv-border)]">
          <div
            v-for="i in loadingRows"
            :key="i"
            :class="getLoadingRowClass(loadingPreset)"
          >
            <template v-if="loadingPreset === 'post-list'">
              <div class="w-full">
                <BaseSkeleton width="70%" height="24px" className="mb-2" />
                <div class="flex gap-2">
                  <BaseSkeleton width="40px" height="16px" />
                  <BaseSkeleton width="60px" height="16px" />
                </div>
              </div>
            </template>

            <template v-else-if="loadingPreset === 'status-list'">
              <div class="flex flex-col flex-1">
                <BaseSkeleton width="60%" height="20px" className="mb-1" />
                <BaseSkeleton width="30%" height="14px" />
              </div>
              <BaseSkeleton width="60px" height="24px" rounded="rounded-full" />
            </template>

            <template v-else-if="loadingPreset === 'compact-status-list'">
              <div class="flex flex-col flex-1 min-w-0">
                <BaseSkeleton width="60%" height="14px" className="mb-1" />
                <BaseSkeleton width="40%" height="12px" />
              </div>
              <BaseSkeleton width="48px" height="20px" rounded="rounded-full" />
            </template>

            <template v-else-if="loadingPreset === 'notification-list'">
              <div class="flex flex-col flex-1 min-w-0">
                <BaseSkeleton width="80%" height="14px" className="mb-1" />
                <BaseSkeleton width="60%" height="12px" />
              </div>
              <BaseSkeleton width="48px" height="16px" rounded="rounded-full" />
            </template>

            <template v-else-if="loadingPreset === 'avatar-action-list'">
              <div class="flex items-center">
                <BaseSkeleton width="2.5rem" height="2.5rem" rounded="rounded-full" className="mr-4" />
                <div>
                  <BaseSkeleton width="100px" height="16px" className="mb-1" />
                  <BaseSkeleton width="150px" height="14px" />
                </div>
              </div>
              <BaseSkeleton width="80px" height="32px" />
            </template>

            <template v-else-if="loadingPreset === 'message-list'">
              <BaseSkeleton width="20px" height="20px" className="mr-4 mt-1" />
              <div class="flex-1">
                <div class="flex justify-between mb-1">
                  <BaseSkeleton width="100px" height="16px" />
                  <BaseSkeleton width="80px" height="12px" />
                </div>
                <BaseSkeleton width="80%" height="16px" />
              </div>
            </template>
          </div>
        </div>
      </template>
      <ErrorState v-else-if="error" :message="error" show-retry @retry="emit('retry')" />
      <EmptyState
        v-else-if="itemsCount === 0"
        :title-tag="emptyTitleTag"
        :title="emptyTitle"
        :description="emptyDescription"
        :icon="icon"
        :action-label="emptyActionLabel"
        :action-to="emptyActionTo"
      />
      <slot v-else />

      <div v-if="itemsCount > 0" class="nv-surface-muted px-4 py-4 sm:px-6 flex flex-col items-center">
        <slot name="footer-meta" />
        <Pagination :current-page="page" :total-pages="totalPages" @page-change="emit('page-change', $event)" />
      </div>
    </BaseCard>
  </div>
</template>
