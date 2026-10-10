import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, reactive, ref } from 'vue'
import { QueryClient, VueQueryPlugin, onlineManager } from '@tanstack/vue-query'
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'
import { usePostDetailActions } from '@/features/board/posts/detail/usePostDetailActions'
import { createDeferred } from '@/test/async'
import type { Post } from '@/types'

const mocks = vi.hoisted(() => ({
  addToast: vi.fn(), confirm: vi.fn(), deleteMutate: vi.fn(), likeMutate: vi.fn(),
  reportMutate: vi.fn(), scrapMutate: vi.fn(), unlikeMutate: vi.fn(), unscrapMutate: vi.fn(),
  loggerError: vi.fn(), authStore: null as unknown as {
    isAuthenticated: boolean, sessionGeneration: number, user: { userId: number }, accessToken: string,
  },
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({ addToast: mocks.addToast }) }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: mocks.confirm }) }))
vi.mock('@/features/board/posts/queries/usePost', async () => {
  const { useAuthScopedMutation } = await import('@/composables/useAuthScopedMutation')
  const mutation = (handler: (variables: unknown) => Promise<unknown>) =>
    useAuthScopedMutation({ mutationFn: (variables: unknown) => handler(variables) })
  return { usePost: () => ({
    useDeletePost: () => mutation(mocks.deleteMutate),
    useLikePost: () => mutation(mocks.likeMutate),
    useReportPost: () => mutation(mocks.reportMutate),
    useScrapPost: () => mutation(mocks.scrapMutate),
    useUnlikePost: () => mutation(mocks.unlikeMutate),
    useUnscrapPost: () => mutation(mocks.unscrapMutate),
  }) }
})
vi.mock('@/utils/logger', () => ({ default: { error: mocks.loggerError } }))
const wrappers: VueWrapper[] = []
const clients: QueryClient[] = []
const route = reactive({ params: { postId: '7' } })
const router = { push: vi.fn() }
const postFactory = (overrides: Partial<Post> = {}): Post => ({
  postId: 7, title: 'Post', contents: '<p>Body</p>', viewCount: 1, likeCount: 0,
  commentCount: 0, isNotice: false, isNsfw: false, isSpoiler: false, liked: false, scrapped: false,
  author: { userId: 1, displayName: 'Author' },
  board: { boardId: 1, boardName: 'Free', boardUrl: 'free' }, createdAt: '2026-01-01T00:00:00',
  ...overrides,
})
function createActions(overrides: { authenticated?: boolean, canReport?: boolean, post?: Post | null } = {}) {
  const closeOverflowMenu = vi.fn()
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  clients.push(client)
  let actions!: ReturnType<typeof usePostDetailActions>
  mocks.authStore.isAuthenticated = overrides.authenticated ?? true
  wrappers.push(mount(defineComponent({ setup() {
    actions = usePostDetailActions({
      post: ref(overrides.post === undefined ? postFactory() : overrides.post),
      canReport: computed(() => overrides.canReport ?? true), authStore: mocks.authStore,
      route: route as unknown as RouteLocationNormalizedLoaded, router: router as unknown as Router,
      t: key => key, buildBoardListRoute: boardUrl => ({ name: 'board-detail', params: { boardUrl } }),
      closeOverflowMenu,
    })
    return () => h('div')
  } }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }))
  return { actions, closeOverflowMenu }
}

