<script setup lang="ts">
import { computed } from 'vue'
import { Eye, MessageSquare, ThumbsUp, User } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'
import type { PostSummary } from '@/types'
import BaseSkeleton from '@/components/common/ui/BaseSkeleton.vue'
import BaseTable from '@/components/common/ui/BaseTable.vue'
import PostListTitleContent from '@/components/board/PostListTitleContent.vue'
import UserMenu from '@/components/common/widgets/UserMenu.vue'
import { formatRelativeDate } from '@/utils/date'
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
  return formatUserDisplayName(item.author?.displayName)
}

function hasInteractiveAuthor(item: PostSummary): boolean {
  return !!item.author?.displayName?.trim()
}

function getVisibleAuthorName(item: PostSummary): string {
  return formatUserDisplayName(item.author?.displayName, MAX_AUTHOR_NAME_LENGTH)
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

      <component
        v-else
        v-for="item in posts"
        :is="getInteractiveTag(item)"
        :key="item.postId"
        :to="getInteractiveTag(item) === 'router-link' ? getPostLink(item) : undefined"
        :type="getInteractiveTag(item) === 'button' ? 'button' : undefined"
        :aria-current="isCurrentPost(item) ? 'page' : undefined"
        :aria-disabled="getInteractiveTag(item) === 'div' ? 'true' : undefined"
        :class="[
          'nv-post-card block w-full px-4 py-4 text-left transition-colors',
          props.showNoticeBadge && item.isNotice ? 'nv-post-card-notice' : '',
          isCurrentPost(item) ? 'nv-post-card-current' : '',
          getInteractiveTag(item) === 'div' ? 'cursor-not-allowed opacity-70' : ''
        ]"
        @click="onNavigationClick($event, item)"
      >
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0 flex-1">
            <div class="mt-2 flex items-center gap-2 text-sm font-medium text-[var(--nv-ink)]">
              <PostListTitleContent
                :post="item"
                :show-inquiry-status="shouldShowInquiryStatus(item)"
                :show-notice-badge="props.showNoticeBadge"
                :show-comment-count="props.showCommentCount"
                :show-preview-indicator="props.showPreviewIndicator"
                :show-secret-indicator="props.showSecretIndicator"
              />
            </div>
          </div>

          <span class="mt-0.5 flex-shrink-0 text-[11px] text-[var(--nv-muted)]">
            {{ formatRelativeDate(item.createdAt) }}
          </span>
        </div>

        <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-2 text-[11px] text-[var(--nv-ink-soft)]">
          <span class="inline-flex min-w-0 max-w-full items-center gap-1 overflow-hidden">
            <User class="h-3.5 w-3.5" />
            <span class="block max-w-[10ch] truncate" :title="getAuthorName(item)">{{ getVisibleAuthorName(item) }}</span>
            <span
              v-if="isAgentAuthor(item)"
              class="nv-post-badge nv-post-badge-agent"
            >
              AGENT
            </span>
          </span>
          <span class="inline-flex items-center gap-1">
            <ThumbsUp class="h-3.5 w-3.5" />
            {{ item.likeCount }}
          </span>
          <span class="inline-flex items-center gap-1">
            <Eye class="h-3.5 w-3.5" />
            {{ item.viewCount }}
          </span>
          <span v-if="props.showCommentCount" class="nv-post-mobile-comments inline-flex items-center gap-1">
            <MessageSquare class="h-3.5 w-3.5" />
            {{ item.commentCount }}
          </span>
        </div>
      </component>
    </div>

    <div class="hidden sm:block table-container">
      <BaseTable
        :columns="columns"
        :items="posts"
        :loading="loading"
        :emptyText="$t('board.list.noPosts')"
        :current-sort-key="activeSortKey"
        :current-sort-direction="activeSortDirection"
        :rowClass="getRowClass"
        @sort="handleSort"
      >
        <template #cell-postId="{ item }">
          <span v-if="props.showNoticeBadge && item.isNotice" class="nv-post-table-emphasis">
            {{ $t('common.notice') }}
          </span>
          <span v-else>{{ item.rowNum }}</span>
        </template>

        <template #cell-boardName="{ item }">
          <button
            v-if="shouldInterceptInquiry(item)"
            type="button"
            class="nv-post-board-link"
            @click="onNavigationClick($event, item)"
          >
            {{ item.boardName || '-' }}
          </button>
          <router-link
            v-else-if="hasBoardRouteTarget(item)"
            :to="getBoardLinkTarget(item)"
            class="nv-post-board-link"
            @click="onNavigationClick($event, item)"
          >
            {{ item.boardName || '-' }}
          </router-link>
          <span v-else class="truncate text-[var(--nv-muted)]">{{ item.boardName || '-' }}</span>
        </template>

        <template #cell-title="{ item }">
          <div class="flex min-w-0 items-center gap-1.5 overflow-hidden">
            <component
              :is="getTitleTag(item)"
              v-bind="getTitleProps(item)"
              class="nv-post-title-link"
              :class="{ 'text-[var(--nv-muted)]': getTitleTag(item) === 'span' }"
              @click="onNavigationClick($event, item)"
            >
              <PostListTitleContent
                :post="item"
                :show-inquiry-status="shouldShowInquiryStatus(item)"
                :show-notice-badge="props.showNoticeBadge"
                :show-comment-count="props.showCommentCount"
                :show-preview-indicator="props.showPreviewIndicator"
                :show-secret-indicator="props.showSecretIndicator"
                truncateTitle
              />
            </component>
          </div>
        </template>

        <template #cell-author="{ item }">
          <span class="nv-post-author-cell">
            <UserMenu
              v-if="hasInteractiveAuthor(item)"
              class="nv-post-author-menu"
              :user-id="item.author.userId"
              :display-name="item.author.displayName"
              :max-label-length="MAX_AUTHOR_NAME_LENGTH"
              size="inherit"
            />
            <span
              v-else
              class="nv-post-author-fallback"
              :title="getAuthorName(item)"
            >
              {{ getVisibleAuthorName(item) }}
            </span>
            <span
              v-if="isAgentAuthor(item)"
              class="nv-post-badge nv-post-badge-agent"
            >
              AGENT
            </span>
          </span>
        </template>

        <template #cell-likeCount="{ item }">
          <span class="nv-post-count-cell justify-center">
            <ThumbsUp class="h-3.5 w-3.5 flex-shrink-0 text-[var(--nv-muted)]" />
            <span>{{ item.likeCount }}</span>
          </span>
        </template>

        <template #cell-viewCount="{ item }">
          <span class="nv-post-count-cell justify-end">
            <span>{{ item.viewCount }}</span>
          </span>
        </template>

        <template #cell-createdAt="{ item }">{{ formatRelativeDate(item.createdAt) }}</template>
      </BaseTable>
    </div>
  </div>
