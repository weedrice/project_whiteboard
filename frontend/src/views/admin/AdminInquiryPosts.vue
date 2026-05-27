<script setup lang="ts">
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import AdminPaginationFooter from '@/components/admin/AdminPaginationFooter.vue'
import AdminPanel from '@/components/admin/AdminPanel.vue'
import AdminInquiryDetailModal from '@/components/admin/AdminInquiryDetailModal.vue'
import { useI18n } from 'vue-i18n'
import { useAdminInquiryPosts } from '@/composables/useAdminInquiryPosts'

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
</script>

<template>
  <div>
    <AdminPageHeader :title="t('admin.inquiries.title')" :description="t('admin.inquiries.description')">
      <template #actions>
        <div class="mt-3 flex items-center gap-2 sm:mt-0">
          <label for="inquiry-sort" class="text-sm text-gray-600 dark:text-gray-300">{{ t('admin.inquiries.sort.label') }}</label>
          <select
            id="inquiry-sort"
            v-model="sort"
            class="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-100"
          >
            <option value="createdAt,desc">{{ t('admin.inquiries.sort.latest') }}</option>
            <option value="createdAt,asc">{{ t('admin.inquiries.sort.oldest') }}</option>
          </select>
        </div>
      </template>
    </AdminPageHeader>

    <AdminPanel class="mt-4 shadow-sm" padding="none" :shadow="false">
      <div v-if="isLoading" class="flex items-center justify-center py-10">
        <BaseSpinner size="lg" />
      </div>

      <div v-else-if="error" class="px-4 py-6 text-sm text-red-600 dark:text-red-400">
        {{ t('common.messages.loadFailed') }}
      </div>

      <div v-else class="overflow-x-auto">
        <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead class="bg-gray-50 dark:bg-gray-900/30">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">{{ t('common.title') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">{{ t('admin.inquiries.table.summary') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">{{ t('common.author') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">{{ t('common.createdAt') }}</th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-300">{{ t('common.status') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
            <tr v-if="posts.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-sm text-gray-500 dark:text-gray-400">
                {{ t('admin.inquiries.empty') }}
              </td>
            </tr>
            <tr
              v-for="post in posts"
              :key="post.id"
              class="cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/40"
              @click="openDetail(post.id)"
            >
              <td class="px-4 py-3 text-sm text-gray-900 dark:text-gray-100">
                <button type="button" class="max-w-[280px] truncate text-left hover:underline" @click.stop="openDetail(post.id)">
                  {{ post.title }}
                </button>
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                {{ post.summaryText }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                {{ post.authorName }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                {{ post.createdAtText }}
              </td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">
                <span
                  class="inline-flex rounded-full px-2 py-1 text-xs font-medium"
                  :class="post.statusClass"
                >
                  {{ t(post.statusLabelKey) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
  </div>
</template>
