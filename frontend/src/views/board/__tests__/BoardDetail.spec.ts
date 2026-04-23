import { defineComponent, h, ref } from 'vue'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BoardDetail from '../BoardDetail.vue'

const {
  route,
  router,
  addRecentBoard,
  subscribeMutate,
  boardPayload,
  postsPayload
} = vi.hoisted(() => ({
  route: {
    params: {
      boardUrl: 'free'
    },
    query: {} as Record<string, string>,
    name: 'board-detail',
    path: '/board/free'
  },
  router: {
    replace: vi.fn(),
    push: vi.fn()
  },
  addRecentBoard: vi.fn(),
  subscribeMutate: vi.fn(),
  boardPayload: {
    boardId: 1,
    boardName: 'Free Board',
    boardUrl: 'free',
    description: 'Open discussion board',
    iconUrl: '',
    sortOrder: 1,
    subscriberCount: 12,
    adminDisplayName: 'Admin',
    adminUserId: 44,
    isSubscribed: false,
    isActive: true,
    isPublic: true,
    subscriptionAccessible: true,
    allowNsfw: false,
    isAdmin: true,
    categories: [
      { categoryId: 1, name: '\uC77C\uBC18', sortOrder: 1, isActive: true, minWriteRole: 'USER' },
      { categoryId: 2, name: 'QnA', sortOrder: 2, isActive: true, minWriteRole: 'USER' }
    ],
    latestPosts: [],
    agentUseYn: false
  },
  postsPayload: {
    content: [],
    totalElements: 0,
    totalPages: 0
  }
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => route,
    useRouter: () => router
  }
})

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key
    })
  }
})

vi.mock('@unhead/vue', () => ({
  useHead: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isAuthenticated: true,
    user: {
      role: 'USER'
    }
  })
}))

vi.mock('@/composables/useRecentBoards', () => ({
  useRecentBoards: () => ({
    addRecentBoard
  })
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoardDetail: () => ({
      data: ref(boardPayload),
      isLoading: ref(false),
      error: ref(null)
    }),
    useBoardPosts: () => ({
      data: ref(postsPayload),
      isLoading: ref(false),
      isFetching: ref(false),
      error: ref(null)
    }),
    useSubscribeBoard: () => ({
      mutate: subscribeMutate,
      isPending: ref(false)
    })
  })
}))

vi.mock('@/utils/image', () => ({
  getOptimizedBoardIconUrl: (url: string) => url,
  handleImageError: vi.fn()
}))

vi.mock('@/utils/keyboard', () => ({
  isInputFocused: () => false
}))

vi.mock('@/utils/errorHandler', () => ({
  isRestrictedResourceError: () => false
}))

