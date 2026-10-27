import { computed, defineComponent, nextTick, reactive, ref } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { commentApi } from '@/api/comment'
import { apiDataResponse, apiSuccessResponse, pageResponseFixture } from '@/test/apiResponseFixtures'
import { useCommentReplies } from '@/features/comments/useCommentReplies'
import { useComment } from '@/features/comments/queries/useComment'
import type { Comment } from '@/types'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'

const authStore = reactive({ sessionGeneration: 0, user: { userId: 9 } })
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('@/api/comment', () => ({
  commentApi: {
    getReplies: vi.fn(), createComment: vi.fn(), updateComment: vi.fn(),
    deleteComment: vi.fn(), likeComment: vi.fn(), unlikeComment: vi.fn(),
  },
}))

function comment(commentId: number): Comment {
  return {
    commentId, content: `comment ${commentId}`, author: null, liked: false,
    likeCount: 0, isDeleted: false, children: [], replyCount: 52,
    hasReplies: true, createdAt: '2026-07-17T00:00:00Z',
  }
}

let serverReplies: Comment[]
let queryClient: QueryClient
let wrapper: VueWrapper | undefined
function setup(parentValue = comment(1), path: readonly number[] = []) {
  const parent = ref(parentValue)
  const deepLinkPath = ref(path)
  let replies!: ReturnType<typeof useCommentReplies>
  let mutations!: {
    update: ReturnType<ReturnType<typeof useComment>['useUpdateComment']>
    remove: ReturnType<ReturnType<typeof useComment>['useDeleteComment']>
    create: ReturnType<ReturnType<typeof useComment>['useCreateComment']>
    like: ReturnType<ReturnType<typeof useComment>['useToggleCommentLike']>
  }
  wrapper = mount(defineComponent({
    setup() {
      replies = useCommentReplies(computed(() => parent.value), ref(100), computed(() => deepLinkPath.value))
      const queries = useComment()
      mutations = {
        update: queries.useUpdateComment(), remove: queries.useDeleteComment(),
        create: queries.useCreateComment(), like: queries.useToggleCommentLike(),
      }
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
  return { replies, parent, deepLinkPath, mutations }
}

async function loadTwoPages(replies: ReturnType<typeof useCommentReplies>) {
  replies.toggleReplies()
  await vi.waitFor(() => expect(replies.replies.value).toHaveLength(50))
  replies.loadMoreReplies()
  await vi.waitFor(() => expect(replies.replies.value).toHaveLength(52))
}

const ids = (items: Comment[]) => items.map((item) => item.commentId)

describe('useCommentReplies with the query cache', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authStore.sessionGeneration = 0
    serverReplies = Array.from({ length: 52 }, (_, index) => comment(index + 10))
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    vi.mocked(commentApi.getReplies).mockImplementation(async (_id, params) => {
      const page = params?.page ?? 0
      const size = params?.size ?? 50
      return apiDataResponse<typeof commentApi.getReplies>({
        content: serverReplies.slice(page * size, (page + 1) * size).map((item) => ({ ...item })),
        page, size, totalElements: serverReplies.length,
        totalPages: Math.ceil(serverReplies.length / size),
        hasNext: (page + 1) * size < serverReplies.length, hasPrevious: page > 0,
      })
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    queryClient.clear()
  })

  it('waits until the branch opens and follows a deep link through later pages', async () => {
    const { replies, deepLinkPath } = setup()
    await nextTick()
    expect(commentApi.getReplies).not.toHaveBeenCalled()
    deepLinkPath.value = [1, 61]
    await vi.waitFor(() => expect(replies.replies.value).toHaveLength(52))
    expect(vi.mocked(commentApi.getReplies).mock.calls.map(([, params]) => params?.page)).toEqual([0, 1])
    expect(replies.isRepliesOpen.value).toBe(true)
    deepLinkPath.value = [2, 99]
    await nextTick()
    expect(replies.isRepliesOpen.value).toBe(false)
  })

  it('continues a deep link from fresh cached pages without refetching the first page', async () => {
    const key = sessionQueryKey(0, commentQueryKeys.infiniteReplies(100, 1, { size: 50 }))
    queryClient.setQueryDefaults(key, { staleTime: Infinity })
    queryClient.setQueryData(key, {
      pages: [pageResponseFixture(serverReplies.slice(0, 50), {
        number: 0, size: 50, totalElements: 52, totalPages: 2, last: false,
      })],
      pageParams: [0],
    })
    const { replies } = setup(comment(1), [1, 61])
    await vi.waitFor(() => expect(replies.replies.value).toHaveLength(52))
    expect(vi.mocked(commentApi.getReplies).mock.calls.map(([, params]) => params?.page)).toEqual([1])
  })

  it('refreshes edits on both earlier and later loaded pages', async () => {
    const { replies, mutations } = setup()
    await loadTwoPages(replies)
    vi.mocked(commentApi.updateComment).mockImplementation(async (id, data) => {
      serverReplies = serverReplies.map((item) => item.commentId === id ? { ...item, content: data.content } : item)
      return apiDataResponse<typeof commentApi.updateComment>(Number(id))
    })
    for (const id of [10, 61]) {
      await mutations.update.mutateAsync({ postId: 100, commentId: id, data: { content: `edited ${id}` } })
      await vi.waitFor(() => expect(replies.replies.value.find((item) => item.commentId === id)?.content).toBe(`edited ${id}`))
    }
    expect(replies.replies.value).toHaveLength(52)
  })

  it('shows optimistic likes and rollbacks on every loaded page', async () => {
    const { replies, mutations } = setup()
    await loadTwoPages(replies)
    for (const id of [10, 61]) {
      let rejectLike!: (error: Error) => void
      vi.mocked(commentApi.likeComment).mockImplementationOnce(() => new Promise((_resolve, reject) => {
        rejectLike = reject
      }))
      const operation = mutations.like.mutateAsync({ postId: 100, commentId: id, liked: true })
      const failure = operation.catch((error: unknown) => error)
      await vi.waitFor(() => expect(replies.replies.value.find((item) => item.commentId === id)).toMatchObject({ liked: true, likeCount: 1 }))
      rejectLike(new Error('like failed'))
      expect(await failure).toMatchObject({ message: 'like failed' })
      await vi.waitFor(() => expect(replies.replies.value.find((item) => item.commentId === id)).toMatchObject({ liked: false, likeCount: 0 }))
    }
  })

  it('replaces page boundaries after deletion and includes newly created replies', async () => {
    const { replies, mutations } = setup()
    await loadTwoPages(replies)
    vi.mocked(commentApi.deleteComment).mockImplementation(async (id) => {
      serverReplies = serverReplies.filter((item) => item.commentId !== id)
      return apiSuccessResponse<typeof commentApi.deleteComment>()
    })
    await mutations.remove.mutateAsync({ commentId: 10, postId: 100 })
    await vi.waitFor(() => expect(ids(replies.replies.value)).toEqual(ids(serverReplies)))
    vi.mocked(commentApi.createComment).mockImplementation(async () => {
      serverReplies.push(comment(62))
      return apiDataResponse<typeof commentApi.createComment>({ commentId: 62 })
    })
    await mutations.create.mutateAsync({ postId: 100, data: { content: 'new reply', parentId: 1 } })
    replies.markReplyCreated()
    await vi.waitFor(() => expect(ids(replies.replies.value)).toEqual(ids(serverReplies)))
  })

  it('stops automatic deep-link paging after a failure until the user retries', async () => {
    const { replies, deepLinkPath } = setup()
    replies.toggleReplies()
    await vi.waitFor(() => expect(replies.replies.value).toHaveLength(50))
    vi.mocked(commentApi.getReplies).mockRejectedValueOnce(new Error('page unavailable'))
    deepLinkPath.value = [1, 61]
    await vi.waitFor(() => expect(replies.repliesError.value?.message).toBe('page unavailable'))
    await nextTick()
    expect(commentApi.getReplies).toHaveBeenCalledTimes(2)
    replies.retryReplies()
    await vi.waitFor(() => expect(replies.replies.value).toHaveLength(52))
    expect(vi.mocked(commentApi.getReplies).mock.calls.map(([, params]) => params?.page)).toEqual([0, 1, 1])
  })

  it('retains cached replies after a refresh failure and retries the refresh', async () => {
    const { replies } = setup()
    await loadTwoPages(replies)
    vi.mocked(commentApi.getReplies).mockRejectedValueOnce(new Error('refresh unavailable'))
    await replies.refetchReplies()
    expect(replies.replies.value).toHaveLength(52)
    expect(replies.repliesError.value).toBeTruthy()
    vi.mocked(commentApi.getReplies).mockClear()
    replies.retryReplies()
    await vi.waitFor(() => expect(replies.repliesError.value).toBeNull())
    expect(vi.mocked(commentApi.getReplies).mock.calls.map(([, params]) => params?.page)).toEqual([0, 1])
    expect(replies.replies.value).toHaveLength(52)
  })

  it('ignores a late next-page response after changing the parent', async () => {
    const { replies, parent } = setup()
    replies.toggleReplies()
    await vi.waitFor(() => expect(replies.replies.value).toHaveLength(50))
    let resolveOldPage!: (value: Awaited<ReturnType<typeof commentApi.getReplies>>) => void
    vi.mocked(commentApi.getReplies).mockImplementationOnce(() => new Promise((resolve) => { resolveOldPage = resolve }))
    replies.loadMoreReplies()
    await vi.waitFor(() => expect(resolveOldPage).toBeDefined())
    parent.value = comment(2)
    serverReplies = [comment(99)]
    await nextTick()
    replies.toggleReplies()
    await vi.waitFor(() => expect(ids(replies.replies.value)).toEqual([99]))
    resolveOldPage(apiDataResponse<typeof commentApi.getReplies>({
      content: [comment(61)], page: 1, size: 50,
      totalElements: 51, totalPages: 2, hasNext: false, hasPrevious: true,
    }))
    await nextTick()
    expect(ids(replies.replies.value)).toEqual([99])
  })

  it('keeps surviving replies open when their parent is deleted', async () => {
    const { replies, parent } = setup()
    await loadTwoPages(replies)
    parent.value = { ...parent.value, isDeleted: true }
    await nextTick()
    expect(replies.isRepliesOpen.value).toBe(true)
    expect(replies.replies.value).toHaveLength(52)
  })

  it('opens a deep link through a deleted parent with surviving replies', async () => {
    const { replies } = setup({ ...comment(1), isDeleted: true }, [1, 61])
    await vi.waitFor(() => expect(replies.replies.value).toHaveLength(52))
    expect(replies.isRepliesOpen.value).toBe(true)
  })

  it('does not fetch a deleted branch with no surviving replies', async () => {
    const { replies } = setup({ ...comment(1), isDeleted: true, hasReplies: false, replyCount: 0 })
    replies.toggleReplies()
    await nextTick()
    expect(replies.canLoadReplies.value).toBe(false)
    expect(commentApi.getReplies).not.toHaveBeenCalled()
  })

  it('clears previous parent data and starts the new branch at its first page', async () => {
    const { replies, parent } = setup()
    await loadTwoPages(replies)
    parent.value = comment(2)
    await nextTick()
    expect(replies.replies.value).toEqual([])
    expect(replies.isRepliesOpen.value).toBe(false)
    serverReplies = [comment(99)]
    replies.toggleReplies()
    await vi.waitFor(() => expect(ids(replies.replies.value)).toEqual([99]))
    expect(commentApi.getReplies).toHaveBeenLastCalledWith(2, { size: 50, page: 0 }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
  })

  it('discards the old session pages when authentication changes', async () => {
    const { replies } = setup()
    await loadTwoPages(replies)
    serverReplies = [comment(99)]
    authStore.sessionGeneration += 1
    await nextTick()
    expect(replies.replies.value).toEqual([])
    await vi.waitFor(() => expect(ids(replies.replies.value)).toEqual([99]))
    expect(commentApi.getReplies).toHaveBeenLastCalledWith(1, { size: 50, page: 0 }, expect.anything())
  })
})
