import { describe, expect, it } from 'vitest'
import { normalizeBoardAdmin, normalizeSuperAdmin } from '@/api/adminAccountApi'
import { normalizePersonalFeedPage } from '@/api/feed'
import { normalizeEmoticonMaster } from '@/api/emoticon'
import {
  normalizePostDetail,
  normalizePostSummary,
  normalizePostSummaryPage,
  type PostDetailWire,
  type PostSummaryWire,
} from '@/api/postContract'
import type {
  ActionMessageResponse,
  SignupResponse,
  UpdateProfileResponse,
  UserSettingsUpdatePayload,
} from '@/types'

const postSummaryWire = (overrides: Partial<PostSummaryWire> = {}): PostSummaryWire => ({
  postId: 1,
  title: 'wire post',
  viewCount: 3,
  likeCount: 2,
  commentCount: 1,
  isSpoiler: false,
  author: { userId: 7, displayName: 'writer' },
  createdAt: '2026-07-14T00:00:00',
  ...overrides,
})

describe('frontend-backend contract adapters', () => {
  it('normalizes PostSummary boolean aliases and embedded categories', () => {
    const result = normalizePostSummary(postSummaryWire({
      notice: true,
      nsfw: true,
      category: { categoryId: 5, name: 'QnA' },
    }))

    expect(result).toMatchObject({
      isNotice: true,
      isNsfw: true,
      category: { categoryId: 5, name: 'QnA' },
    })
  })

  it('normalizes backend page metadata and every PostSummary item', () => {
    const result = normalizePostSummaryPage({
      content: [postSummaryWire({ notice: true, nsfw: false })],
      page: 2,
      size: 10,
      totalElements: 25,
      totalPages: 3,
      hasNext: false,
      hasPrevious: true,
    })

    expect(result).toMatchObject({ number: 2, first: false, last: true, empty: false })
    expect(result.content[0]).toMatchObject({ isNotice: true, isNsfw: false })
  })

  it('normalizes post detail reaction flags', () => {
    const wire = {
      postId: 1,
      title: 'detail',
      contents: 'body',
      viewCount: 1,
      likeCount: 2,
      commentCount: 3,
      isNotice: false,
      isNsfw: false,
      isSpoiler: false,
      author: { userId: 7, displayName: 'writer' },
      board: { boardId: 1, boardName: 'Free', boardUrl: 'free' },
      createdAt: '2026-07-14T00:00:00',
      isLiked: true,
      isScrapped: false,
    } satisfies PostDetailWire

    expect(normalizePostDetail(wire)).toMatchObject({ liked: true, scrapped: false })
  })

  it('normalizes admin and super-admin wire boolean keys', () => {
    expect(normalizeBoardAdmin({
      adminId: 1,
      role: 'BOARD_ADMIN',
      active: true,
      createdAt: '2026-07-14T00:00:00',
      user: { userId: 2, loginId: 'admin', displayName: 'Admin' },
      board: null,
    }).isActive).toBe(true)

    expect(normalizeSuperAdmin({
      userId: 2,
      loginId: 'super',
      displayName: 'Super',
      superAdmin: true,
      createdAt: '2026-07-14T00:00:00',
    }).isSuperAdmin).toBe(true)
  })

  it('normalizes feed read state, embedded posts, and null emoticon images', () => {
    const feed = normalizePersonalFeedPage({
      content: [{
        feedId: 1,
        feedType: 'SUBSCRIPTION',
        contentType: 'POST',
        contentId: 1,
        read: true,
        createdAt: '2026-07-14T00:00:00',
        post: postSummaryWire({ notice: true, nsfw: false }),
      }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
      hasPrevious: false,
    })

    expect(feed.content[0]).toMatchObject({ isRead: true, post: { isNotice: true } })
    expect(normalizeEmoticonMaster({
      emoticonId: 1,
      name: 'pack',
      tags: [],
      isActive: true,
      images: null,
      createdAt: '2026-07-14T00:00:00',
      modifiedAt: '2026-07-14T00:00:00',
    }).images).toEqual([])
  })

  it('keeps auth, profile, settings, and user action fixtures on their dedicated contracts', () => {
    const signup = {
      userId: 1,
      loginId: 'new-user',
      email: 'new-user@example.com',
      displayName: 'New User',
    } satisfies SignupResponse
    const profile = {
      userId: 1,
      displayName: 'Updated User',
      profileImageUrl: null,
      spentPoints: 10,
      remainingPoints: 90,
    } satisfies UpdateProfileResponse
    const action = { message: 'completed' } satisfies ActionMessageResponse
    const settings = {
      theme: 'DARK',
      language: 'ko',
      timezone: 'Asia/Seoul',
      hideNsfw: true,
    } satisfies UserSettingsUpdatePayload

    expect(signup).toMatchObject({ userId: 1, loginId: 'new-user' })
    expect(profile).toMatchObject({ spentPoints: 10, remainingPoints: 90 })
    expect(action.message).toBe('completed')
    expect(settings).toEqual({
      theme: 'DARK',
      language: 'ko',
      timezone: 'Asia/Seoul',
      hideNsfw: true,
    })
  })
})
