<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'
import { FileText } from 'lucide-vue-next'
import type { PostSummary } from '@/types'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import EmptyState from '@/components/common/ui/EmptyState.vue'
import PostListDesktopTable from '@/components/board/PostListDesktopTable.vue'
import PostListDensityControl from '@/components/board/PostListDensityControl.vue'
import PostListMobileItem from '@/components/board/PostListMobileItem.vue'
import { usePostListDensity, type PostListDensity } from '@/components/board/postListDensity'
import {
  createPostListColumns,
  getPostListActiveSortDirection,
  getPostListActiveSortKey,
  getPostListAuthorName,
  getPostListInteractiveTag,
  getPostListNextSort,
  getPostListRowClass,
  getPostListTitleProps,
  getPostListTitleTag,
  getPostListVisibleAuthorName,
  hasPostListInteractiveAuthor,
  isPostListAgentAuthor,
  isPostListCurrentPost,
  POST_LIST_MAX_AUTHOR_NAME_LENGTH,
  resolvePostListBoardRoute,
  resolvePostListPostRoute,
  shouldInterceptPostListInquiry,
  shouldShowPostListInquiryStatus,
  type PostPredicate,
  type ResolveBoardRoute,
  type ResolvePostRoute,
} from '@/components/board/postListModel'

const props = withDefaults(defineProps<{
  posts: PostSummary[]
  loading?: boolean
  boardUrl?: string
  currentSort?: string
  currentPostId?: string
  linkQuery?: LocationQueryRaw
  resolvePostRoute?: ResolvePostRoute
  resolveBoardRoute?: ResolveBoardRoute
  shouldInterceptPost?: PostPredicate
  showInquiryStatus?: PostPredicate
  showBoardName?: boolean
  hideNoColumn?: boolean
  interceptInquiry?: boolean
  showNoticeBadge?: boolean
  showCommentCount?: boolean
  showPreviewIndicator?: boolean
  showSecretIndicator?: boolean
  canWrite?: boolean
  emptyTitle?: string
  emptyDescription?: string
  emptyActionLabel?: string
  emptyActionTo?: RouteLocationRaw
  enableInfiniteLoad?: boolean
  hasMorePosts?: boolean
  isLoadingMore?: boolean
  density?: PostListDensity
  showDensityControl?: boolean
}>(), {
  loading: false,
  currentSort: 'createdAt,desc',
  showBoardName: false,
  hideNoColumn: false,
  interceptInquiry: false,
  showNoticeBadge: true,
  showCommentCount: true,
  showPreviewIndicator: true,
  showSecretIndicator: true,
  canWrite: false,
  emptyTitle: undefined,
  emptyDescription: undefined,
  emptyActionLabel: undefined,
  emptyActionTo: undefined,
  enableInfiniteLoad: false,
  hasMorePosts: false,
  isLoadingMore: false,
  density: undefined,
  showDensityControl: true,
})

const emit = defineEmits<{
  (e: 'update:sort', sort: string): void
  (e: 'inquiry-click', post: PostSummary): void
  (e: 'load-more'): void
}>()

const { t } = useI18n()
const listDensity = usePostListDensity()
const effectiveDensity = computed(() => props.density ?? listDensity.value)
const effectiveEmptyTitle = computed(() => props.emptyTitle ?? t('board.list.noPosts'))
const effectiveEmptyDescription = computed(() => props.emptyDescription)
const effectiveEmptyActionLabel = computed(() => (
  props.canWrite ? (props.emptyActionLabel ?? t('common.write')) : undefined
))

const getRowClass = (item: PostSummary) => (
  getPostListRowClass(item, props.currentPostId)
)

function isCurrentPost(item: PostSummary): boolean {
  return isPostListCurrentPost(item, props.currentPostId)
}

function getBoardLink(item: PostSummary): RouteLocationRaw | null {
  return resolvePostListBoardRoute(item, props.boardUrl, props.resolveBoardRoute)
}

function hasBoardRouteTarget(item: PostSummary): boolean {
  return getBoardLink(item) !== null
}

function getBoardLinkTarget(item: PostSummary): RouteLocationRaw {
  return getBoardLink(item) ?? '/'
}