describe('usePostDetailActions with real scoped Vue Query mutations', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.authStore = reactive({ isAuthenticated: true, sessionGeneration: 1, user: { userId: 1 }, accessToken: 'first' })
    mocks.confirm.mockResolvedValue(true)
    route.params.postId = '7'
  })
  afterEach(() => {
    onlineManager.setOnline(true)
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    clients.splice(0).forEach(client => client.clear())
  })
  it('confirms and deletes the original post before navigating', async () => {
    const { actions } = createActions()
    await actions.handleDelete()
    expect(mocks.confirm).toHaveBeenCalledWith('common.messages.confirmDelete')
    expect(mocks.deleteMutate).toHaveBeenCalledWith('7')
    expect(router.push).toHaveBeenCalledWith({ name: 'board-detail', params: { boardUrl: 'free' } })
  })
  it.each(['cancel', 'route', 'session', 'principal'])('discards a delete confirmation after %s', async reason => {
    const confirmation = createDeferred<boolean>()
    mocks.confirm.mockReturnValueOnce(confirmation.promise)
    const { actions } = createActions()
    const pending = actions.handleDelete()
    if (reason === 'route') route.params.postId = '8'
    if (reason === 'session') mocks.authStore.sessionGeneration += 1
    if (reason === 'principal') mocks.authStore.user.userId = 2
    confirmation.resolve(reason !== 'cancel')
    await pending
    expect(mocks.deleteMutate).not.toHaveBeenCalled()
  })
  it.each([false, true])('selects the correct like/bookmark operations for previous state %s', async previous => {
    const { actions } = createActions({ post: postFactory({ liked: previous, scrapped: previous }) })
    await actions.handleLike()
    await actions.handleBookmark()
    expect(previous ? mocks.unlikeMutate : mocks.likeMutate).toHaveBeenCalledWith('7')
    expect(previous ? mocks.unscrapMutate : mocks.scrapMutate).toHaveBeenCalledWith('7')
  })
  it.each(['like', 'bookmark'] as const)('keeps a new %s pending when an old session finishes', async kind => {
    const first = createDeferred<void>()
    const second = createDeferred<void>()
    const mutation = kind === 'like' ? mocks.likeMutate : mocks.scrapMutate
    mutation.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { actions } = createActions()
    const run = kind === 'like' ? actions.handleLike : actions.handleBookmark
    const pending = kind === 'like' ? actions.isLikeMutationPending : actions.isBookmarkMutationPending
    const oldRun = run()
    await flushPromises()
    await run()
    expect(mutation).toHaveBeenCalledTimes(1)
    mocks.authStore.sessionGeneration += 1
    expect(pending.value).toBe(false)
    const newRun = run()
    await flushPromises()
    first.resolve()
    await oldRun
    expect(pending.value).toBe(true)
    second.resolve()
    await newRun
    expect(pending.value).toBe(false)
  })
  it('settles a report after session cancellation instead of waiting for skipped callbacks', async () => {
    const response = createDeferred<void>()
    mocks.reportMutate.mockReturnValueOnce(response.promise)
    const { actions } = createActions()
    actions.openReportModal()
    const submission = actions.submitReport('Spam')
    await flushPromises()
    mocks.authStore.sessionGeneration += 1
    expect(actions.showReportModal.value).toBe(false)
    response.resolve()
    await expect(submission).resolves.toBe(false)
    expect(mocks.addToast).not.toHaveBeenCalled()
    expect(mocks.loggerError).not.toHaveBeenCalled()
  })
  it('settles report success/failure and ignores an old modal response', async () => {
    const { actions } = createActions()
    actions.openReportModal()
    await expect(actions.submitReport('Spam')).resolves.toBe(true)
    expect(mocks.reportMutate).toHaveBeenCalledWith({ targetPostId: '7', reason: 'Spam' })
    expect(actions.showReportModal.value).toBe(false)
    actions.openReportModal()
    mocks.reportMutate.mockRejectedValueOnce(new Error('failed'))
    await expect(actions.submitReport('Abuse')).resolves.toBe(false)
    expect(mocks.addToast).toHaveBeenCalledWith('board.postDetail.reportFailed', 'error')
    const response = createDeferred<void>()
    mocks.reportMutate.mockReturnValueOnce(response.promise)
    const oldReport = actions.submitReport('Old')
    await flushPromises()
    actions.closeReportModal()
    actions.openReportModal()
    response.resolve()
    await expect(oldReport).resolves.toBe(false)
    expect(actions.showReportModal.value).toBe(true)
  })
  it('prevents stale offline work from executing after reconnect while releasing local pending', async () => {
    onlineManager.setOnline(false)
    const { actions } = createActions()
    const pending = actions.handleLike()
    await flushPromises()
    expect(actions.isLikeMutationPending.value).toBe(true)
    mocks.authStore.sessionGeneration += 1
    expect(actions.isLikeMutationPending.value).toBe(false)
    onlineManager.setOnline(true)
    await pending
    expect(mocks.likeMutate).not.toHaveBeenCalled()
  })
  it('allows ordinary token refresh without cancelling the report', async () => {
    const response = createDeferred<void>()
    mocks.reportMutate.mockReturnValueOnce(response.promise)
    const { actions } = createActions()
    actions.openReportModal()
    const submission = actions.submitReport('Spam')
    await flushPromises()
    mocks.authStore.accessToken = 'refreshed'
    response.resolve()
    await expect(submission).resolves.toBe(true)
  })
  it('requires authentication and a post for reactions, and report permission', async () => {
    const blocked = createActions({ authenticated: false, canReport: false }).actions
    blocked.openReportModal()
    await blocked.handleLike()
    await blocked.handleBookmark()
    expect(blocked.showReportModal.value).toBe(false)
    const missing = createActions({ post: null }).actions
    await missing.handleLike()
    await missing.handleBookmark()
    expect(mocks.likeMutate).not.toHaveBeenCalled()
    expect(mocks.scrapMutate).not.toHaveBeenCalled()
  })
  it('does not submit a report for a route changed after opening', async () => {
    const { actions } = createActions()
    actions.openReportModal()
    route.params.postId = '9'
    await expect(actions.submitReport('Spam')).resolves.toBe(false)
    expect(mocks.reportMutate).not.toHaveBeenCalled()
  })
})
