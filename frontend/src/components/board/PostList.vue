<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'
import type { PostSummary } from '@/types'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import PostListDesktopTable from '@/components/board/PostListDesktopTable.vue'
import PostListMobileItem from '@/components/board/PostListMobileItem.vue'
import { formatUserDisplayName } from '@/utils/userDisplay'

type ResolvePostRoute = (
  post: PostSummary,
  boardUrl: string,
  linkQuery: LocationQueryRaw | undefined
) => RouteLocationRaw | null | undefined

type ResolveBoardRoute = (
  post: PostSummary,
  boardUrl: string
) => RouteLocationRaw | null | undefined

type PostPredicate = (
  post: PostSummary,
  boardUrl: string
) => boolean

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
const MAX_AUTHOR_NAME_LENGTH = 10
const SORT_FIELD_MAP: Record<string, string> = {
  postId: 'createdAt',
  likeCount: 'likeCount',
  viewCount: 'viewCount'
}

const getRowClass = (item: PostSummary) => (
  isCurrentPost(item) ? 'post-list-row post-list-row-current' : 'post-list-row'
)

function isCurrentPost(item: PostSummary): boolean {
  return String(item.postId) === String(props.currentPostId ?? '')
}

function getResolvedBoardUrl(item: PostSummary): string {
  const raw = String(props.boardUrl || item.boardUrl || '').trim().toLowerCase()
  return raw.replace(/^\/+|\/+$/g, '')
}

function getBoardLink(item: PostSummary): RouteLocationRaw | null {
  const boardUrl = getResolvedBoardUrl(item)
  if (!boardUrl) return null
  if (props.resolveBoardRoute) {
    return props.resolveBoardRoute(item, boardUrl) ?? null
  }
  return `/board/${boardUrl}`
}

function hasBoardRouteTarget(item: PostSummary): boolean {
  return getBoardLink(item) !== null
}

function getBoardLinkTarget(item: PostSummary): RouteLocationRaw {
  return getBoardLink(item) ?? '/'
}

function resolvePostRouteTarget(item: PostSummary): RouteLocationRaw | null {
  const boardUrl = getResolvedBoardUrl(item)
  if (!boardUrl) return null
  if (props.resolvePostRoute) {
    return props.resolvePostRoute(item, boardUrl, props.linkQuery) ?? null
  }
  return null
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
  const boardUrl = getResolvedBoardUrl(item)
  return props.interceptInquiry && !!props.shouldInterceptPost?.(item, boardUrl)
}

function shouldShowInquiryStatus(item: PostSummary): boolean {
  const boardUrl = getResolvedBoardUrl(item)
  return !!props.showInquiryStatus?.(item, boardUrl)
}

function isAgentAuthor(item: PostSummary): boolean {
  return item.author?.authorType === 'AGENT'
}

function getAuthorName(item: PostSummary): string {
  return formatUserDisplayName(item.author?.displayName, undefined, t('user.deletedUser'))
}

function hasInteractiveAuthor(item: PostSummary): boolean {
  return !!item.author?.displayName?.trim()
}

function getVisibleAuthorName(item: PostSummary): string {
  return formatUserDisplayName(item.author?.displayName, MAX_AUTHOR_NAME_LENGTH, t('user.deletedUser'))
}

function handleSort(field: string) {
  const normalizedField = SORT_FIELD_MAP[field] ?? field
  const [currentField, currentDirection] = props.currentSort.split(',')
  let nextDirection = 'desc'

  if (normalizedField === currentField) {
    nextDirection = currentDirection === 'desc' ? 'asc' : 'desc'
  }

  emit('update:sort', `${normalizedField},${nextDirection}`)
}

const activeSortKey = computed(() => {
  const [currentField] = props.currentSort.split(',')
  const matchedEntry = Object.entries(SORT_FIELD_MAP).find(([, apiField]) => apiField === currentField)
  return matchedEntry?.[0] ?? null
})

const activeSortDirection = computed<'asc' | 'desc' | null>(() => {
  const [, currentDirection] = props.currentSort.split(',')
  if (activeSortKey.value === null) {
    return null
  }

  return currentDirection === 'asc' ? 'asc' : 'desc'
})

function onNavigationClick(event: Event, item: PostSummary) {
  if (shouldInterceptInquiry(item)) {
    event.preventDefault()
    emit('inquiry-click', item)
  }
}

function getInteractiveTag(item: PostSummary): 'button' | 'router-link' | 'div' {
  if (shouldInterceptInquiry(item)) return 'button'
  if (hasPostRouteTarget(item)) return 'router-link'
  return 'div'
}

function getTitleTag(item: PostSummary): 'button' | 'router-link' | 'span' {
  if (shouldInterceptInquiry(item)) return 'button'
  if (hasPostRouteTarget(item)) return 'router-link'
  return 'span'
}

function getTitleProps(item: PostSummary) {
  const tag = getTitleTag(item)

  if (tag === 'button') {
    return { type: 'button' }
  }

  if (tag === 'router-link') {
    return { to: getPostLink(item) }
  }

  return { title: t('board.invalidUrl') }
}

const columns = computed(() => {
  const cols = []

  if (!props.hideNoColumn) {
    cols.push({
      key: 'postId',
      label: t('common.no'),
      width: '10%',
      align: 'center' as const,
      sortable: true
    })
  }

  if (props.showBoardName) {
    cols.push({
      key: 'boardName',
      label: t('common.board'),
      width: '14%',
      align: 'left' as const
    })
  }

  cols.push({
    key: 'title',
    label: t('common.title'),
    width: props.showBoardName ? '34%' : '48%',
    align: 'left' as const
  })

  cols.push({
    key: 'author',
    label: t('common.author'),
    width: '13%',
    align: 'left' as const
  })

  cols.push({
    key: 'likeCount',
    label: t('common.likes'),
    width: '8%',
    align: 'center' as const,
    sortable: true
  })

  cols.push({
    key: 'viewCount',
    label: t('common.views'),
    width: '8%',
    align: 'right' as const,
    sortable: true
  })

  cols.push({
    key: 'createdAt',
    label: t('common.date'),
    width: '13%',
    align: 'center' as const,
    sortable: false
  })

  return cols
})
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
      :max-author-name-length="MAX_AUTHOR_NAME_LENGTH"
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
