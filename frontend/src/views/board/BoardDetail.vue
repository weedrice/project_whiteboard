<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@unhead/vue'
import { useI18n } from 'vue-i18n'
import BoardDetailHeader from '@/components/board/BoardDetailHeader.vue'
import BoardDetailLoadingState from '@/components/board/BoardDetailLoadingState.vue'
import BoardNoticeList from '@/components/board/BoardNoticeList.vue'
import BoardPostFilters from '@/components/board/BoardPostFilters.vue'
import BoardPostSearch from '@/components/board/BoardPostSearch.vue'
import PostList from '@/components/board/PostList.vue'
import { usePostListDensity } from '@/components/board/postListDensity'
import Pagination from '@/components/common/ui/Pagination.vue'
import PullToRefresh from '@/components/common/ui/PullToRefresh.vue'
import { useBoardDetailNavigation } from '@/features/board/detail/useBoardDetailNavigation'
import { useBoardDetailResource } from '@/features/board/detail/useBoardDetailResource'
import { useBoardListState } from '@/features/board/detail/useBoardListState'
import { useBoardRecentVisit } from '@/features/board/detail/useBoardRecentVisit'
import { useBoardSubscriptionAction } from '@/features/board/detail/useBoardSubscriptionAction'
import { useAuthStore } from '@/stores/auth'
import { canWriteBoardPost } from '@/utils/board'
import { isInquiryPostItem, resolvePostDetailRoute } from '@/utils/postNavigation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const listDensity = usePostListDensity()

const {
  page,
  searchQuery,
  searchType,
  isSearching,
  conceptOnly,
  selectedCategoryId,
  sort,
  queryParams,
  buildPaginationRoute,
  handleSearch,
  clearSearch,
  activateAllPostsFilter,
  toggleConceptPosts,
  toggleCategory,
  handleSortChange,
  handlePageChange,
  resetListState
} = useBoardListState(route, router)

const boardUrl = computed(() => route.params.boardUrl as string)
const currentPostId = computed(() => route.params.postId as string | undefined)

const {
  board,
  boardTitle,
  blockingError,
  categories,
  hasNoticeOverflow,
  isInitialLoading,
  isFetchingNextPostPage,
  isMobilePostList,
  isPostPageResolved,
  isNoticesExpanded,
  isSubscribePending,
  hasMorePosts,
  loadMorePosts,
  posts,
  resetNoticeState,
  showPostListLoading,
  subscribeMutate,
  totalPages,
  transientListError,
  visibleNotices,
  notices,
  refresh,
} = useBoardDetailResource({
  boardUrl,
  queryParams,
  isSearching,
  t
})

watch([page, totalPages, isPostPageResolved], ([currentPage, resolvedTotalPages, isResolved]) => {
  if (!isResolved) return
  handlePageChange(currentPage, resolvedTotalPages)
}, { immediate: true })

useBoardRecentVisit(board)

const canWrite = computed(() => (
  canWriteBoardPost(board.value, authStore.isAuthenticated, authStore.user?.role)
))
const mobilePostListEnabled = computed(() => isMobilePostList.value)
const isAllPostsActive = computed(() => (
  !conceptOnly.value
  && selectedCategoryId.value === null
))

const { handleSubscribe } = useBoardSubscriptionAction({
  board,
  sessionGeneration: computed(() => authStore.sessionGeneration),
  isSubscribePending,
  subscribeMutate,
})

const {
  buildBoardListRoute,
  getNoticeRoute,
  highlightedPostId,
  listQuery,
  onPageChange,
  searchInputElementId
} = useBoardDetailNavigation({
  route,
  router,
  boardUrl,
  currentPostId,
  page,
  totalPages,
  board,
  canWrite,
  isSubscribePending,
  handleSubscribe,
  handlePageChange,
  resetListState,
  resetNoticeState
})

useHead({
  title: computed(() => (currentPostId.value ? undefined : boardTitle.value)),
  meta: [
    { name: 'description', content: computed(() => board.value?.description || t('board.seo.spaceDescriptionFallback')) },
    { property: 'og:title', content: computed(() => `${board.value?.boardName || t('board.seo.spaceTitleFallback')} - ${t('common.appName')}`) },
    { property: 'og:description', content: computed(() => board.value?.description || t('board.seo.spaceDescriptionFallback')) }
  ]
})

</script>

