import { firstQueryValue } from '@/utils/routeQueryValue'

export const DEFAULT_POST_LIST_SORT = 'createdAt,desc'

// Shared by keyword and tag listings, whose APIs support the same post fields.
export function normalizePostListSort(value: unknown): string {
  const sort = firstQueryValue(value).trim()
  return /^(createdAt|postId|likeCount|viewCount),(asc|desc)$/.test(sort)
    ? sort
    : DEFAULT_POST_LIST_SORT
}