describe('BoardDetail', () => {
  beforeEach(() => {
    route.params.boardUrl = 'free'
    route.query = {}
    route.path = '/board/free'
    postsPayload.content = []
    postsPayload.totalElements = 0
    postsPayload.totalPages = 0
    router.replace.mockReset()
    router.push.mockReset()
    addRecentBoard.mockReset()
    subscribeMutate.mockReset()
  })

  it('shows all, concept, and real category filters in the toolbar', () => {
    const wrapper = mount(BoardDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          RouterView: true,
          PostList: true,
          Pagination: true,
          UserMenu: true,
          BaseSkeleton: true
        }
      }
    })

    expect(wrapper.text()).toContain('QnA')
    expect(wrapper.text()).toContain('board.detail.filter.all')
    expect(wrapper.text()).toContain('board.detail.filter.concept')
    expect(wrapper.text()).not.toContain('Post Index')
    expect(wrapper.text()).not.toContain('\uC77C\uBC18')
    expect(wrapper.html()).toContain('nv-board-header-panel')
    expect(wrapper.html()).toContain('nv-board-toolbar-sticky')

    const allButton = wrapper.findAll('button').find((button) => button.text() === 'board.detail.filter.all')
    expect(allButton?.attributes('aria-pressed')).toBe('true')
  })

  it('syncs category selection into the board URL state', async () => {
    const wrapper = mount(BoardDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          RouterView: true,
          PostList: true,
          Pagination: true,
          UserMenu: true,
          BaseSkeleton: true
        }
      }
    })

    const categoryButton = wrapper.findAll('button').find((button) => button.text() === 'QnA')

    await categoryButton?.trigger('click')

    expect(router.replace).toHaveBeenLastCalledWith({
      path: '/board/free',
      query: {
        categoryId: '2'
      }
    })
    expect(categoryButton?.attributes('aria-pressed')).toBe('true')
  })

  it('syncs search query into the board URL state', async () => {
    const wrapper = mount(BoardDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          RouterView: true,
          PostList: true,
          Pagination: true,
          UserMenu: true,
          BaseSkeleton: true
        }
      }
    })

    const searchInput = wrapper.find('#board-search-input')
    const searchSelect = wrapper.find('select[aria-label="Search scope"]')
    const searchButton = wrapper.findAll('button').find((button) => button.text() === 'search.doSearch')

    await searchInput.setValue('vue')
    await searchSelect.setValue('TITLE')
    await searchButton?.trigger('click')

    expect(router.replace).toHaveBeenLastCalledWith({
      path: '/board/free',
      query: {
        q: 'vue',
        type: 'TITLE'
      }
    })
  })

  it('does not mark the all filter as active while search results are shown', () => {
    route.query = {
      q: 'vue',
      type: 'TITLE'
    }

    const wrapper = mount(BoardDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          RouterView: true,
          PostList: true,
          Pagination: true,
          UserMenu: true,
          BaseSkeleton: true
        }
      }
    })

    const allButton = wrapper.findAll('button').find((button) => button.text() === 'board.detail.filter.all')

    expect(allButton?.attributes('aria-pressed')).toBe('false')
  })

  it('toggles the concept filter through route-synced board state', async () => {
    const wrapper = mount(BoardDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          RouterView: true,
          PostList: true,
          Pagination: true,
          UserMenu: true,
          BaseSkeleton: true
        }
      }
    })

    const conceptButton = wrapper.findAll('button').find((button) => button.text() === 'board.detail.filter.concept')
    await conceptButton?.trigger('click')

    expect(conceptButton?.attributes('aria-pressed')).toBe('true')
    expect(router.replace).toHaveBeenLastCalledWith({
      path: '/board/free',
      query: {
        concept: '1'
      }
    })
  })

  it('resets to the default all filter when the all chip is pressed', async () => {
    route.query = {
      categoryId: '2'
    }

    const wrapper = mount(BoardDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          RouterView: true,
          PostList: true,
          Pagination: true,
          UserMenu: true,
          BaseSkeleton: true
        }
      }
    })

    const allButton = wrapper.findAll('button').find((button) => button.text() === 'board.detail.filter.all')
    await allButton?.trigger('click')

    expect(allButton?.attributes('aria-pressed')).toBe('true')
    expect(router.replace).toHaveBeenLastCalledWith({
      path: '/board/free',
      query: {}
    })
  })

  it('updates the current sort when the list emits a new sort value', async () => {
    const PostListStub = defineComponent({
      name: 'PostListStub',
      props: {
        currentSort: {
          type: String,
          default: ''
        }
      },
      emits: ['update:sort'],
      setup(props, { emit }) {
        return () => h(
          'button',
          {
            'data-testid': 'sort-proxy',
            onClick: () => emit('update:sort', 'likeCount,desc')
          },
          props.currentSort
        )
      }
    })

    const wrapper = mount(BoardDetail, {
      global: {
        mocks: {
          $t: (key: string) => key
        },
        stubs: {
          RouterLink: RouterLinkStub,
          RouterView: true,
          PostList: PostListStub,
          Pagination: true,
          UserMenu: true,
          BaseSkeleton: true
        }
      }
    })

    expect(wrapper.get('[data-testid="sort-proxy"]').text()).toBe('createdAt,desc')

    await wrapper.get('[data-testid="sort-proxy"]').trigger('click')

    expect(wrapper.get('[data-testid="sort-proxy"]').text()).toBe('likeCount,desc')
  })
})
