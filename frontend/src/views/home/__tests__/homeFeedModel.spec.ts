import { describe, expect, it } from 'vitest'
import type { BoardListItem, FeedPost, HomeLandingStats } from '@/types'
import {
  createHomeStatsCards,
  createHomeTrendingPeriods,
  filterHomePostsExcludingHero,
  formatHomePostsTodayDelta,
  getHomeBoardStrip,
  getHomeRemainingBoardSlots,
  selectHomeHeroPost,
} from '../homeFeedModel'

const post = (postId: number): FeedPost => ({
  postId,
  boardUrl: 'free',
  boardName: 'Free',
  author: {
    userId: postId,
    displayName: `Author ${postId}`,
  },
  authorName: `Author ${postId}`,
  title: `Post ${postId}`,
  viewCount: 1,
  likeCount: 0,
  commentCount: 0,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  createdAt: '2025-01-01',
  liked: false,
  scrapped: false,
  subscribed: false,
})

const board = (boardId: number): BoardListItem => ({
  boardId,
  boardUrl: `board-${boardId}`,
  boardName: `Board ${boardId}`,
  sortOrder: boardId,
  subscriberCount: 0,
  postCount: 0,
  isSubscribed: false,
  isActive: true,
  isPublic: true,
  subscriptionAccessible: true,
})

describe('homeFeedModel', () => {
  it('selects the hero post and removes it from other lists', () => {
    expect(selectHomeHeroPost(post(1), [post(2)], [post(3)])?.postId).toBe(1)
    expect(selectHomeHeroPost(null, [post(2)], [post(3)])?.postId).toBe(2)
    expect(selectHomeHeroPost(null, [], [post(3)])?.postId).toBe(3)
    expect(selectHomeHeroPost(null, [], [])).toBeNull()
    expect(filterHomePostsExcludingHero([post(1), post(2)], 1).map((item) => item.postId)).toEqual([2])
  })

  it('calculates board strip slots', () => {
    const boards = Array.from({ length: 10 }, (_, index) => board(index + 1))

    expect(getHomeBoardStrip(boards).map((item) => item.boardId)).toEqual([1, 2, 3, 4, 5, 6, 7])
    expect(getHomeRemainingBoardSlots(6)).toBe(1)
    expect(getHomeRemainingBoardSlots(8)).toBe(0)
  })

  it('creates stat cards and trending period options', () => {
    const stats: HomeLandingStats = {
      boardCount: 0,
      postCount: 0,
      liveCount: 0,
      onlineCount: 0,
      postsToday: 12,
      postsTodayDeltaPercent: 5,
      activeBoardCount: 3,
      newMembersLast24Hours: 2,
      commentsToday: 7,
    }

    const cards = createHomeStatsCards(stats, {
      postsToday: 'Posts',
      postsTodayDeltaVsYesterday: '{value} vs yesterday',
      noComparisonData: 'No comparison',
      activeBoards: 'Boards',
      activeBoardsMeta: 'active',
      newMembers: 'Members',
      newMembersMeta: 'new',
      comments: 'Comments',
      commentsMeta: 'today',
    }, (value) => String(value))

    expect(cards).toEqual([
      { label: 'Posts', value: '12', meta: '+5% vs yesterday' },
      { label: 'Boards', value: '3', meta: 'active' },
      { label: 'Members', value: '+2', meta: 'new' },
      { label: 'Comments', value: '7', meta: 'today' },
    ])
    expect(formatHomePostsTodayDelta(null, {
      postsTodayDeltaVsYesterday: '{value} vs yesterday',
      noComparisonData: 'No comparison',
    })).toBe('No comparison')
    expect(createHomeTrendingPeriods({
      last24Hours: '24h',
      last7Days: '7d',
      last30Days: '30d',
    })).toEqual([
      { value: '24h', label: '24h' },
      { value: '7d', label: '7d' },
      { value: '30d', label: '30d' },
    ])
  })
})
