<script setup lang="ts">
import { computed } from 'vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseTable, { type TableColumn } from '@/components/common/ui/BaseTable.vue'
import AdminDataPage from '@/components/admin/AdminDataPage.vue'
import AdminFilterField from '@/components/admin/AdminFilterField.vue'
import AdminFilterPanel from '@/components/admin/AdminFilterPanel.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import AdminInquiryDetailModal from '@/components/admin/AdminInquiryDetailModal.vue'
import { useI18n } from 'vue-i18n'
import { useAdminInquiryPosts, type AdminInquiryListItem } from '@/composables/useAdminInquiryPosts'

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
  { key: 'summaryText', label: t('admin.inquiries.table.summary'), width: '30%' },
  { key: 'authorName', label: t('common.author'), width: '14%' },
  { key: 'createdAtText', label: t('common.createdAt'), width: '16%' },
  { key: 'status', label: t('common.status'), width: '12%' },
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
      <AdminFilterPanel class-name="mt-4">
        <div class="flex flex-wrap items-end gap-3">
          <AdminFilterField :label="t('admin.inquiries.sort.label')" for-id="inquiry-sort" width-class="w-44">
          <select
            id="inquiry-sort"
            v-model="sort"
            class="input-base rounded-md px-3 py-2 text-sm"
          >
            <option value="createdAt,desc">{{ t('admin.inquiries.sort.latest') }}</option>
            <option value="createdAt,asc">{{ t('admin.inquiries.sort.oldest') }}</option>
          </select>
          </AdminFilterField>
        </div>
      </AdminFilterPanel>
    </template>

    <AdminPanel class="mt-4 shadow-sm" padding="none" :shadow="false">
      <div v-if="isLoading" class="flex items-center justify-center py-10">
        <BaseSpinner size="lg" />
      </div>

      <div v-else-if="error" class="px-4 py-6 text-sm nv-form-error">
        {{ t('common.messages.loadFailed') }}
      </div>

      <BaseTable
        v-else
        :columns="columns"
        :items="posts"
        row-key="id"
        :empty-text="t('admin.inquiries.empty')"
        :row-class="getRowClass"
        @row-click="handleRowClick"
      >
        <template #cell-title="{ item }">
          <button type="button" class="max-w-[280px] truncate text-left hover:underline" @click.stop="openDetail(item.id)">
            {{ item.title }}
          </button>
        </template>

        <template #cell-status="{ item }">
          <span
            class="inline-flex rounded-full px-2 py-1 text-xs font-medium"
            :class="item.statusClass"
          >
            {{ t(item.statusLabelKey) }}
          </span>
        </template>
      </BaseTable>
    </AdminPanel>

    <AdminPaginationFooter
      :page="page"
      :total-pages="totalPages"
      :summary="t('admin.inquiries.total', { count: totalElements })"
      :loading="isFetching"
      :loading-text="t('admin.inquiries.refreshing')"
      @page-change="handlePageChange"
    />

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
