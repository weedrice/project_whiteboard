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
  postsPayload,
  noticesPayload
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
    boardName: '자유게시판',
    boardUrl: 'free',
    description: '자유롭게 이야기하는 공간입니다.',
    iconUrl: '',
    sortOrder: 1,
    subscriberCount: 12,
    adminDisplayName: '관리자',
    adminUserId: 44,
    isSubscribed: false,
    isActive: true,
    isPublic: true,
    subscriptionAccessible: true,
    allowNsfw: false,
    isAdmin: true,
    categories: [
      { categoryId: 1, name: '일반', sortOrder: 1, isActive: true, minWriteRole: 'USER' },
      { categoryId: 2, name: '질문', sortOrder: 2, isActive: true, minWriteRole: 'USER' }
    ],
    latestPosts: [],
    agentUseYn: false
  },
  postsPayload: {
    content: [],
    totalElements: 0,
    totalPages: 0
  },
  noticesPayload: []
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
      isLoading: ref(false)
    }),
    useBoardNotices: () => ({
      data: ref(noticesPayload)
    }),
    useSubscribeBoard: () => ({
      mutate: subscribeMutate
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

  it('shows only actual category chips and removes concept filter', () => {
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

    expect(wrapper.text()).toContain('질문')
    expect(wrapper.text()).not.toContain('일반')
    expect(wrapper.text()).not.toContain('board.detail.filter.concept')
    expect(wrapper.html()).toContain('nv-board-header-panel')
    expect(wrapper.html()).toContain('nv-board-toolbar-sticky')
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

    const categoryButton = wrapper.findAll('button').find((button) => button.text() === '질문')

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
    const searchSelect = wrapper.find('select[aria-label="검색 범위"]')
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

  it('preserves the server-provided order for non-default sorts', () => {
    route.query = { sort: 'likeCount,desc' }
    postsPayload.content = [
      {
        postId: 10,
        rowNum: 2,
        title: 'First from server',
        likeCount: 1,
        viewCount: 1,
        commentCount: 0,
        createdAt: '2026-04-22T10:00:00',
        author: { displayName: 'A' }
      } as never,
      {
        postId: 11,
        rowNum: 1,
        title: 'Second from server',
        likeCount: 99,
        viewCount: 1,
        commentCount: 0,
        createdAt: '2026-04-22T09:00:00',
        author: { displayName: 'B' }
      } as never
    ]
    postsPayload.totalElements = 2
    postsPayload.totalPages = 1

    const PostListStub = defineComponent({
      name: 'PostListStub',
      props: {
        posts: { type: Array, default: () => [] }
      },
      setup(props) {
        return () => h('div', { 'data-testid': 'post-order' }, (props.posts as Array<{ postId: number }>).map((post) => post.postId).join(','))
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

    expect(wrapper.get('[data-testid=\"post-order\"]').text()).toBe('10,11')
  })
})
