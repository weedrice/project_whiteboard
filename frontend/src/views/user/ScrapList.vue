<script setup lang="ts">
import { onMounted } from 'vue'
import { userApi } from '@/api/user'
import PostList from '@/components/board/PostList.vue'
import Pagination from '@/components/common/ui/Pagination.vue'
import PageSizeSelector from '@/components/common/widgets/PageSizeSelector.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { Bookmark } from 'lucide-vue-next'
import type { PostSummary } from '@/types'
import { usePagination } from '@/composables/usePagination'
import { isInquiryPostItem, resolvePostDetailRoute } from '@/utils/postNavigation'

const {
  items: scraps,
  loading,
  page,
  size,
  totalPages,
  error,
  fetch: fetchScraps,
  handlePageChange,
  handleSizeChange
} = usePagination<PostSummary>(async (params, { signal }) => {
  const { data } = await userApi.getMyScraps(params, { signal })
  return data
}, { page: 0, size: 15 })

onMounted(() => {
  fetchScraps()
})
</script>

<template>
  <div class="max-w-7xl mx-auto py-4 sm:py-6 md:py-8 px-4 sm:px-6 lg:px-8">
    <div class="bg-white dark:bg-gray-800 shadow overflow-hidden sm:rounded-lg transition-colors duration-200">
      <div
        class="px-4 py-4 sm:py-5 sm:px-6 flex flex-col sm:flex-row sm:justify-between sm:items-center gap-3 border-b border-gray-200 dark:border-gray-700">
        <h3 class="text-lg leading-6 font-medium text-gray-900 dark:text-white flex items-center">
          <Bookmark class="h-5 w-5 mr-2 text-gray-500 dark:text-gray-400 flex-shrink-0" />
          {{ $t('user.tabs.scraps') }}
        </h3>
        <div class="hidden sm:block">
          <PageSizeSelector v-model="size" @change="handleSizeChange" />
        </div>
      </div>
      <div v-if="loading && scraps.length === 0" class="divide-y divide-gray-200 dark:divide-gray-700">
        <div v-for="i in 5" :key="i" class="px-4 py-4 sm:px-6 flex justify-between items-center">
          <div class="w-full">
            <BaseSkeleton width="70%" height="24px" className="mb-2" />
            <div class="flex gap-2">
              <BaseSkeleton width="40px" height="16px" />
              <BaseSkeleton width="60px" height="16px" />
            </div>
          </div>
        </div>
      </div>
      <ErrorState v-else-if="error" :message="error" show-retry @retry="fetchScraps" />
      <EmptyState v-else-if="scraps.length === 0" :title="$t('user.scrapList.empty')" :icon="Bookmark" />
      <div v-else>
        <PostList
          :posts="scraps"
          :show-board-name="true"
          :hide-no-column="true"
          :show-notice-badge="false"
          :show-comment-count="false"
          :show-preview-indicator="false"
          :show-secret-indicator="false"
          :resolve-post-route="resolvePostDetailRoute"
          :show-inquiry-status="isInquiryPostItem"
        />
        <div class="bg-gray-50 dark:bg-gray-900/50 px-4 py-4 sm:px-6 flex justify-center">
          <Pagination :current-page="page" :total-pages="totalPages" @page-change="handlePageChange" />
        </div>
      </div>
    </div>
  </div>
</template>
