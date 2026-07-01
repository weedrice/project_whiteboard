import type { BoardListItem, FeedPost, HomeLandingPeriod, HomeLandingStats } from '@/types'

export const HOME_BOARD_STRIP_LIMIT = 7

export type HomeStatsCard = {
  label: string
  value: string
  meta: string
}

export type HomeStatsCardLabels = {
  postsToday: string
  postsTodayDeltaVsYesterday: string
  noComparisonData: string
  activeBoards: string
  activeBoardsMeta: string
  newMembers: string
  newMembersMeta: string
  comments: string
  commentsMeta: string
}

export type HomeTrendingPeriodLabels = {
  last24Hours: string
  last7Days: string
  last30Days: string
}

export function selectHomeHeroPost(
  featured: FeedPost | null | undefined,
  editorPicks: FeedPost[],
  trending: FeedPost[],
): FeedPost | null {
  return featured ?? editorPicks[0] ?? trending[0] ?? null
}

export function filterHomePostsExcludingHero(posts: FeedPost[], heroPostId: number | null): FeedPost[] {
  return posts.filter((post) => post.postId !== heroPostId)
}

export function getHomeBoardStrip(
  boards: BoardListItem[],
  limit = HOME_BOARD_STRIP_LIMIT,
): BoardListItem[] {
  return boards.slice(0, limit)
}

export function getHomeRemainingBoardSlots(boardStripLength: number, limit = HOME_BOARD_STRIP_LIMIT): number {
  return Math.max(0, limit - boardStripLength)
}

export function formatHomeSignedNumber(value: number, formatNumber: (value: number) => string): string {
  return value > 0 ? `+${formatNumber(value)}` : formatNumber(value)
}

export function formatHomeSignedPercent(value: number): string {
  return value > 0 ? `+${value}%` : `${value}%`
}

export function formatHomePostsTodayDelta(
  value: number | null,
  labels: Pick<HomeStatsCardLabels, 'postsTodayDeltaVsYesterday' | 'noComparisonData'>,
): string {
  if (value == null) {
    return labels.noComparisonData
  }

  return labels.postsTodayDeltaVsYesterday.replace('{value}', formatHomeSignedPercent(value))
}

export function createHomeStatsCards(
  stats: HomeLandingStats,
  labels: HomeStatsCardLabels,
  formatNumber: (value: number) => string,
): HomeStatsCard[] {
  return [
    {
      label: labels.postsToday,
      value: formatNumber(stats.postsToday),
      meta: formatHomePostsTodayDelta(stats.postsTodayDeltaPercent, labels),
    },
    {
      label: labels.activeBoards,
      value: formatNumber(stats.activeBoardCount),
      meta: labels.activeBoardsMeta,
    },
    {
      label: labels.newMembers,
      value: formatHomeSignedNumber(stats.newMembersLast24Hours, formatNumber),
      meta: labels.newMembersMeta,
    },
    {
      label: labels.comments,
      value: formatNumber(stats.commentsToday),
      meta: labels.commentsMeta,
    },
  ]
}

export function createHomeTrendingPeriods(labels: HomeTrendingPeriodLabels): Array<{ value: HomeLandingPeriod; label: string }> {
  return [
    { value: '24h', label: labels.last24Hours },
    { value: '7d', label: labels.last7Days },
    { value: '30d', label: labels.last30Days },
  ]
}
