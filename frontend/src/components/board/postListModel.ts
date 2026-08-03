import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router'
import type { TableColumn } from '@/components/common/ui/BaseTable.vue'
import type { PostSummary } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import { formatUserDisplayName } from '@/utils/userDisplay'

export type ResolvePostRoute = (
  post: PostSummary,
  boardUrl: string,
  linkQuery: LocationQueryRaw | undefined
) => RouteLocationRaw | null | undefined

export type ResolveBoardRoute = (
  post: PostSummary,
  boardUrl: string
) => RouteLocationRaw | null | undefined

export type PostPredicate = (
  post: PostSummary,
  boardUrl: string
) => boolean

export type PostListInteractiveTag = 'button' | 'router-link' | 'div'
export type PostListTitleTag = 'button' | 'router-link' | 'span'

export const POST_LIST_MAX_AUTHOR_NAME_LENGTH = 10

const SORT_FIELD_MAP: Record<string, string> = {
  postId: 'createdAt',
  likeCount: 'likeCount',
  viewCount: 'viewCount'
}

export function isPostListCurrentPost(item: PostSummary, currentPostId?: string): boolean {
  return String(item.postId) === String(currentPostId ?? '')
}

export function getPostListRowClass(item: PostSummary, currentPostId?: string): string {
  return isPostListCurrentPost(item, currentPostId)
    ? 'post-list-row post-list-row-current'
    : 'post-list-row'
}

export function getPostListResolvedBoardUrl(item: PostSummary, boardUrl?: string): string {
  const raw = String(boardUrl || item.boardUrl || '').trim().toLowerCase()
  return raw.replace(/^\/+|\/+$/g, '')
}

export function resolvePostListBoardRoute(
  item: PostSummary,
  boardUrl: string | undefined,
  resolveBoardRoute?: ResolveBoardRoute,
): RouteLocationRaw | null {
  const resolvedBoardUrl = getPostListResolvedBoardUrl(item, boardUrl)
  if (!resolvedBoardUrl) return null
  return resolveBoardRoute?.(item, resolvedBoardUrl) ?? `/board/${encodePathSegment(resolvedBoardUrl)}/`
}

export function resolvePostListPostRoute(
  item: PostSummary,
  boardUrl: string | undefined,
  linkQuery: LocationQueryRaw | undefined,
  resolvePostRoute?: ResolvePostRoute,
): RouteLocationRaw | null {
  const resolvedBoardUrl = getPostListResolvedBoardUrl(item, boardUrl)
  if (!resolvedBoardUrl || !resolvePostRoute) return null
  return resolvePostRoute(item, resolvedBoardUrl, linkQuery) ?? null
}

export function shouldInterceptPostListInquiry(
  item: PostSummary,
  boardUrl: string | undefined,
  interceptInquiry: boolean,
  shouldInterceptPost?: PostPredicate,
): boolean {
  const resolvedBoardUrl = getPostListResolvedBoardUrl(item, boardUrl)
  return interceptInquiry && Boolean(shouldInterceptPost?.(item, resolvedBoardUrl))
}

export function shouldShowPostListInquiryStatus(
  item: PostSummary,
  boardUrl: string | undefined,
  showInquiryStatus?: PostPredicate,
): boolean {
  const resolvedBoardUrl = getPostListResolvedBoardUrl(item, boardUrl)
  return Boolean(showInquiryStatus?.(item, resolvedBoardUrl))
}

export function isPostListAgentAuthor(item: PostSummary): boolean {
  return item.author?.authorType === 'AGENT'
}

export function hasPostListInteractiveAuthor(item: PostSummary): boolean {
  return Boolean(item.author?.displayName?.trim())
}

export function getPostListAuthorName(item: PostSummary, deletedUserLabel: string): string {
  return formatUserDisplayName(item.author?.displayName, undefined, deletedUserLabel)
}

export function getPostListVisibleAuthorName(item: PostSummary, deletedUserLabel: string): string {
  return formatUserDisplayName(item.author?.displayName, POST_LIST_MAX_AUTHOR_NAME_LENGTH, deletedUserLabel)
}

export function getPostListRowNumberLabel(
  item: PostSummary,
  noticeLabel: string,
  showNoticeBadge: boolean,
): string | number | undefined {
  return showNoticeBadge && item.isNotice ? noticeLabel : item.rowNum
}

export function getPostListBoardNameLabel(item: PostSummary): string {
  return item.boardName || '-'
}

export function getPostListCountLabel(value: number): string {
  return String(value)
}

export function getPostListNextSort(currentSort: string, field: string): string {
  const normalizedField = SORT_FIELD_MAP[field] ?? field
  const [currentField, currentDirection] = currentSort.split(',')
  const nextDirection = normalizedField === currentField && currentDirection === 'desc' ? 'asc' : 'desc'
  return `${normalizedField},${nextDirection}`
}

export function getPostListActiveSortKey(currentSort: string): string | null {
  const [currentField] = currentSort.split(',')
  const matchedEntry = Object.entries(SORT_FIELD_MAP).find(([, apiField]) => apiField === currentField)
  return matchedEntry?.[0] ?? null
}

export function getPostListActiveSortDirection(currentSort: string): 'asc' | 'desc' | null {
  const [, currentDirection] = currentSort.split(',')
  if (getPostListActiveSortKey(currentSort) === null) return null
  return currentDirection === 'asc' ? 'asc' : 'desc'
}

export function getPostListInteractiveTag(shouldIntercept: boolean, hasPostRouteTarget: boolean): PostListInteractiveTag {
  if (shouldIntercept) return 'button'
  if (hasPostRouteTarget) return 'router-link'
  return 'div'
}

export function getPostListTitleTag(shouldIntercept: boolean, hasPostRouteTarget: boolean): PostListTitleTag {
  if (shouldIntercept) return 'button'
  if (hasPostRouteTarget) return 'router-link'
  return 'span'
}

export function getPostListTitleProps(
  tag: PostListTitleTag,
  postLink: RouteLocationRaw | null,
  invalidTitle: string,
): Record<string, unknown> {
  if (tag === 'button') return { type: 'button' }
  if (tag === 'router-link') return { to: postLink }
  return { title: invalidTitle }
}

type PostListColumnLabels = {
  no: string
  board: string
  title: string
  author: string
  likes: string
  views: string
  date: string
}

type PostListColumnOptions = {
  showBoardName: boolean
  hideNoColumn: boolean
}

export function createPostListColumns(
  labels: PostListColumnLabels,
  { showBoardName, hideNoColumn }: PostListColumnOptions,
): TableColumn[] {
  const cols: TableColumn[] = []

  if (!hideNoColumn) {
    cols.push({
      key: 'postId',
      label: labels.no,
      width: '10%',
      align: 'center',
      sortable: true
    })
  }

  if (showBoardName) {
    cols.push({
      key: 'boardName',
      label: labels.board,
      width: '14%',
      align: 'left'
    })
  }

  cols.push({
    key: 'title',
    label: labels.title,
    width: showBoardName ? '34%' : '48%',
    align: 'left'
  })
  cols.push({
    key: 'author',
    label: labels.author,
    width: '13%',
    align: 'left'
  })
  cols.push({
    key: 'likeCount',
    label: labels.likes,
    width: '8%',
    align: 'center',
    sortable: true
  })
  cols.push({
    key: 'viewCount',
    label: labels.views,
    width: '8%',
    align: 'right',
    sortable: true
  })
  cols.push({
    key: 'createdAt',
    label: labels.date,
    width: '13%',
    align: 'center',
    sortable: false
  })

  return cols
}
