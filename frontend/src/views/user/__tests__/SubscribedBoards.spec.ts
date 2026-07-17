import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SubscribedBoards from '../SubscribedBoards.vue'
import { axiosApiPageSuccess } from '@/test/apiResponseFixtures'
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

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ sessionGeneration: 0 }),
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

vi.mock('vuedraggable', () => ({
  default: {
    name: 'Draggable',
    props: ['modelValue'],
    emits: ['update:modelValue', 'end'],
    template: '<ul><slot v-for="(element, index) in modelValue" name="item" :element="element" :index="index" /></ul>',
  },
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

const subscription: SubscriptionBoardListItem = {
  boardId: 1,
  boardName: 'Free',
  boardUrl: 'free',
  description: 'Free board',
  sortOrder: 1,
  subscriberCount: 3,
  postCount: 10,
  newPostCount: 0,
  hasNewPosts: false,
  adminDisplayName: null,
  isSubscribed: true,
  isActive: true,
  isPublic: true,
  subscriptionAccessible: true,
  accessState: 'ACCESSIBLE',
  inaccessibleReason: null,
}

const mountView = () => mount(SubscribedBoards, {
  global: {
    mocks: {
      $t: (key: string) => key,
      $router: { push: vi.fn() },
    },
    stubs: {
      BaseButton: {
        emits: ['click'],
        template: '<button @click="$emit(\'click\', $event)"><slot /></button>',
      },
      BaseSkeleton: true,
      EmptyState: true,
      RouterLink: RouterLinkStub,
      Users: true,
      Menu: true,
    },
  },
})

describe('SubscribedBoards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockResolvedValue(true)
    mocks.unsubscribeBoard.mockResolvedValue({ data: { success: true } })
    mocks.updateSubscriptionOrder.mockResolvedValue({ data: { success: true } })
  })

  it('loads the first subscription page before fetching remaining pages in parallel order', async () => {
    mocks.getMySubscriptions
      .mockResolvedValueOnce(pageResponse(0, 3, [
        { ...subscription, boardId: 1, boardUrl: 'general', boardName: 'General' },
      ]))
      .mockResolvedValueOnce(pageResponse(1, 3, [
        { ...subscription, boardId: 2, boardUrl: 'news', boardName: 'News' },
      ]))
      .mockResolvedValueOnce(pageResponse(2, 3, [
        {
          ...subscription,
          boardId: 3,
          boardUrl: 'hidden',
          boardName: 'Hidden',
          accessState: 'INACCESSIBLE',
          inaccessibleReason: 'INACTIVE',
          isActive: false,
          subscriptionAccessible: false,
        },
      ]))

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.getMySubscriptions).toHaveBeenNthCalledWith(1, {
      page: 0,
      size: 100,
      includeUnavailable: true,
    }, {
      signal: expect.any(AbortSignal),
    })
    expect(mocks.getMySubscriptions).toHaveBeenNthCalledWith(2, {
      page: 1,
      size: 100,
      includeUnavailable: true,
    }, {
      signal: expect.any(AbortSignal),
    })
    expect(mocks.getMySubscriptions).toHaveBeenNthCalledWith(3, {
      page: 2,
      size: 100,
      includeUnavailable: true,
    }, {
      signal: expect.any(AbortSignal),
    })
    expect(wrapper.text()).toContain('General')
    expect(wrapper.text()).toContain('News')
    expect(wrapper.text()).toContain('Hidden')
  })

  it('invalidates board subscription caches after unsubscribe', async () => {
    mocks.getMySubscriptions
      .mockResolvedValueOnce(pageResponse(0, 1, [subscription]))
      .mockResolvedValueOnce(pageResponse(0, 1, []))

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    const unsubscribeButton = wrapper.findAll('button')
      .find(button => button.text() === 'user.subscriptions.unsubscribe')
    await unsubscribeButton?.trigger('click')
    await flushPromises()

    expect(mocks.unsubscribeBoard).toHaveBeenCalledWith('free', {
      signal: expect.any(AbortSignal),
    })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
  })

  it('invalidates board subscription caches after reorder', async () => {
    mocks.getMySubscriptions.mockResolvedValue(pageResponse(0, 1, [subscription]))

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    wrapper.getComponent({ name: 'Draggable' }).vm.$emit('end')
    await flushPromises()

    expect(mocks.updateSubscriptionOrder).toHaveBeenCalledWith(['free'], {
      signal: expect.any(AbortSignal),
    })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards'] })
    expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'boards', 'subscriptions'] })
  })

  it('supports moving subscriptions with accessible order buttons', async () => {
    mocks.getMySubscriptions.mockResolvedValue(pageResponse(0, 1, [
      { ...subscription, boardId: 1, boardUrl: 'general', boardName: 'General', sortOrder: 1 },
      { ...subscription, boardId: 2, boardUrl: 'news', boardName: 'News', sortOrder: 2 },
    ]))

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    const moveDownButton = wrapper.findAll('button')
      .find(button => button.attributes('aria-label') === 'user.subscriptions.moveDown' && !button.attributes('disabled'))
    await moveDownButton?.trigger('click')
    await flushPromises()

    expect(mocks.updateSubscriptionOrder).toHaveBeenCalledWith(['news', 'general'], {
      signal: expect.any(AbortSignal),
    })
  })

  it('renders accessible subscriptions as real board links', async () => {
    mocks.getMySubscriptions.mockResolvedValue(pageResponse(0, 1, [subscription]))

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    const link = wrapper.getComponent(RouterLinkStub)

    expect(link.props('to')).toBe('/board/free')
    expect(link.attributes('aria-label')).toBe('Free')
  })
})
