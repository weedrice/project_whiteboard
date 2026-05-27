<script setup lang="ts">
import { onMounted } from 'vue'
import { userApi } from '@/api/user'
import PostList from '@/components/board/PostList.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
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
  <PaginatedListCard
    :title="$t('user.tabs.scraps')"
    :icon="Bookmark"
    :items-count="scraps.length"
    :loading="loading"
    :error="error"
    :empty-title="$t('user.scrapList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-7xl"
    @retry="fetchScraps"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #loading>
      <div class="divide-y divide-gray-200 dark:divide-gray-700">
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
    </template>

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
  </PaginatedListCard>
</template>
