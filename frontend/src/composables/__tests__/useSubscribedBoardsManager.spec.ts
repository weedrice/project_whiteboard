import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { useSubscribedBoardsManager } from '../useSubscribedBoardsManager'
import type { SubscriptionBoardListItem } from '@/types'

const mocks = vi.hoisted(() => ({
  getMySubscriptions: vi.fn(),
  unsubscribeBoard: vi.fn(),
  updateSubscriptionOrder: vi.fn(),
  confirm: vi.fn(),
  addToast: vi.fn(),
  handleSilentError: vi.fn(),
  handleError: vi.fn(),
  invalidateQueries: vi.fn(),
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

const pageResponse = (page: number, totalPages: number, content: SubscriptionBoardListItem[]) => ({
  data: {
    success: true,
    data: {
      content,
      number: page,
      size: 100,
      totalElements: content.length,
      totalPages,
      first: page === 0,
      last: page === totalPages - 1,
      empty: content.length === 0,
    },
  },
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
    manager: manager as ReturnType<typeof useSubscribedBoardsManager>,
  }
}

describe('useSubscribedBoardsManager', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
    mocks.unsubscribeBoard.mockResolvedValue({ data: { success: true } })
    mocks.updateSubscriptionOrder.mockResolvedValue({ data: { success: true } })
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

    expect(mocks.unsubscribeBoard).toHaveBeenCalledWith('free')
    expect(mocks.addToast).toHaveBeenCalledWith('user.subscriptions.unsubscribeSuccess', 'success')
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['boards'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['boards', 'subscriptions'] })
    expect(mocks.getMySubscriptions).toHaveBeenCalledTimes(2)
  })

  it('removes resize listener on unmount', () => {
    mocks.getMySubscriptions.mockResolvedValue(pageResponse(0, 1, []))
    const addListener = vi.spyOn(window, 'addEventListener')
    const removeListener = vi.spyOn(window, 'removeEventListener')

    const { wrapper } = mountManager()
    wrapper.unmount()

    expect(addListener).toHaveBeenCalledWith('resize', expect.any(Function))
    expect(removeListener).toHaveBeenCalledWith('resize', expect.any(Function))

    addListener.mockRestore()
    removeListener.mockRestore()
  })
})
