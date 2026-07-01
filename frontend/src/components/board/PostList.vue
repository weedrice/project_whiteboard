<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'
import type { PostSummary } from '@/types'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import PostListDesktopTable from '@/components/board/PostListDesktopTable.vue'
import PostListMobileItem from '@/components/board/PostListMobileItem.vue'
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
}>(), {
  loading: false,
  currentSort: 'createdAt,desc',
  showBoardName: false,
  hideNoColumn: false,
  interceptInquiry: false,
  showNoticeBadge: true,
  showCommentCount: true,
  showPreviewIndicator: true,
  showSecretIndicator: true
})

const emit = defineEmits<{
  (e: 'update:sort', sort: string): void
  (e: 'inquiry-click', post: PostSummary): void
}>()

const { t } = useI18n()

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
        <div class="px-4 py-10 text-center text-xs text-[var(--nv-muted)]">
          {{ $t('board.list.noPosts') }}
        </div>
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
        :show-notice-badge="props.showNoticeBadge"
        :show-comment-count="props.showCommentCount"
        :show-preview-indicator="props.showPreviewIndicator"
        :show-secret-indicator="props.showSecretIndicator"
        :deleted-user-label="t('user.deletedUser')"
        @navigate="onNavigationClick"
      />
    </div>

    <PostListDesktopTable
      :posts="posts"
      :loading="loading"
      :columns="columns"
      :active-sort-key="activeSortKey"
      :active-sort-direction="activeSortDirection"
      :show-notice-badge="props.showNoticeBadge"
      :show-comment-count="props.showCommentCount"
      :show-preview-indicator="props.showPreviewIndicator"
      :show-secret-indicator="props.showSecretIndicator"
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
