import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import OnboardingPage from '../OnboardingPage.vue'
import { boardApi } from '@/api/board'
import { apiDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { BaseButtonStub, flushAll, identityT } from '@/test/vue-test-helpers'
import type { BoardListItem } from '@/types'

const replace = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({ query: { redirect: '/mypage' } }))
const completeOnboarding = vi.hoisted(() => vi.fn())
const subscribeBoard = vi.hoisted(() => vi.fn())
const getBoardRecommendations = vi.hoisted(() => vi.fn())
const enablePush = vi.hoisted(() => vi.fn())

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

  return mount(OnboardingPage, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: {
        BaseButton: BaseButtonStub,
        BaseSpinner: true,
      },
    },
  })
}

describe('OnboardingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getBoardRecommendations.mockResolvedValue(apiDataResponse<typeof boardApi.getBoardRecommendations>([
      board(),
      board({ boardId: 2, boardName: 'Dev', boardUrl: 'dev', isSubscribed: true }),
    ]))
    subscribeBoard.mockResolvedValue(apiSuccessResponse<typeof boardApi.subscribeBoard>())
    completeOnboarding.mockResolvedValue(undefined)
    enablePush.mockResolvedValue(undefined)
  })

  it('renders recommended boards and subscribes to a board', async () => {
    const wrapper = mountPage()
    await flushAll()

    expect(wrapper.text()).toContain('General')
    await wrapper.findAll('button')[0].trigger('click')
    await flushAll()

    expect(subscribeBoard).toHaveBeenCalledWith('general')
  })

  it('completes onboarding and returns to the redirect target', async () => {
    const wrapper = mountPage()
    await flushAll()

    await wrapper.findAll('button')[4].trigger('click')
    await flushAll()

    expect(completeOnboarding).toHaveBeenCalled()
    expect(replace).toHaveBeenCalledWith('/mypage')
  })

  it('can enable push without completing onboarding', async () => {
    const wrapper = mountPage()
    await flushAll()

    await wrapper.findAll('button')[2].trigger('click')
    await flushAll()

    expect(enablePush).toHaveBeenCalled()
    expect(wrapper.text()).toContain('onboarding.pushEnabled')
  })

  it('renders a retryable error when recommendations fail', async () => {
    getBoardRecommendations.mockRejectedValueOnce(new Error('network'))

    const wrapper = mountPage()
    await flushAll()

    expect(wrapper.text()).toContain('데이터를 불러오는데 실패했습니다.')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('General')
  })
})
