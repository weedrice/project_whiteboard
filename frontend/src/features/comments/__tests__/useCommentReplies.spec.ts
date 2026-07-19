import { computed, nextTick, ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useCommentReplies } from '@/features/comments/useCommentReplies'
import type { Comment, CommentListResponse } from '@/types'

const repliesData = ref<CommentListResponse>()
let capturedReplyParams: Ref<{ page: number; size: number }> | undefined
let capturedRepliesEnabled: Ref<boolean> | undefined

vi.mock('@/features/comments/queries/useComment', () => ({
  useComment: () => ({
    useReplies: (_commentId: Ref<number>, params: Ref<{ page: number; size: number }>, enabled: Ref<boolean>) => {
      capturedReplyParams = params
      capturedRepliesEnabled = enabled
      return {
      data: repliesData,
      isLoading: ref(false),
      error: ref(null),
      refetch: vi.fn(),
      }
    },
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
    capturedReplyParams = undefined
    capturedRepliesEnabled = undefined
  })

  it('keeps replies closed until the user or a deep link opens the branch', async () => {
    const parent = ref(comment(1))
    const deepLinkPath = ref<readonly number[]>([])
    const replies = useCommentReplies(computed(() => parent.value), computed(() => deepLinkPath.value))

    expect(replies.isRepliesOpen.value).toBe(false)
    expect(capturedRepliesEnabled?.value).toBe(false)

    deepLinkPath.value = [1, 11]
    await nextTick()

    expect(replies.isRepliesOpen.value).toBe(true)
    expect(capturedRepliesEnabled?.value).toBe(true)

    deepLinkPath.value = [2, 20]
    await nextTick()
    expect(replies.isRepliesOpen.value).toBe(false)
  })

  it('loads only the deep-link branch until its direct child is found', async () => {
    const parent = ref(comment(1))
    const deepLinkPath = ref<readonly number[]>([1, 11])
    const replies = useCommentReplies(computed(() => parent.value), computed(() => deepLinkPath.value))

    repliesData.value = page(0, [10], true)
    await nextTick()
    expect(capturedReplyParams?.value.page).toBe(1)

    repliesData.value = page(1, [11], false)
    await nextTick()
    expect(replies.replies.value.map((item) => item.commentId)).toEqual([10, 11])
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
