import { computed, defineComponent, reactive, ref } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { QueryObserver } from '@tanstack/query-core'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { commentApi } from '@/api/comment'
import { apiDataResponse, pageResponseFixture } from '@/test/apiResponseFixtures'
import { emitCommentStreamEvent } from '@/features/comments/commentStreamEvents'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import { postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { useComment } from '@/features/comments/queries/useComment'
import { sessionQueryKey } from '@/queryAuthScope'
import { usePostCommentRealtimeSync } from '../usePostCommentRealtimeSync'

const authStore = reactive({ sessionGeneration: 0 })
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('@/api/comment', () => ({ commentApi: { getComments: vi.fn(), getReplies: vi.fn() } }))
vi.mock('@/api/notification', () => ({ notificationApi: {} }))
vi.mock('@/features/notifications/stream/notificationStreamController', () => ({ recycleNotificationStreamConnection: vi.fn() }))
vi.mock('@/features/notifications/stream/notificationStreamConnectionEvents', () => ({
  getNotificationStreamConnection: () => null,
  subscribeNotificationStreamConnection: () => () => {},
}))

let queryClient: QueryClient
let wrapper: VueWrapper | undefined
let stopOtherSession: (() => void) | undefined
let contentVersion = 0
const postObservers: Array<() => void> = []

function setup() {
  const postId = ref('100')
  const authenticated = ref(true)
  let sync!: ReturnType<typeof usePostCommentRealtimeSync>
  let replies!: ReturnType<ReturnType<typeof useComment>['useInfiniteReplies']>
  wrapper = mount(defineComponent({
    setup() {
      const queries = useComment()
      queries.useInfiniteComments(ref(100), ref({ size: 1 }))
      queries.useInfiniteComments(ref(200), ref({ size: 1 }))
      replies = queries.useInfiniteReplies(ref(100), ref(1), ref({ size: 1 }), ref(true))
      queries.useInfiniteReplies(ref(100), ref(2), ref({ size: 1 }), ref(true))
      queries.useInfiniteReplies(ref(200), ref(3), ref({ size: 1 }), ref(true))
      sync = usePostCommentRealtimeSync({
        postId,
        commentQueryPostId: computed(() => Number(postId.value)),
        currentUserId: ref(9),
        isAuthenticated: authenticated,
        sessionGeneration: computed(() => authStore.sessionGeneration),
        queryClient,
      })
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
  return { postId, authenticated, sync, get replies() { return replies } }
}

function observePostCount(postId: string | number, session = 0) {
  const queryKey = sessionQueryKey(session, postQueryKeys.detail(postId))
  queryClient.setQueryData(queryKey, { commentCount: 5 })
  const request = vi.fn(async () => ({ commentCount: contentVersion }))
  const observer = new QueryObserver(queryClient, { queryKey, queryFn: request })
  postObservers.push(observer.subscribe(() => {}))
  return { request, observer }
}

async function settle() {
  await flushPromises()
  await vi.advanceTimersByTimeAsync(0)
  await flushPromises()
}

function event(action: 'CREATED' | 'UPDATED' | 'DELETED', postId = 100, sessionGeneration = 0) {
  emitCommentStreamEvent({ action, postId, sessionGeneration, commentId: 11, actorUserId: 8, occurredAt: '2026-10-04T00:00:00+09:00' })
}

function clearRequestCounts() {
  vi.mocked(commentApi.getComments).mockClear()
  vi.mocked(commentApi.getReplies).mockClear()
}

describe('post comment realtime cache refresh', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    authStore.sessionGeneration = 0
    contentVersion = 0
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity, staleTime: Infinity } } })
    vi.mocked(commentApi.getComments).mockImplementation(async (postId, params) => (
      apiDataResponse<typeof commentApi.getComments>(pageResponseFixture(
        [{ commentId: Number(postId), content: `root ${contentVersion}` }],
        { number: params.page ?? 0, size: 1, totalElements: 1, totalPages: 1 },
      ))
    ))
    vi.mocked(commentApi.getReplies).mockImplementation(async (parentId, params) => {
      const page = params.page ?? 0
      return apiDataResponse<typeof commentApi.getReplies>({
        content: [{ commentId: Number(parentId) * 10 + page, content: `reply ${contentVersion}` }],
        page, size: 1, totalElements: 2, totalPages: 2, hasNext: page === 0, hasPrevious: page === 1,
      })
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    stopOtherSession?.()
    stopOtherSession = undefined
    postObservers.splice(0).forEach(stop => stop())
    queryClient.clear()
    vi.useRealTimers()
  })

  it.each(['UPDATED', 'DELETED'] as const)('refreshes all loaded reply pages for %s without touching other posts or sessions', async (action) => {
    const state = setup()
    await settle()
    await state.replies.fetchNextPage()
    await settle()
    expect(state.replies.data.value?.pages).toHaveLength(2)
    const otherSessionRequest = vi.fn(async () => ({ pages: [], pageParams: [] }))
    const otherSession = new QueryObserver(queryClient, {
      queryKey: sessionQueryKey(99, commentQueryKeys.infiniteReplies(100, 1, { size: 1 })),
      queryFn: otherSessionRequest,
    })
    stopOtherSession = otherSession.subscribe(() => {})
    await otherSession.refetch()
    otherSessionRequest.mockClear()
    clearRequestCounts()
    contentVersion = 1

    event(action)
    await vi.advanceTimersByTimeAsync(2000)
    await settle()
    expect(vi.mocked(commentApi.getComments).mock.calls.map(([id]) => id)).toEqual([100])
    expect(vi.mocked(commentApi.getReplies).mock.calls.map(([id, params]) => [id, params.page])).toEqual([[1, 0], [2, 0], [1, 1]])
    expect(state.replies.data.value?.pages.flatMap((page) => page.content.map((item) => item.content))).toEqual(['reply 1', 'reply 1'])
    expect(otherSessionRequest).not.toHaveBeenCalled()
  })

  it.each(['UPDATED', 'DELETED', 'INVALIDATED'] as const)('refreshes current post counts for %s after the debounce', async (action) => {
    setup()
    const routePost = observePostCount('100')
    const numericPost = observePostCount(100)
    const otherPost = observePostCount('200')
    const otherSession = observePostCount('100', 99)
    await settle()
    contentVersion = 1
    if (action === 'INVALIDATED') {
      emitCommentStreamEvent({ action, postId: 100, sessionGeneration: 0, occurredAt: '2026-11-05T12:00:00' })
    } else {
      event(action)
    }
    await vi.advanceTimersByTimeAsync(1999)
    expect(routePost.request).not.toHaveBeenCalled()
    expect(numericPost.request).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1)
    await settle()
    expect(routePost.observer.getCurrentResult().data?.commentCount).toBe(1)
    expect(numericPost.observer.getCurrentResult().data?.commentCount).toBe(1)
    expect(routePost.request).toHaveBeenCalledTimes(1)
    expect(numericPost.request).toHaveBeenCalledTimes(1)
    expect(otherPost.request).not.toHaveBeenCalled()
    expect(otherSession.request).not.toHaveBeenCalled()
  })

  it('refreshes anonymous invalidation without showing a new-comment banner', async () => {
    const state = setup()
    await settle()
    clearRequestCounts()
    contentVersion = 3
    emitCommentStreamEvent({ action: 'INVALIDATED', postId: 100, sessionGeneration: 0,
      occurredAt: '2026-11-05T12:00:00' })
    expect(state.sync.pendingCommentCount.value).toBe(0)
    await vi.advanceTimersByTimeAsync(2000)
    await settle()
    expect(state.sync.pendingCommentCount.value).toBe(0)
    expect(vi.mocked(commentApi.getComments).mock.calls.map(([id]) => id)).toEqual([100])
    expect(state.replies.data.value?.pages[0]?.content[0]?.content).toBe('reply 3')
  })

  it('drops anonymous invalidation from another post or an old session', async () => {
    const state = setup()
    await settle()
    clearRequestCounts()
    emitCommentStreamEvent({ action: 'INVALIDATED', postId: 200, sessionGeneration: 0, occurredAt: 'now' })
    emitCommentStreamEvent({ action: 'INVALIDATED', postId: 100, sessionGeneration: 99, occurredAt: 'now' })
    await vi.advanceTimersByTimeAsync(2000)
    await settle()
    expect(commentApi.getComments).not.toHaveBeenCalled()
    expect(commentApi.getReplies).not.toHaveBeenCalled()
    expect(state.sync.pendingCommentCount.value).toBe(0)
  })

  it('refreshes expanded replies when the user accepts a new-comment notification', async () => {
    const state = setup()
    await settle()
    clearRequestCounts()
    const postCount = observePostCount('100')
    event('CREATED')
    expect(postCount.request).not.toHaveBeenCalled()
    expect(state.sync.pendingCommentCount.value).toBe(1)
    expect(commentApi.getReplies).not.toHaveBeenCalled()
    contentVersion = 2
    state.sync.refreshComments()
    await settle()
    expect(state.sync.pendingCommentCount.value).toBe(0)
    expect(postCount.observer.getCurrentResult().data?.commentCount).toBe(2)
    expect(postCount.request).toHaveBeenCalledTimes(1)
    expect(state.replies.data.value?.pages[0]?.content[0]?.content).toBe('reply 2')
    expect(vi.mocked(commentApi.getReplies).mock.calls.map(([id]) => id)).toEqual([1, 2])
  })

  it.each(['post', 'session', 'logout'] as const)('drops a scheduled refresh after a %s change', async (change) => {
    const state = setup()
    await settle()
    const postCount = observePostCount('100')
    event('UPDATED')
    if (change === 'post') state.postId.value = '200'
    if (change === 'session') authStore.sessionGeneration += 1
    if (change === 'logout') state.authenticated.value = false
    await settle()
    clearRequestCounts()
    await vi.advanceTimersByTimeAsync(2000)
    await settle()
    expect(commentApi.getComments).not.toHaveBeenCalled()
    expect(commentApi.getReplies).not.toHaveBeenCalled()
    expect(postCount.request).not.toHaveBeenCalled()
  })

  it('ignores events from other posts and old sessions', async () => {
    setup()
    await settle()
    clearRequestCounts()
    event('UPDATED', 200)
    event('UPDATED', 100, 99)
    await vi.advanceTimersByTimeAsync(2000)
    await settle()
    expect(commentApi.getComments).not.toHaveBeenCalled()
    expect(commentApi.getReplies).not.toHaveBeenCalled()
  })

  it('allows a manual refresh after a reply request fails without automatically retrying it', async () => {
    const state = setup()
    await settle()
    clearRequestCounts()
    vi.mocked(commentApi.getReplies).mockRejectedValueOnce(new Error('reply unavailable'))
    event('UPDATED')
    await vi.advanceTimersByTimeAsync(2000)
    await settle()
    expect(state.replies.error.value?.message).toBe('reply unavailable')
    expect(vi.mocked(commentApi.getReplies).mock.calls.filter(([id]) => id === 1)).toHaveLength(1)
    state.sync.refreshComments()
    await settle()
    expect(state.replies.error.value).toBeNull()
    expect(vi.mocked(commentApi.getReplies).mock.calls.filter(([id]) => id === 1)).toHaveLength(2)
  })
})
