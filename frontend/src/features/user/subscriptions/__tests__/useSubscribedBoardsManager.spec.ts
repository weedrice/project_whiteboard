import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { useSubscribedBoardsManager } from '../useSubscribedBoardsManager'
import { axiosApiPageSuccess } from '@/test/apiResponseFixtures'
import { apiEmptySuccess, axiosApiResponse } from '@/test/factories'
import { createDeferred } from '@/test/async'
import type { SubscriptionBoardListItem } from '@/types'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

const mocks = vi.hoisted(() => ({
  getMySubscriptions: vi.fn(),
  unsubscribeBoard: vi.fn(),
  updateSubscriptionOrder: vi.fn(),
  confirm: vi.fn(),
  addToast: vi.fn(),
  handleSilentError: vi.fn(),
  handleError: vi.fn(),
  invalidateQueries: vi.fn(),
  mediaQueryAddEventListener: vi.fn(),
  mediaQueryRemoveEventListener: vi.fn(),
  authStore: { sessionGeneration: 0, isAuthenticated: true },
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getMySubscriptions: mocks.getMySubscriptions,
  },
}))

vi.mock('@/api/board', () => ({
  boardApi: {
    unsubscribeBoard: mocks.unsubscribeBoard,
    updateSubscriptionOrder: mocks.updateSubscriptionOrder,
  },
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({ addToast: mocks.addToast }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({ confirm: mocks.confirm }),
}))

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({
    handleSilentError: mocks.handleSilentError,
    handleError: mocks.handleError,
  }),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

const pageResponse = (page: number, totalPages: number, content: SubscriptionBoardListItem[]) =>
  axiosApiPageSuccess(content, {
    number: page,
    size: 100,
    totalElements: content.length,
    totalPages,
    first: page === 0,
    last: page === totalPages - 1,
    empty: content.length === 0,
  })

const subscription = (overrides: Partial<SubscriptionBoardListItem> = {}): SubscriptionBoardListItem => ({
  boardId: 1,
  boardName: 'Free',
  boardUrl: 'free',
  description: 'Free board',
  sortOrder: 1,
  subscriberCount: 3,
  postCount: 10,
  adminDisplayName: null,
  isSubscribed: true,
  isActive: true,
  isPublic: true,
  subscriptionAccessible: true,
  accessState: 'ACCESSIBLE',
  inaccessibleReason: null,
  ...overrides,
  newPostCount: overrides.newPostCount ?? 0,
  hasNewPosts: overrides.hasNewPosts ?? false,
})

function mountManager() {
  let manager: ReturnType<typeof useSubscribedBoardsManager> | null = null
  const wrapper = mount(defineComponent({
    setup() {
      manager = useSubscribedBoardsManager()
      return {}
    },
    template: '<div />',
  }))

  return {
    wrapper,
    manager: manager as unknown as ReturnType<typeof useSubscribedBoardsManager>,
  }
}

describe('useSubscribedBoardsManager', () => {
  const originalMatchMedia = window.matchMedia
  const originalInnerWidth = window.innerWidth

  beforeEach(() => {
    vi.clearAllMocks()
    mocks.authStore.sessionGeneration = 0
    mocks.authStore.isAuthenticated = true
    window.matchMedia = vi.fn(() => ({
      matches: false,
      addEventListener: mocks.mediaQueryAddEventListener,
      removeEventListener: mocks.mediaQueryRemoveEventListener,
    }) as unknown as MediaQueryList)
    mocks.confirm.mockResolvedValue(true)
    mocks.unsubscribeBoard.mockResolvedValue(axiosApiResponse(apiEmptySuccess()))
    mocks.updateSubscriptionOrder.mockResolvedValue(axiosApiResponse(apiEmptySuccess()))
  })

  afterEach(() => {
    window.matchMedia = originalMatchMedia
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalInnerWidth })
  })

  it('partitions accessible and unavailable subscriptions after loading all pages', async () => {
    mocks.getMySubscriptions
      .mockResolvedValueOnce(pageResponse(0, 2, [subscription({ boardUrl: 'general' })]))
      .mockResolvedValueOnce(pageResponse(1, 2, [
        subscription({ boardId: 2, boardUrl: 'hidden', accessState: 'INACCESSIBLE', isActive: false }),
      ]))

    const { manager } = mountManager()
    await flushPromises()
    await flushPromises()

    expect(manager.accessibleBoards.value.map(board => board.boardUrl)).toEqual(['general'])
    expect(manager.unavailableBoards.value.map(board => board.boardUrl)).toEqual(['hidden'])
    expect(manager.hasSubscriptions.value).toBe(true)
  })

  it('invalidates subscription caches after unsubscribe and reloads subscriptions', async () => {
    mocks.getMySubscriptions
      .mockResolvedValueOnce(pageResponse(0, 1, [subscription()]))
      .mockResolvedValueOnce(pageResponse(0, 1, []))

    const { manager } = mountManager()
    await flushPromises()
    await flushPromises()

    await manager.handleUnsubscribe(subscription())
    await flushPromises()

    expect(mocks.unsubscribeBoard).toHaveBeenCalledWith('free', { signal: expect.any(AbortSignal) })
    expect(mocks.addToast).toHaveBeenCalledWith('user.subscriptions.unsubscribeSuccess', 'success')
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
    expect(mocks.getMySubscriptions).toHaveBeenCalledTimes(2)
  })

  it('rolls back reordered subscriptions when order update and reload fail', async () => {
    mocks.getMySubscriptions
      .mockResolvedValueOnce(pageResponse(0, 1, [
        subscription({ boardId: 1, boardUrl: 'free', sortOrder: 1 }),
        subscription({ boardId: 2, boardUrl: 'tech', sortOrder: 2 }),
      ]))
      .mockRejectedValueOnce(new Error('reload failed'))
    mocks.updateSubscriptionOrder.mockRejectedValueOnce(new Error('failed'))

    const { manager } = mountManager()
    await flushPromises()
    await flushPromises()

    manager.accessibleBoards.value = [
      manager.accessibleBoards.value[1],
      manager.accessibleBoards.value[0],
    ]

    await manager.handleDragEnd()

    expect(mocks.updateSubscriptionOrder).toHaveBeenCalledWith(['tech', 'free'], { signal: expect.any(AbortSignal) })
    expect(mocks.handleSilentError).toHaveBeenCalledWith(expect.any(Error), 'Failed to update subscription order')
    expect(mocks.getMySubscriptions).toHaveBeenCalledTimes(2)
    expect(manager.accessibleBoards.value.map(board => board.boardUrl)).toEqual(['free', 'tech'])
    expect(manager.isReordering.value).toBe(false)
  })

  it('ignores additional reorder saves while an order update is pending', async () => {
    const reorderResult = createDeferred<unknown>()
    mocks.getMySubscriptions.mockResolvedValueOnce(pageResponse(0, 1, [
      subscription({ boardId: 1, boardUrl: 'free', sortOrder: 1 }),
      subscription({ boardId: 2, boardUrl: 'tech', sortOrder: 2 }),
    ]))
    mocks.updateSubscriptionOrder.mockReturnValueOnce(reorderResult.promise)

    const { manager } = mountManager()
    await flushPromises()
    await flushPromises()

    manager.accessibleBoards.value = [
      manager.accessibleBoards.value[1],
      manager.accessibleBoards.value[0],
    ]

    const firstReorder = manager.handleDragEnd()

    expect(manager.isReordering.value).toBe(true)
    expect(mocks.updateSubscriptionOrder).toHaveBeenCalledTimes(1)

    await manager.handleDragEnd()

    expect(mocks.updateSubscriptionOrder).toHaveBeenCalledTimes(1)

    reorderResult.resolve(axiosApiResponse(apiEmptySuccess()))
    await firstReorder

    expect(manager.isReordering.value).toBe(false)
  })

  it('does not start unsubscribe while an order update is pending', async () => {
    const reorderResult = createDeferred<unknown>()
    mocks.getMySubscriptions.mockResolvedValueOnce(pageResponse(0, 1, [subscription()]))
    mocks.updateSubscriptionOrder.mockReturnValueOnce(reorderResult.promise)
    const { manager } = mountManager()
    await flushPromises()

    const reorder = manager.handleDragEnd()
    await manager.handleUnsubscribe(subscription())

    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(mocks.unsubscribeBoard).not.toHaveBeenCalled()
    reorderResult.resolve(axiosApiResponse(apiEmptySuccess()))
    await reorder
  })

  it('removes mobile media query listener on unmount', () => {
    mocks.getMySubscriptions.mockResolvedValue(pageResponse(0, 1, []))

    const { wrapper } = mountManager()
    wrapper.unmount()

    expect(mocks.mediaQueryAddEventListener).toHaveBeenCalledWith('change', expect.any(Function))
    expect(mocks.mediaQueryRemoveEventListener).toHaveBeenCalledWith('change', expect.any(Function))
  })

  it('aborts and clears private subscription state at a session boundary', async () => {
    const delayed = createDeferred<ReturnType<typeof pageResponse>>()
    mocks.getMySubscriptions.mockReturnValueOnce(delayed.promise)
    const { manager } = mountManager()
    await Promise.resolve()
    const requestConfig = mocks.getMySubscriptions.mock.calls[0]?.[1] as { signal: AbortSignal }

    mocks.authStore.sessionGeneration = 1
    mocks.authStore.isAuthenticated = false
    notifyAuthSessionBoundary(1)

    expect(requestConfig.signal.aborted).toBe(true)
    expect(manager.accessibleBoards.value).toEqual([])
    expect(manager.unavailableBoards.value).toEqual([])
    expect(manager.loading.value).toBe(false)

    delayed.resolve(pageResponse(0, 1, [subscription()]))
    await flushPromises()
    expect(manager.accessibleBoards.value).toEqual([])
  })

  it('loads subscriptions for the authenticated account after a session boundary', async () => {
    mocks.getMySubscriptions
      .mockResolvedValueOnce(pageResponse(0, 1, [subscription({ boardUrl: 'account-a' })]))
      .mockResolvedValueOnce(pageResponse(0, 1, [subscription({ boardId: 2, boardUrl: 'account-b' })]))
    const { manager } = mountManager()
    await flushPromises()
    expect(manager.accessibleBoards.value.map((board) => board.boardUrl)).toEqual(['account-a'])

    mocks.authStore.sessionGeneration = 1
    mocks.authStore.isAuthenticated = true
    notifyAuthSessionBoundary(1)
    await flushPromises()

    expect(mocks.getMySubscriptions).toHaveBeenCalledTimes(2)
    expect(manager.accessibleBoards.value.map((board) => board.boardUrl)).toEqual(['account-b'])
  })

  it('exposes a retryable load error instead of presenting a failed load as empty', async () => {
    mocks.getMySubscriptions.mockRejectedValueOnce(new Error('load failed'))
    const { manager } = mountManager()
    await flushPromises()

    expect(manager.loadError.value).toBe(true)
    expect(manager.hasSubscriptions.value).toBe(false)

    mocks.getMySubscriptions.mockResolvedValueOnce(pageResponse(0, 1, [subscription()]))
    await manager.fetchSubscriptions()
    expect(manager.loadError.value).toBe(false)
    expect(manager.accessibleBoards.value).toHaveLength(1)
  })

  it('falls back to window width when matchMedia is unavailable', () => {
    mocks.getMySubscriptions.mockResolvedValue(pageResponse(0, 1, []))
    window.matchMedia = undefined as unknown as typeof window.matchMedia
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 500 })

    const { manager } = mountManager()

    expect(manager.isMobile.value).toBe(true)
    expect(mocks.mediaQueryAddEventListener).not.toHaveBeenCalled()
  })
})
