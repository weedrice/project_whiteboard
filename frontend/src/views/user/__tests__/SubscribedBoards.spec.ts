import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SubscribedBoards from '../SubscribedBoards.vue'

const mocks = vi.hoisted(() => ({
  getMySubscriptions: vi.fn(),
  unsubscribeBoard: vi.fn(),
  updateSubscriptionOrder: vi.fn(),
  confirm: vi.fn(),
  addToast: vi.fn(),
  handleSilentError: vi.fn(),
  handleError: vi.fn(),
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

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('vuedraggable', () => ({
  default: {
    name: 'Draggable',
    props: ['modelValue'],
    emits: ['update:modelValue', 'end'],
    template: '<ul><slot v-for="element in modelValue" name="item" :element="element" /></ul>',
  },
}))

const pageResponse = (page: number, totalPages: number, content: unknown[]) => ({
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

const mountView = () => mount(SubscribedBoards, {
  global: {
    mocks: {
      $t: (key: string) => key,
      $router: { push: vi.fn() },
    },
    stubs: {
      BaseButton: {
        emits: ['click'],
        template: '<button @click="$emit(\'click\')"><slot /></button>',
      },
      BaseSkeleton: true,
      EmptyState: true,
      Users: true,
      Menu: true,
    },
  },
})

describe('SubscribedBoards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the first subscription page before fetching remaining pages in parallel order', async () => {
    mocks.getMySubscriptions
      .mockResolvedValueOnce(pageResponse(0, 3, [
        { boardId: 1, boardUrl: 'general', boardName: 'General', accessState: 'ACCESSIBLE', isActive: true },
      ]))
      .mockResolvedValueOnce(pageResponse(1, 3, [
        { boardId: 2, boardUrl: 'news', boardName: 'News', accessState: 'ACCESSIBLE', isActive: true },
      ]))
      .mockResolvedValueOnce(pageResponse(2, 3, [
        { boardId: 3, boardUrl: 'hidden', boardName: 'Hidden', accessState: 'DELETED', isActive: false },
      ]))

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.getMySubscriptions).toHaveBeenNthCalledWith(1, {
      page: 0,
      size: 100,
      includeUnavailable: true,
    })
    expect(mocks.getMySubscriptions).toHaveBeenNthCalledWith(2, {
      page: 1,
      size: 100,
      includeUnavailable: true,
    })
    expect(mocks.getMySubscriptions).toHaveBeenNthCalledWith(3, {
      page: 2,
      size: 100,
      includeUnavailable: true,
    })
    expect(wrapper.text()).toContain('General')
    expect(wrapper.text()).toContain('News')
    expect(wrapper.text()).toContain('Hidden')
  })
})