<template>
  <PullToRefresh :enabled="!currentPostId" :refresh="refresh">
  <div class="nv-board-shell">
    <BoardDetailLoadingState v-if="isInitialLoading" />

    <section v-else-if="blockingError" class="nv-board-panel nv-elevated-surface nv-board-state-panel px-4 py-12 text-center sm:px-6"
      role="alert" aria-live="assertive">
      <p class="nv-kicker">{{ t('common.board') }}</p>
      <p class="mt-3 text-sm nv-form-error">{{ blockingError }}</p>
    </section>

    <template v-else-if="board">
      <BoardDetailHeader
        :board="board"
        :heading-tag="currentPostId ? 'h2' : 'h1'"
        :can-write="canWrite"
        :is-authenticated="authStore.isAuthenticated"
        :is-subscribe-pending="isSubscribePending"
        :build-board-list-route="buildBoardListRoute"
        @subscribe="handleSubscribe"
      />

      <div v-if="currentPostId" class="mb-3 sm:mb-6">
        <router-view />
      </div>

      <section id="board-post-list" class="nv-board-panel nv-elevated-surface nv-board-list-panel overflow-hidden">
        <BoardNoticeList
          :notices="notices"
          :visible-notices="visibleNotices"
          :has-notice-overflow="hasNoticeOverflow"
          :is-expanded="isNoticesExpanded"
          :highlighted-post-id="highlightedPostId"
          :get-notice-route="getNoticeRoute"
          @update:is-expanded="isNoticesExpanded = $event"
        />

        <BoardPostFilters
          v-model:density="listDensity"
          :categories="categories"
          :is-all-posts-active="isAllPostsActive"
          :concept-only="conceptOnly"
          :selected-category-id="selectedCategoryId"
          @activate-all="activateAllPostsFilter"
          @toggle-concept="toggleConceptPosts"
          @toggle-category="toggleCategory"
        />

        <PostList
          :posts="posts"
          :loading="showPostListLoading"
          :boardUrl="board.boardUrl"
          :current-sort="sort"
          :currentPostId="highlightedPostId"
          :linkQuery="listQuery"
          :resolve-post-route="resolvePostDetailRoute"
          :show-inquiry-status="isInquiryPostItem"
          :can-write="canWrite && !isSearching"
          :empty-description="isSearching ? t('board.detail.emptySearchDescription') : t('board.detail.emptyDescription')"
          :empty-action-to="`/board/${board.boardUrl}/write`"
          :enable-infinite-load="mobilePostListEnabled"
          :has-more-posts="hasMorePosts"
          :is-loading-more="isFetchingNextPostPage"
          :density="listDensity"
          :show-density-control="false"
          @update:sort="handleSortChange"
          @load-more="loadMorePosts"
        />

        <BoardPostSearch
          v-model:search-query="searchQuery"
          v-model:search-type="searchType"
          :search-input-element-id="searchInputElementId"
          :is-searching="isSearching"
          :can-write="canWrite"
          :board-url="board.boardUrl"
          :transient-list-error="transientListError"
          @search="handleSearch"
          @clear="clearSearch"
        />

        <div
          v-if="!mobilePostListEnabled && totalPages > 1"
          class="flex justify-center px-3 py-3 sm:px-4"
        >
          <Pagination
            :currentPage="page"
            :totalPages="totalPages"
            :linkBuilder="buildPaginationRoute"
            @page-change="onPageChange"
          />
        </div>
      </section>
    </template>
  </div>
  </PullToRefresh>
</template>

<style scoped>
.nv-board-shell {
  color: var(--nv-ink);
}

.nv-board-stack {
  display: grid;
  gap: 0;
}

.nv-board-panel {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem;
  box-shadow: var(--nv-shadow-card);
}

.nv-board-header-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-surface-2) 45%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border-bottom: 0;
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.nv-board-list-panel {
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

.nv-board-search-btn:not(:disabled) {
  cursor: pointer;
}

.nv-board-search-btn:not(:disabled):active {
  filter: brightness(0.9);
}

.nv-board-search-btn:focus-visible {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--nv-accent) 18%, transparent);
  outline: none;
}

.nv-board-search-btn:disabled {
  cursor: not-allowed;
}

.nv-board-state-panel {
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--nv-danger) 8%, transparent), transparent),
    color-mix(in srgb, var(--nv-surface) 94%, transparent);
}

@media (max-width: 639px) {
  .nv-board-panel {
    border-radius: 0.85rem;
  }

}
</style>
