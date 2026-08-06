import { describe, expect, it } from 'vitest'
import { toPostDetailViewModel } from '@/features/board/posts/detail/usePostDetailViewModel'
import type { Post } from '@/types'

const basePost: Post = {
  postId: 15,
  title: 'Test post',
  contents: '<p>body</p>',
  viewCount: 12,
  likeCount: 4,
  commentCount: 2,
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  author: {
    userId: 7,
    displayName: 'Author',
    authorType: 'USER'
  },
  board: {
    boardId: 1,
    boardName: 'Free board',
    boardUrl: 'free',
    isAdmin: false
  },
  tags: ['vue'],
  liked: false,
  scrapped: true,
  editCount: 2,
  createdAt: '2026-04-22T10:00:00',
  modifiedAt: '2026-04-22T10:00:00'
}

describe('toPostDetailViewModel', () => {
  it('flattens post detail fields used by the template', () => {
    expect(toPostDetailViewModel(basePost)).toEqual({
      postId: 15,
      title: 'Test post',
      createdAt: '2026-04-22T10:00:00',
      editCount: 2,
      viewCount: 12,
      commentCount: 2,
      likeCount: 4,
      liked: false,
      scrapped: true,
      lastReadCommentId: null,
      lastViewedAt: null,
      seriesNavigation: null,
      poll: null,
      tags: ['vue'],
      boardName: 'Free board',
      boardUrl: 'free',
      authorUserId: 7,
      authorDisplayName: 'Author',
      representativeBadge: null,
      isBlinded: false,
      blindReason: null,
      isBoardAdmin: false
    })
  })

  it('uses canonical reaction flags without changing the cached post shape', () => {
    const normalizedPost = {
      ...basePost,
      liked: true,
      scrapped: false,
      tags: undefined
    } as Post

    const viewModel = toPostDetailViewModel(normalizedPost)

    expect(viewModel.liked).toBe(true)
    expect(viewModel.scrapped).toBe(false)
    expect(viewModel.tags).toEqual([])
  })
})
