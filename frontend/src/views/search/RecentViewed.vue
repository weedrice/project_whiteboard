<script setup lang="ts">
import { useUser } from '@/composables/useUser'
import { Clock } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import { useI18n } from 'vue-i18n'
import type { PostSummary } from '@/types'

const { t } = useI18n()
const { useRecentlyViewedPosts } = useUser()
const {
  page,
  size,
  handlePageChange,
  handleSizeChange,
  items: posts,
  totalPages,
  isLoading: loading,
  errorMessage,
  refetch,
} = usePaginatedListState<PostSummary>(useRecentlyViewedPosts, {
  initialSize: 15,
  t,
  getErrorMessage: (error, t) => error instanceof Error ? error.message : t('common.messages.loadFailed'),
})
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.recent')"
    :icon="Clock"
    :items-count="posts.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.recentViewed.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-none"
    :inset="false"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <template #loading>
      <div class="text-center py-10">
        <BaseSpinner size="lg" />
      </div>
    </template>

    <PostList
      :posts="posts"
      :show-board-name="true"
      :hide-no-column="true"
      :resolve-post-route="resolvePostDetailRoute"
      :resolve-board-route="resolveBoardRoute"
      :show-inquiry-status="isInquiryPostItem"
    />
  </PaginatedListCard>
</template>
