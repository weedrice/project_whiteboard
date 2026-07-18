import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useBoardSubscriptionAction } from '../useBoardSubscriptionAction'
import type { BoardDetail } from '@/types'

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: mocks.confirm,
  }),
}))

const boardDetail = (overrides: Partial<BoardDetail> = {}): BoardDetail => ({
  boardId: 1,
  boardName: 'Free Board',
  boardUrl: 'free',
  sortOrder: 1,
  subscriberCount: 12,
  postCount: 3,
  isSubscribed: false,
  isActive: true,
  isPublic: true,
  subscriptionAccessible: true,
  allowNsfw: false,
  isAdmin: false,
  categories: [],
  latestPosts: [],
  agentUseYn: false,
  ...overrides,
})

describe('useBoardSubscriptionAction', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
  })

  it('subscribes without confirmation when the board is not subscribed', async () => {
    const subscribeMutate = vi.fn()
    const { handleSubscribe } = useBoardSubscriptionAction({
      board: ref(boardDetail({ isSubscribed: false })),
      sessionGeneration: ref(0),
      isSubscribePending: ref(false),
      subscribeMutate,
    })

    await handleSubscribe()

    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(subscribeMutate).toHaveBeenCalledWith({
      boardUrl: 'free',
      isSubscribed: false,
    })
  })

  it('confirms before unsubscribing', async () => {
    const subscribeMutate = vi.fn()
    const { handleSubscribe } = useBoardSubscriptionAction({
      board: ref(boardDetail({ isSubscribed: true })),
      sessionGeneration: ref(0),
      isSubscribePending: ref(false),
      subscribeMutate,
    })

    await handleSubscribe()

    expect(mocks.confirm).toHaveBeenCalledWith('user.subscriptions.unsubscribeConfirm')
    expect(subscribeMutate).toHaveBeenCalledWith({
      boardUrl: 'free',
      isSubscribed: true,
    })
  })

  it('skips unsubscribe when confirmation is cancelled', async () => {
    const subscribeMutate = vi.fn()
    mocks.confirm.mockResolvedValueOnce(false)
    const { handleSubscribe } = useBoardSubscriptionAction({
      board: ref(boardDetail({ isSubscribed: true })),
      sessionGeneration: ref(0),
      isSubscribePending: ref(false),
      subscribeMutate,
    })

    await handleSubscribe()

    expect(subscribeMutate).not.toHaveBeenCalled()
  })

  it('skips action while board data is unavailable or a subscription mutation is pending', async () => {
    const subscribeMutate = vi.fn()
    const board = ref<BoardDetail | null>(null)
    const isSubscribePending = ref(false)
    const { handleSubscribe } = useBoardSubscriptionAction({
      board,
      sessionGeneration: ref(0),
      isSubscribePending,
      subscribeMutate,
    })

    await handleSubscribe()

    board.value = boardDetail()
    isSubscribePending.value = true
    await handleSubscribe()

    expect(mocks.confirm).not.toHaveBeenCalled()
    expect(subscribeMutate).not.toHaveBeenCalled()
  })

  it('drops an unsubscribe intent when the board changes during confirmation', async () => {
    let resolveConfirm!: (value: boolean) => void
    mocks.confirm.mockReturnValueOnce(new Promise<boolean>((resolve) => { resolveConfirm = resolve }))
    const subscribeMutate = vi.fn()
    const board = ref(boardDetail({ isSubscribed: true }))
    const { handleSubscribe } = useBoardSubscriptionAction({
      board,
      sessionGeneration: ref(0),
      isSubscribePending: ref(false),
      subscribeMutate,
    })

    const pending = handleSubscribe()
    board.value = boardDetail({ boardUrl: 'other', isSubscribed: true })
    resolveConfirm(true)
    await pending

    expect(subscribeMutate).not.toHaveBeenCalled()
  })

  it('drops an unsubscribe intent when the session changes during confirmation', async () => {
    let resolveConfirm!: (value: boolean) => void
    mocks.confirm.mockReturnValueOnce(new Promise<boolean>((resolve) => { resolveConfirm = resolve }))
    const subscribeMutate = vi.fn()
    const sessionGeneration = ref(0)
    const { handleSubscribe } = useBoardSubscriptionAction({
      board: ref(boardDetail({ isSubscribed: true })),
      sessionGeneration,
      isSubscribePending: ref(false),
      subscribeMutate,
    })

    const pending = handleSubscribe()
    sessionGeneration.value = 1
    resolveConfirm(true)
    await pending

    expect(subscribeMutate).not.toHaveBeenCalled()
  })
})
