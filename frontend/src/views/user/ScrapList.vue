<script setup lang="ts">
import { useUser } from '@/composables/useUser'
import PostList from '@/components/board/PostList.vue'
import PaginatedListCard from '@/components/common/ui/PaginatedListCard.vue'
import { Bookmark } from 'lucide-vue-next'
import { usePaginatedListState } from '@/composables/usePaginatedListState'
import { isInquiryPostItem, resolveBoardRoute, resolvePostDetailRoute } from '@/utils/postNavigation'
import { useI18n } from 'vue-i18n'
import type { PostSummary } from '@/types'

const { t } = useI18n()
const { useMyScraps } = useUser()
const {
  page,
  size,
  handlePageChange,
  handleSizeChange,
  items: scraps,
  totalPages,
  isLoading: loading,
  errorMessage,
  refetch,
} = usePaginatedListState<PostSummary>(useMyScraps, { initialSize: 15, t })
</script>

<template>
  <PaginatedListCard
    :title="$t('user.tabs.scraps')"
    :icon="Bookmark"
    :items-count="scraps.length"
    :loading="loading"
    :error="errorMessage || null"
    :empty-title="$t('user.scrapList.empty')"
    :page="page"
    :size="size"
    :total-pages="totalPages"
    max-width-class="max-w-7xl"
    loading-preset="post-list"
    @retry="refetch"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
  >
    <PostList
      :posts="scraps"
      :show-board-name="true"
      :hide-no-column="true"
      :show-notice-badge="false"
      :show-comment-count="false"
      :show-preview-indicator="false"
      :show-secret-indicator="false"
      :resolve-post-route="resolvePostDetailRoute"
      :resolve-board-route="resolveBoardRoute"
      :show-inquiry-status="isInquiryPostItem"
    />
  </PaginatedListCard>
</template>
