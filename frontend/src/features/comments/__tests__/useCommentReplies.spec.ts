import { computed, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCommentReplies } from '@/features/comments/useCommentReplies'
import type { Comment, CommentListResponse } from '@/types'

const repliesData = ref<CommentListResponse>()

vi.mock('@/features/comments/queries/useComment', () => ({
  useComment: () => ({
    useReplies: () => ({
      data: repliesData,
      isLoading: ref(false),
      error: ref(null),
      refetch: vi.fn(),
    }),
  }),
}))

function comment(commentId: number): Comment {
  return {
    commentId,
    content: 'comment',
    author: null,
    likeCount: 0,
    isDeleted: false,
    children: [],
    replyCount: 2,
    hasReplies: true,
    createdAt: '2026-07-17T00:00:00Z',
  }
}

function page(pageNumber: number, ids: number[], hasNext: boolean): CommentListResponse {
  return {
    content: ids.map(comment),
    page: pageNumber,
    size: 1,
    totalElements: 2,
    totalPages: 2,
    hasNext,
    hasPrevious: pageNumber > 0,
  }
}

describe('useCommentReplies', () => {
  beforeEach(() => {
    repliesData.value = undefined
  })

  it('ignores a late response from a previous page', async () => {
    const parent = ref(comment(1))
    const replies = useCommentReplies(computed(() => parent.value))
    repliesData.value = page(0, [10], true)
    await nextTick()

    replies.loadMoreReplies()
    repliesData.value = page(1, [11], false)
    await nextTick()
    expect(replies.replies.value.map((item) => item.commentId)).toEqual([10, 11])

    repliesData.value = page(0, [99], true)
    await nextTick()
    expect(replies.replies.value.map((item) => item.commentId)).toEqual([10, 11])
  })

  it('resets accumulated replies when the parent comment changes', async () => {
    const parent = ref(comment(1))
    const replies = useCommentReplies(computed(() => parent.value))
    repliesData.value = page(0, [10], false)
    await nextTick()

    parent.value = comment(2)
    await nextTick()

    expect(replies.replies.value).toEqual([])
    expect(replies.replyHasNext.value).toBe(false)
  })
})
