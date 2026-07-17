import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import OnboardingPage from '../OnboardingPage.vue'
import { boardApi } from '@/api/board'
import { apiDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { BaseButtonStub, flushAll, identityT } from '@/test/vue-test-helpers'
import type { BoardListItem } from '@/types'
import { sessionQueryKey } from '@/queryAuthScope'

const replace = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({ query: { redirect: '/mypage' } }))
const completeOnboarding = vi.hoisted(() => vi.fn())
const subscribeBoard = vi.hoisted(() => vi.fn())
const getBoardRecommendations = vi.hoisted(() => vi.fn())
const enablePush = vi.hoisted(() => vi.fn())
const authStore = vi.hoisted(() => ({ sessionGeneration: 0 }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authStore,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace }),
  useRoute: () => route,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: identityT }),
}))

vi.mock('@/api/board', () => ({
  boardApi: {
    getBoardRecommendations,
    subscribeBoard,
  },
}))

vi.mock('@/composables/useUser', () => ({
  useUser: () => ({
    useCompleteOnboarding: () => ({
      mutateAsync: completeOnboarding,
      isPending: false,
    }),
  }),
}))

vi.mock('@/features/notifications/usePushNotifications', () => ({
  usePushNotifications: () => ({
    supported: { value: true },
    enabled: { value: true },
    isEnabling: false,
    enablePush,
  }),
}))

const board = (overrides: Partial<BoardListItem> = {}): BoardListItem => ({
  boardId: 1,
  boardName: 'General',
  boardUrl: 'general',
  description: 'General board',
  sortOrder: 1,
  subscriberCount: 10,
  postCount: 20,
  isSubscribed: false,
  isActive: true,
  isPublic: true,
  subscriptionAccessible: true,
  ...overrides,
})

const mountPage = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')

  const wrapper = mount(OnboardingPage, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: {
        BaseButton: BaseButtonStub,
        BaseSpinner: true,
      },
    },
  })

  return { invalidateQueries, queryClient, wrapper }
}

describe('OnboardingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authStore.sessionGeneration = 0
    getBoardRecommendations.mockResolvedValue(apiDataResponse<typeof boardApi.getBoardRecommendations>([
      board(),
      board({ boardId: 2, boardName: 'Dev', boardUrl: 'dev', isSubscribed: true }),
    ]))
    subscribeBoard.mockResolvedValue(apiSuccessResponse<typeof boardApi.subscribeBoard>())
    completeOnboarding.mockResolvedValue(undefined)
    enablePush.mockResolvedValue(undefined)
  })

  it('renders recommended boards and subscribes to a board', async () => {
    const { invalidateQueries, queryClient, wrapper } = mountPage()
    await flushAll()

    expect(wrapper.text()).toContain('General')
    await wrapper.findAll('button')[0].trigger('click')
    await flushAll()

    expect(subscribeBoard).toHaveBeenCalledWith('general')
    expect(queryClient.getQueryData<BoardListItem[]>(sessionQueryKey(0, ['onboarding', 'board-recommendations']))?.[0]?.isSubscribed).toBe(true)
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: sessionQueryKey(0, ['board', 'detail', 'general']) })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: sessionQueryKey(0, ['boards']) })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: sessionQueryKey(0, ['boards', 'subscriptions']) })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: sessionQueryKey(0, ['home', 'landing']) })
  })

  it('keeps the recommendation state unchanged when subscription fails', async () => {
    subscribeBoard.mockRejectedValueOnce(new Error('network'))
    const { invalidateQueries, queryClient, wrapper } = mountPage()
    await flushAll()

    await wrapper.findAll('button')[0].trigger('click')
    await flushAll()

    expect(queryClient.getQueryData<BoardListItem[]>(sessionQueryKey(0, ['onboarding', 'board-recommendations']))?.[0]?.isSubscribed).toBe(false)
    expect(invalidateQueries).not.toHaveBeenCalled()
  })

  it('ignores a subscription response from a previous session generation', async () => {
    let resolveSubscription!: (value: Awaited<ReturnType<typeof boardApi.subscribeBoard>>) => void
    subscribeBoard.mockImplementationOnce(() => new Promise((resolve) => {
      resolveSubscription = resolve
    }))
    const { invalidateQueries, queryClient, wrapper } = mountPage()
    await flushAll()

    await wrapper.findAll('button')[0].trigger('click')
    await vi.waitFor(() => expect(subscribeBoard).toHaveBeenCalledWith('general'))
    authStore.sessionGeneration += 1
    resolveSubscription(apiSuccessResponse<typeof boardApi.subscribeBoard>())
    await flushAll()

    expect(queryClient.getQueryData<BoardListItem[]>(sessionQueryKey(0, ['onboarding', 'board-recommendations']))?.[0]?.isSubscribed).toBe(false)
    expect(queryClient.getQueryData(sessionQueryKey(1, ['onboarding', 'board-recommendations']))).toBeUndefined()
    expect(invalidateQueries).not.toHaveBeenCalled()
  })

  it('completes onboarding and returns to the redirect target', async () => {
    const { wrapper } = mountPage()
    await flushAll()

    await wrapper.findAll('button')[4].trigger('click')
    await flushAll()

    expect(completeOnboarding).toHaveBeenCalled()
    expect(replace).toHaveBeenCalledWith('/mypage')
  })

  it('does not navigate when onboarding completion belongs to an older session', async () => {
    let resolveCompletion!: () => void
    let requestSignal: AbortSignal | undefined
    completeOnboarding.mockImplementationOnce((signal?: AbortSignal) => {
      requestSignal = signal
      return new Promise<void>((resolve) => { resolveCompletion = resolve })
    })
    const { wrapper } = mountPage()
    await flushAll()

    await wrapper.findAll('button')[4].trigger('click')
    await vi.waitFor(() => expect(completeOnboarding).toHaveBeenCalledOnce())
    authStore.sessionGeneration += 1
    resolveCompletion()
    await flushAll()

    expect(requestSignal).toBeInstanceOf(AbortSignal)
    expect(replace).not.toHaveBeenCalled()
  })

  it('can enable push without completing onboarding', async () => {
    const { wrapper } = mountPage()
    await flushAll()

    await wrapper.findAll('button')[2].trigger('click')
    await flushAll()

    expect(enablePush).toHaveBeenCalled()
    expect(wrapper.text()).toContain('onboarding.pushEnabled')
  })

  it('renders a retryable error when recommendations fail', async () => {
    getBoardRecommendations.mockRejectedValueOnce(new Error('network'))

    const { wrapper } = mountPage()
    await flushAll()

    expect(wrapper.text()).toContain('데이터를 불러오는데 실패했습니다.')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('General')
  })
})
