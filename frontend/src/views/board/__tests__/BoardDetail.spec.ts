import { defineComponent, h, ref } from 'vue'
import { mount, RouterLinkStub } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BoardDetail from '../BoardDetail.vue'

const {
  route,
  router,
  addRecentBoard,
  confirmMock,
  subscribeMutate,
  boardPayload,
  postsPayload,
  noticesPayload,
  boardState,
  useBoardPostsCalls
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
  confirmMock: vi.fn(),
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
    content: [] as Array<Record<string, unknown>>,
    totalElements: 0,
    totalPages: 0
  },
  noticesPayload: [] as Array<Record<string, unknown>>,
  boardState: {
    value: null as null | Record<string, unknown>
  },
  useBoardPostsCalls: [] as unknown[][]
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

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: confirmMock
  })
}))

vi.mock('@/composables/useBoard', () => ({
  useBoard: () => ({
    useBoardDetail: () => ({
      data: ref(boardState.value),
      isLoading: ref(false),
      error: ref(null)
    }),
    useBoardPosts: (...args: unknown[]) => {
      useBoardPostsCalls.push(args)
      return {
        data: ref(postsPayload),
        isLoading: ref(false),
        isFetching: ref(false),
        error: ref(null)
      }
    },
    useBoardNotices: () => ({
      data: ref(noticesPayload),
      isLoading: ref(false),
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
    delete (route.params as Record<string, string | undefined>).postId
    route.query = {}
    route.name = 'board-detail'
    route.path = '/board/free'
    postsPayload.content = []
    postsPayload.totalElements = 0
    postsPayload.totalPages = 0
    noticesPayload.length = 0
    boardState.value = boardPayload
    useBoardPostsCalls.length = 0
    router.replace.mockReset()
    router.push.mockReset()
    addRecentBoard.mockReset()
    confirmMock.mockReset()
    confirmMock.mockResolvedValue(true)
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

  it('enables posts query from route boardUrl before board detail data resolves', () => {
    boardState.value = null

    mount(BoardDetail, {
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

    const enabled = useBoardPostsCalls[0]?.[3] as { value: boolean } | undefined
    expect(enabled?.value).toBe(true)
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
    const searchWriteLink = wrapper.find('.nv-board-search-write-btn')

    await searchInput.setValue('vue')
    await searchSelect.setValue('TITLE')
    await searchButton?.trigger('click')

    expect(searchSelect.classes()).toContain('nv-board-search-select')
    expect(searchButton?.classes()).toContain('nv-board-search-btn')
    expect(searchWriteLink.exists()).toBe(true)
    expect(searchWriteLink.classes()).toContain('nv-board-write-btn')
    expect(searchWriteLink.text()).toBe('common.write')
    expect(router.replace).toHaveBeenLastCalledWith({
      path: '/board/free',
      query: {
        q: 'vue',
        type: 'TITLE'
      }
    })
  })

  it('keeps the all filter active while search results are shown without category or concept filters', () => {
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

    expect(allButton?.attributes('aria-pressed')).toBe('true')
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

  it('keeps the active search query when the all chip is pressed', async () => {
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
    await allButton?.trigger('click')

    expect(router.replace).toHaveBeenLastCalledWith({
      path: '/board/free',
      query: {
        q: 'vue',
        type: 'TITLE'
      }
    })
  })

  it('does not keep a category chip active when search and category query params are mixed', () => {
    route.query = {
      q: 'vue',
      type: 'TITLE',
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
    const categoryButton = wrapper.findAll('button').find((button) => button.text() === 'QnA')

    expect(allButton?.attributes('aria-pressed')).toBe('true')
    expect(categoryButton?.attributes('aria-pressed')).toBe('false')
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        q: 'vue',
        type: 'TITLE'
      }
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

  it('suppresses the current post highlight after create navigation', () => {
    route.name = 'post-detail'
    ;(route.params as Record<string, string>).postId = '123'
    route.query = {
      fromCreate: '1',
      page: '2'
    }
    postsPayload.content = [
      {
        postId: 123,
        boardUrl: 'free',
        title: 'Created post',
        createdAt: '2026-01-04T00:00:00',
        viewCount: 0,
        likeCount: 0,
        commentCount: 0,
        isNotice: false,
        isNsfw: false,
        isSpoiler: false,
        author: { userId: 1, displayName: 'Author' }
      }
    ]

    const PostListStub = defineComponent({
      name: 'PostListStub',
      props: {
        currentPostId: {
          type: String,
          default: undefined
        },
        linkQuery: {
          type: Object,
          default: undefined
        }
      },
      setup(props) {
        return () => h('div', {
          'data-testid': 'post-list-proxy',
          'data-current-post-id': props.currentPostId ?? '',
          'data-link-query': JSON.stringify(props.linkQuery ?? {})
        })
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

    const postList = wrapper.get('[data-testid="post-list-proxy"]')
    expect(postList.attributes('data-current-post-id')).toBe('')
    expect(postList.attributes('data-link-query')).toBe('{"page":"2"}')
  })

  it('shows the latest three notices first and expands to all notices', async () => {
    noticesPayload.push(
      { postId: 1, boardUrl: 'free', title: 'Old notice', createdAt: '2026-01-01T00:00:00', isNotice: true },
      { postId: 4, boardUrl: 'free', title: 'Newest notice', createdAt: '2026-01-04T00:00:00', isNotice: true },
      { postId: 3, boardUrl: 'free', title: 'Middle notice', createdAt: '2026-01-03T00:00:00', isNotice: true },
      { postId: 2, boardUrl: 'free', title: 'Second notice', createdAt: '2026-01-02T00:00:00', isNotice: true }
    )

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

    expect(wrapper.text()).toContain('board.detail.notices.title')
    expect(wrapper.text()).toContain('Newest notice')
    expect(wrapper.text()).toContain('Middle notice')
    expect(wrapper.text()).toContain('Second notice')
    expect(wrapper.text()).not.toContain('Old notice')

    const moreButton = wrapper.find('.nv-board-notice-more')
    expect(moreButton.exists()).toBe(true)
    expect(moreButton.attributes('aria-expanded')).toBe('false')

    await moreButton.trigger('click')

    expect(wrapper.text()).toContain('Old notice')
    expect(moreButton.attributes('aria-expanded')).toBe('true')
  })

  it('does not unsubscribe when the app confirm is cancelled', async () => {
    boardState.value = {
      ...boardPayload,
      isSubscribed: true
    }
    confirmMock.mockResolvedValue(false)

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

    const subscribeButton = wrapper.findAll('button').find((button) => button.text() === 'common.unsubscribe')
    await subscribeButton?.trigger('click')
    await Promise.resolve()

    expect(confirmMock).toHaveBeenCalledWith('user.subscriptions.unsubscribeConfirm')
    expect(subscribeMutate).not.toHaveBeenCalled()
  })

  it('unsubscribes after the app confirm is accepted', async () => {
    boardState.value = {
      ...boardPayload,
      isSubscribed: true
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

    const subscribeButton = wrapper.findAll('button').find((button) => button.text() === 'common.unsubscribe')
    await subscribeButton?.trigger('click')
    await Promise.resolve()

    expect(subscribeMutate).toHaveBeenCalledWith({
      boardUrl: 'free',
      isSubscribed: true
    })
  })
})
