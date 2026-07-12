<script setup lang="ts">
import { computed } from 'vue'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import AdminPaginatedTable from '@/components/admin/AdminPaginatedTable.vue'
import AdminStatusBadge from '@/components/admin/AdminStatusBadge.vue'
import AdminInquiryDetailModal from '@/components/admin/AdminInquiryDetailModal.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import { useI18n } from 'vue-i18n'
import { useAdminInquiryPosts, type AdminInquiryListItem } from '@/features/admin/inquiries/useAdminInquiryPosts'

const { t } = useI18n()
const {
  closeDetail,
  detailError,
  error,
  handlePageChange,
  isDetailFetching,
  isDetailLoading,
  isFetching,
  isLoading,
  openDetail,
  page,
  posts,
  selectedInquiry,
  selectedPostId,
  sort,
  totalElements,
  totalPages,
} = useAdminInquiryPosts()

const columns = computed<TableColumn[]>(() => [
  { key: 'title', label: t('common.title'), width: '28%' },
  { key: 'summaryText', label: t('admin.inquiries.table.summary'), width: '30%', hideBelow: 'sm' },
  { key: 'authorName', label: t('common.author'), width: '14%', hideBelow: 'lg' },
  { key: 'createdAtText', label: t('common.createdAt'), width: '16%', hideBelow: 'sm' },
  { key: 'status', label: t('common.status'), width: '12%' },
])
const filterChips = computed(() => sort.value === 'createdAt,desc' ? [] : [{ key: 'sort', label: t('admin.inquiries.sort.oldest') }])
const sortOptions = computed(() => [
  { value: 'createdAt,desc', label: t('admin.inquiries.sort.latest') },
  { value: 'createdAt,asc', label: t('admin.inquiries.sort.oldest') },
])

function getRowClass() {
  return 'cursor-pointer'
}

function handleRowClick(post: AdminInquiryListItem) {
  openDetail(post.id)
}
</script>

<template>
  <AdminDataPage :title="t('admin.inquiries.title')" :description="t('admin.inquiries.description')">
    <template #filters>
      <AdminFilterPanel class-name="mt-4" collapsible :title="t('admin.inquiries.sort.label')" :chips="filterChips" @remove-chip="sort = 'createdAt,desc'">
        <div class="flex flex-wrap items-end gap-3">
          <AdminFilterField :label="t('admin.inquiries.sort.label')" for-id="inquiry-sort" width="select">
          <BaseSelect
            id="inquiry-sort"
            v-model="sort"
            :options="sortOptions"
            input-class="rounded-md px-3 py-2 text-sm"
          />
          </AdminFilterField>
        </div>
      </AdminFilterPanel>
    </template>

    <div v-if="error && !isLoading" class="mt-4 rounded-lg border nv-border px-4 py-6 text-sm nv-form-error">
      {{ t('common.messages.loadFailed') }}
    </div>

    <AdminPaginatedTable
      v-else
      table-class="mt-4"
      :columns="columns"
      :caption="t('admin.inquiries.title')"
      :items="posts"
      row-key="id"
      :loading="isLoading"
      :empty-text="t('admin.inquiries.empty')"
      :row-class="getRowClass"
      interactive-rows
      :row-action-label="(item) => t('admin.common.rowDetailAria', { name: item.title })"
      :page="page"
      :total-pages="totalPages"
      :summary="t('admin.inquiries.total', { count: totalElements })"
      :footer-loading="isFetching"
      :loading-text="t('admin.inquiries.refreshing')"
      @row-click="handleRowClick"
      @page-change="handlePageChange"
    >
      <template #cell-title="{ item }">
        <span class="block max-w-[280px] truncate text-left">
          {{ item.title }}
        </span>
      </template>

      <template #cell-status="{ item }">
        <AdminStatusBadge :label="t(item.statusLabelKey)" :variant="item.statusVariant" />
      </template>
    </AdminPaginatedTable>

    <AdminInquiryDetailModal
      :is-open="selectedPostId !== null"
      :inquiry="selectedInquiry"
      :loading="isDetailLoading"
      :fetching="isDetailFetching"
      :error="detailError"
      @close="closeDetail"
    />
  </AdminDataPage>
</template>