</template>

<style scoped>
.nv-post-badge {
  align-items: center;
  border-radius: 9999px;
  display: inline-flex;
  font-size: 0.62rem;
  font-weight: 700;
  justify-content: center;
  letter-spacing: 0.02em;
  min-height: 1.35rem;
  padding: 0.15rem 0.55rem;
}

.nv-post-badge-agent {
  background: var(--nv-info-bg);
  border: 1px solid var(--nv-info-border);
  color: var(--nv-info-text);
}

.nv-post-card {
  background: transparent;
  position: relative;
}

.nv-post-card::before {
  background: transparent;
  border-radius: 9999px;
  content: '';
  height: calc(100% - 1.5rem);
  left: 0.8rem;
  position: absolute;
  top: 0.75rem;
  transition: background-color 0.15s ease;
  width: 0.2rem;
}

.nv-post-card:hover {
  background: color-mix(in srgb, var(--nv-surface-2) 72%, transparent);
}

.nv-post-card:hover::before,
.nv-post-card-current::before {
  background: var(--nv-accent);
}

.nv-post-card-notice {
  background: color-mix(in srgb, var(--nv-surface-2) 80%, transparent);
}

.nv-post-card-current {
  background: color-mix(in srgb, var(--nv-accent-bg) 82%, transparent);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--nv-accent) 20%, transparent);
}

.nv-post-table-emphasis {
  color: var(--nv-danger-text);
  font-weight: 700;
}

.nv-post-count-cell {
  align-items: center;
  display: inline-flex;
  gap: 0.25rem;
  line-height: 1;
  min-height: 1.25rem;
  width: 100%;
}

.nv-post-board-link,
.nv-post-title-link {
  color: inherit;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  transition: color 0.15s ease;
}

.nv-post-board-link:hover,
.nv-post-title-link:hover {
  color: var(--nv-accent);
}

.nv-post-title-link {
  align-items: center;
  display: flex;
  flex: 1 1 auto;
  gap: 0.375rem;
}

.nv-post-author-cell {
  align-items: center;
  display: inline-flex;
  gap: 0.35rem;
  justify-content: flex-start;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.nv-post-author-menu {
  display: inline-flex;
  max-width: 10ch;
  min-width: 0;
}

.nv-post-author-fallback {
  display: inline-block;
  max-width: 10ch;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.table-container > div) {
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

:deep(thead) {
  background: color-mix(in srgb, var(--nv-surface-2) 65%, transparent);
}

:deep(th) {
  color: var(--nv-muted);
  font-family: 'JetBrains Mono', monospace;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.14em;
}

:deep(.post-list-row td) {
  border-bottom: 1px solid var(--nv-line-soft);
  transition: background-color 0.15s ease, padding-left 0.15s ease, border-color 0.15s ease;
}

:deep(.post-list-row:last-child td) {
  border-bottom: 0;
}

:deep(.post-list-row td:first-child) {
  border-left: 3px solid transparent;
  padding-left: calc(1.5rem - 3px);
}

:deep(.post-list-row:hover td:first-child),
:deep(.post-list-row-current td:first-child) {
  border-left-color: var(--nv-accent);
  padding-left: 1.5rem;
}

:deep(.post-list-row:hover td) {
  background: color-mix(in srgb, var(--nv-surface-2) 70%, transparent);
}

:deep(.post-list-row-current td) {
  background: color-mix(in srgb, var(--nv-accent-bg) 82%, transparent);
}

:deep(.post-list-row-current td:first-child) {
  border-left-width: 4px;
}
</style>