function resolvePostRouteTarget(item: PostSummary): RouteLocationRaw | null {
  return resolvePostListPostRoute(item, props.boardUrl, props.linkQuery, props.resolvePostRoute)
}

const postRouteTargets = computed(() => {
  const routes = new Map<PostSummary, RouteLocationRaw | null>()
  props.posts.forEach((item) => {
    routes.set(item, resolvePostRouteTarget(item))
  })
  return routes
})

function getPostLink(item: PostSummary): RouteLocationRaw | null {
  return postRouteTargets.value.has(item)
    ? postRouteTargets.value.get(item) ?? null
    : resolvePostRouteTarget(item)
}

function hasPostRouteTarget(item: PostSummary): boolean {
  return getPostLink(item) !== null
}

function shouldInterceptInquiry(item: PostSummary): boolean {
  return shouldInterceptPostListInquiry(item, props.boardUrl, props.interceptInquiry, props.shouldInterceptPost)
}

function shouldShowInquiryStatus(item: PostSummary): boolean {
  return shouldShowPostListInquiryStatus(item, props.boardUrl, props.showInquiryStatus)
}

function isAgentAuthor(item: PostSummary): boolean {
  return isPostListAgentAuthor(item)
}

function getAuthorName(item: PostSummary): string {
  return getPostListAuthorName(item, t('user.deletedUser'))
}

function hasInteractiveAuthor(item: PostSummary): boolean {
  return hasPostListInteractiveAuthor(item)
}

function getVisibleAuthorName(item: PostSummary): string {
  return getPostListVisibleAuthorName(item, t('user.deletedUser'))
}

function handleSort(field: string) {
  emit('update:sort', getPostListNextSort(props.currentSort, field))
}

const activeSortKey = computed(() => getPostListActiveSortKey(props.currentSort))

const activeSortDirection = computed<'asc' | 'desc' | null>(() => getPostListActiveSortDirection(props.currentSort))
const loadMoreTrigger = ref<HTMLElement | null>(null)
let loadMoreObserver: IntersectionObserver | null = null

function disconnectLoadMoreObserver() {
  loadMoreObserver?.disconnect()
  loadMoreObserver = null
}

function requestLoadMore() {
  if (!props.enableInfiniteLoad || !props.hasMorePosts || props.isLoadingMore) {
    return
  }
  emit('load-more')
}

function attachLoadMoreObserver() {
  disconnectLoadMoreObserver()
  if (
    !props.enableInfiniteLoad
    || !props.hasMorePosts
    || typeof window === 'undefined'
    || typeof window.IntersectionObserver !== 'function'
    || !loadMoreTrigger.value
  ) {
    return
  }

  loadMoreObserver = new window.IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      requestLoadMore()
    }
  }, {
    rootMargin: '240px 0px',
  })
  loadMoreObserver.observe(loadMoreTrigger.value)
}

watch(
  () => [props.enableInfiniteLoad, props.hasMorePosts, props.isLoadingMore, props.posts.length] as const,
  attachLoadMoreObserver,
  { flush: 'post', immediate: true }
)

onBeforeUnmount(disconnectLoadMoreObserver)

function onNavigationClick(event: Event, item: PostSummary) {
  if (shouldInterceptInquiry(item)) {
    event.preventDefault()
    emit('inquiry-click', item)
  }
}

function getInteractiveTag(item: PostSummary): 'button' | 'router-link' | 'div' {
  return getPostListInteractiveTag(shouldInterceptInquiry(item), hasPostRouteTarget(item))
}

function getTitleTag(item: PostSummary): 'button' | 'router-link' | 'span' {
  return getPostListTitleTag(shouldInterceptInquiry(item), hasPostRouteTarget(item))
}

function getTitleProps(item: PostSummary) {
  return getPostListTitleProps(getTitleTag(item), getPostLink(item), t('board.invalidUrl'))
}

const columns = computed(() => createPostListColumns({
  no: t('common.no'),
  board: t('common.board'),
  title: t('common.title'),
  author: t('common.author'),
  likes: t('common.likes'),
  views: t('common.views'),
  date: t('common.date'),
}, {
  showBoardName: props.showBoardName,
  hideNoColumn: props.hideNoColumn,
}))
</script>

