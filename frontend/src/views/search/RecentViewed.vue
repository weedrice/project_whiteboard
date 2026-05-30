<script setup lang="ts">
import { useUser } from '@/composables/useUser'
import { Clock } from 'lucide-vue-next'
import PostList from '@/components/board/PostList.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { usePageResponseState, usePaginatedQueryState } from '@/composables/usePaginatedQueryState'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'

const { useRecentlyViewedPosts } = useUser()
const { page, size, params, handlePageChange, handleSizeChange } = usePaginatedQueryState({ initialSize: 15 })
const { data: recentData, isLoading: loading, isError, error, refetch } = useRecentlyViewedPosts(params)
const { items: posts, totalPages } = usePageResponseState(recentData, page)
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.recent')"
    :icon="Clock"
    :items-count="posts.length"
    :loading="loading"
    :error="isError ? (error instanceof Error ? error.message : $t('common.messages.loadFailed')) : null"
    :empty-title="$t('user.recentViewed.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-7xl"
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