<template>
  <div class="card border-0 bg-transparent shadow-none" :aria-busy="loading ? 'true' : 'false'">
    <div v-if="showDensityControl" class="flex items-center justify-end px-3 py-2 sm:px-4">
      <PostListDensityControl
        v-model="listDensity"
      />
    </div>

    <div class="sm:hidden divide-y divide-[var(--nv-line-soft)]">
      <template v-if="loading">
        <div class="space-y-3 px-4 py-4">
          <div v-for="index in 4" :key="index" class="space-y-3 rounded-[24px] border border-[var(--nv-line)] px-4 py-4">
            <BaseSkeleton width="120px" height="20px" rounded="rounded-full" />
            <BaseSkeleton width="78%" height="18px" />
            <BaseSkeleton width="56%" height="14px" />
          </div>
        </div>
      </template>

      <template v-else-if="posts.length === 0">
        <EmptyState
          title-tag="h2"
          :title="effectiveEmptyTitle"
          :description="effectiveEmptyDescription"
          :icon="FileText"
          :action-label="effectiveEmptyActionLabel"
          :action-to="props.emptyActionTo"
          container-class="px-4"
        />
      </template>

      <PostListMobileItem
        v-else
        v-for="item in posts"
        :key="item.postId"
        :post="item"
        :interactive-tag="getInteractiveTag(item)"
        :post-link="getPostLink(item)"
        :is-current="isCurrentPost(item)"
        :show-inquiry-status="shouldShowInquiryStatus(item)"
        :show-notice-badge="false"
        :show-comment-count="props.showCommentCount"
        :show-preview-indicator="props.showPreviewIndicator"
        :show-secret-indicator="props.showSecretIndicator"
        :deleted-user-label="t('user.deletedUser')"
        :density="effectiveDensity"
        @navigate="onNavigationClick"
      />

      <div
        v-if="enableInfiniteLoad && posts.length > 0"
        class="px-4 py-4 text-center"
      >
        <button
          v-if="hasMorePosts"
          ref="loadMoreTrigger"
          type="button"
          class="nv-mobile-load-more"
          :disabled="isLoadingMore"
          @click="requestLoadMore"
        >
          {{ isLoadingMore ? t('common.loading') : t('common.loadMore') }}
        </button>
      </div>
    </div>

    <div v-if="!loading && posts.length === 0" class="hidden sm:block">
      <EmptyState
        title-tag="h2"
        :title="effectiveEmptyTitle"
        :description="effectiveEmptyDescription"
        :icon="FileText"
        :action-label="effectiveEmptyActionLabel"
        :action-to="props.emptyActionTo"
      />
    </div>

    <PostListDesktopTable
      v-else
      :posts="posts"
      :loading="loading"
      :caption="t('common.post')"
      :columns="columns"
      :active-sort-key="activeSortKey"
      :active-sort-direction="activeSortDirection"
      :show-notice-badge="false"
      :show-comment-count="props.showCommentCount"
      :show-preview-indicator="props.showPreviewIndicator"
      :show-secret-indicator="props.showSecretIndicator"
      :density="effectiveDensity"
      :max-author-name-length="POST_LIST_MAX_AUTHOR_NAME_LENGTH"
      :get-row-class="getRowClass"
      :should-intercept-inquiry="shouldInterceptInquiry"
      :has-board-route-target="hasBoardRouteTarget"
      :get-board-link-target="getBoardLinkTarget"
      :get-title-tag="getTitleTag"
      :get-title-props="getTitleProps"
      :should-show-inquiry-status="shouldShowInquiryStatus"
      :has-interactive-author="hasInteractiveAuthor"
      :get-author-name="getAuthorName"
      :get-visible-author-name="getVisibleAuthorName"
      :is-agent-author="isAgentAuthor"
      :on-navigation-click="onNavigationClick"
      @sort="handleSort"
    />
  </div>
</template>

<style scoped>
.nv-mobile-load-more {
  min-height: 44px;
  border: 1px solid var(--nv-line);
  border-radius: var(--nv-radius-pill);
  background: var(--nv-surface);
  color: var(--nv-ink);
  font-size: 0.875rem;
  font-weight: 700;
  padding: 0.625rem 1.25rem;
}

.nv-mobile-load-more:disabled {
  color: var(--nv-muted);
}
</style>
