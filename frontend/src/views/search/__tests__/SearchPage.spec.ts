import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import { computed, defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({
  name: 'search' as string,
  query: {} as Record<string, unknown>,
  routerPush: vi.fn(),
  invalidateQueries: vi.fn(),
}))

interface ResultPageState {
  totalElements: number
  totalPages: number
  page: number
  size: number
  hasMore: boolean
}

const searchState = vi.hoisted(() => ({
  lastParams: null as ReturnType<typeof computed<Record<string, unknown>>> | null,
  lastSemanticParams: null as ReturnType<typeof computed<Record<string, unknown>>> | null,
  semanticEnabled: null as ReturnType<typeof computed<boolean>> | null,
  searchData: {
    postResults: [] as Array<Record<string, unknown>>,
    commentResults: [] as Array<Record<string, unknown>>,
    userResults: [] as Array<Record<string, unknown>>,
    boardResults: [] as Array<Record<string, unknown>>,
  } as {
    postResults: Array<Record<string, unknown>>
    commentResults: Array<Record<string, unknown>>
    userResults: Array<Record<string, unknown>>
    boardResults: Array<Record<string, unknown>>
    postPage?: ResultPageState
    commentPage?: ResultPageState
    userPage?: ResultPageState
    boardPage?: ResultPageState
  },
  isLoading: false,
  error: null as Error | null,
  popularKeywordsLoading: false,
  popularKeywordsError: false,
  popularTagsLoading: false,
  popularTagsError: false,
  recentKeywordsLoading: false,
  recentKeywordsError: false,
  recentKeywords: [] as Array<{ logId: number, keyword: string }>,
  refetchIntegrated: vi.fn(),
  refetchSemantic: vi.fn(),
  refetchPopularKeywords: vi.fn(),
  refetchPopularTags: vi.fn(),
  refetchRecentKeywords: vi.fn(),
}))

const authState = vi.hoisted(() => ({
  isAuthenticated: true,
  sessionGeneration: 0,
}))

const searchApiMocks = vi.hoisted(() => ({
  deleteRecentSearch: vi.fn(),
  deleteAllRecentSearches: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: routeState.routerPush }),
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/vue-query')>()
  return {
    ...actual,
    useQueryClient: () => ({
      invalidateQueries: routeState.invalidateQueries,
    }),
  }
})

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string, params?: Record<string, string | number>) => {
        if (params?.count !== undefined) return `${key}:${params.count}`
        if (params?.query) return `${key}:${params.query}`
        if (params?.value) return `${key}:${params.value}`
        if (params?.from || params?.to) return `${key}:${params.from ?? ''}:${params.to ?? ''}`
        return key
      },
    }),
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/api/search', () => ({
  searchApi: {
    deleteRecentSearch: searchApiMocks.deleteRecentSearch,
    deleteAllRecentSearches: searchApiMocks.deleteAllRecentSearches,
  },
}))

vi.mock('@/api/tag', () => ({
  tagApi: {
    getPopularTags: vi.fn(),
  },
}))

vi.mock('@/composables/useSearch', () => ({
  useSearch: () => ({
    useIntegratedSearch: (params: ReturnType<typeof computed<Record<string, unknown>>>) => {
      searchState.lastParams = params
      return {
        data: ref(searchState.searchData),
        isLoading: ref(searchState.isLoading),
        error: ref(searchState.error),
        refetch: searchState.refetchIntegrated,
      }
    },
    useSemanticSearch: (
      params: ReturnType<typeof computed<Record<string, unknown>>>,
      enabled: ReturnType<typeof computed<boolean>>,
    ) => {
      searchState.lastSemanticParams = params
      searchState.semanticEnabled = enabled
      return {
        data: ref({ content: [] }),
        isLoading: ref(false),
        error: ref(searchState.error),
        refetch: searchState.refetchSemantic,
      }
    },
    usePopularKeywords: () => ({
      data: ref([]),
      isLoading: ref(searchState.popularKeywordsLoading),
      isError: ref(searchState.popularKeywordsError),
      refetch: searchState.refetchPopularKeywords,
    }),
    useRecentSearches: () => ({
      data: ref({ content: searchState.recentKeywords }),
      isLoading: ref(searchState.recentKeywordsLoading),
      isError: ref(searchState.recentKeywordsError),
      refetch: searchState.refetchRecentKeywords,
    }),
  }),
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: () => ({
    data: ref({ tags: [] }),
    isLoading: ref(searchState.popularTagsLoading),
    isError: ref(searchState.popularTagsError),
    refetch: searchState.refetchPopularTags,
  }),
}))

vi.mock('@/utils/image', () => ({
  getOptimizedBoardIconUrl: (value: string) => value,
  handleImageError: vi.fn(),
}))

vi.mock('@/components/board/PostList.vue', () => ({
  default: defineComponent({
    name: 'PostListStub',
    props: {
      posts: { type: Array, default: () => [] },
    },
    setup(props) {
      return () => h('div', { 'data-testid': 'post-list' }, String(props.posts.length))
    },
  }),
}))

const { default: SearchPage } = await import('../SearchPage.vue')
const { notifyAuthSessionBoundary } = await import('@/queryAuthScope')

const EmptyStateStub = defineComponent({
  name: 'EmptyStateStub',
  props: {
    title: { type: String, default: '' },
    description: { type: String, default: undefined },
  },
  setup(props) {
    return () => h('div', {
      'data-testid': 'empty-state',
      'data-description': props.description,
    }, props.title)
  },
})

describe('SearchPage', () => {
  beforeEach(() => {
    routeState.query = {}
    searchState.lastParams = null
    searchState.lastSemanticParams = null
    searchState.semanticEnabled = null
    searchState.searchData = {
      postResults: [],
      commentResults: [],
      userResults: [],
      boardResults: [],
    }
    searchState.isLoading = false
    searchState.error = null
    searchState.popularKeywordsLoading = false
    searchState.popularKeywordsError = false
    searchState.popularTagsLoading = false
    searchState.popularTagsError = false
    searchState.recentKeywordsLoading = false
    searchState.recentKeywordsError = false
    searchState.recentKeywords = []
    authState.isAuthenticated = true
    authState.sessionGeneration = 0
    searchState.refetchIntegrated.mockClear()
    searchState.refetchSemantic.mockClear()
    searchState.refetchPopularKeywords.mockClear()
    searchState.refetchPopularTags.mockClear()
    searchState.refetchRecentKeywords.mockClear()
    routeState.routerPush.mockClear()
    routeState.invalidateQueries.mockClear()
    searchApiMocks.deleteRecentSearch.mockReset()
    searchApiMocks.deleteAllRecentSearches.mockReset()
    searchApiMocks.deleteRecentSearch.mockResolvedValue(undefined)
    searchApiMocks.deleteAllRecentSearches.mockResolvedValue(undefined)
  })

  const mountPage = () => mount(SearchPage, {
    global: {
      mocks: {
        $t: (key: string, params?: Record<string, string>) => params?.query ? `${key}:${params.query}` : key,
      },
      stubs: {
        BaseSpinner: true,
        EmptyState: EmptyStateStub,
        Layout: true,
        RouterLink: RouterLinkStub,
        Search: true,
        MessageSquare: true,
        User: true,
        UserAvatar: true,
      },
    },
  })

  it('uses q query as the primary integrated search text', () => {
    routeState.query = {
      q: ' vue ',
      keyword: 'ignored',
      tag: 'ignored-tag',
    }

    const wrapper = mountPage()

    expect(searchState.lastParams?.value).toEqual({
      q: 'vue',
      page: 0,
      size: 20,
      searchType: 'TITLE_CONTENT',
    })
    expect(wrapper.text()).toContain('"vue"')
    expect(wrapper.find('[data-testid="empty-state"]').attributes('data-description')).toBe('search.noResultsFor:vue search.noResultsSuggestion')
  })

  it('shows a search prompt instead of no results before a query is provided', () => {
    const wrapper = mountPage()

    expect(wrapper.get('h1').text()).toBe('search.results')
    expect(wrapper.get('label[for="search-page-query"]').text()).toBe('search.placeholder')
    expect(wrapper.get('#search-page-query').attributes()).toMatchObject({
      name: 'searchPageQuery',
      autocomplete: 'off',
    })
    expect(wrapper.find('[data-testid="empty-state"]').text()).toBe('search.placeholder')
    expect(wrapper.find('[data-testid="empty-state"]').attributes('data-description')).toBeUndefined()
  })

  it('submits page-local searches through the search route', async () => {
    const wrapper = mountPage()

    await wrapper.get('#search-page-query').setValue('  local query  ')
    await wrapper.get('form[role="search"]').trigger('submit.prevent')

    expect(routeState.routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: {
        q: 'local query',
      },
    })
  })

  it('invalidates integrated search instead of changing the URL for the same submitted query', async () => {
    routeState.query = { q: 'same' }
    const wrapper = mountPage()

    await wrapper.get('#search-page-query').setValue('same')
    await wrapper.get('form[role="search"]').trigger('submit.prevent')

    expect(routeState.routerPush).not.toHaveBeenCalled()
    expect(routeState.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 0, 'search', 'integrated'],
    })
  })

  it('falls back to keyword and tag query names used by search entry components', () => {
    routeState.query = { q: '   ', keyword: 'pinia' }
    mountPage()
    expect(searchState.lastParams?.value.q).toBe('pinia')

    routeState.query = { tag: 'notice' }
    mountPage()
    expect(searchState.lastParams?.value.q).toBe('notice')
  })

  it('uses the first value when the route query is an array', () => {
    routeState.query = {
      keyword: ['first', 'second'],
    }

    mountPage()

    expect(searchState.lastParams?.value.q).toBe('first')
  })

  it('announces total matches instead of only the current page item count', () => {
    routeState.query = { q: 'vue' }
    searchState.searchData = {
      postResults: [{ postId: 1, title: 'Post' }],
      commentResults: [],
      userResults: [],
      boardResults: [{ boardId: 1, boardName: 'Board' }],
      postPage: { totalElements: 12, totalPages: 3, page: 0, size: 5, hasMore: true },
      commentPage: { totalElements: 0, totalPages: 0, page: 0, size: 5, hasMore: false },
      userPage: { totalElements: 0, totalPages: 0, page: 0, size: 5, hasMore: false },
      boardPage: { totalElements: 3, totalPages: 1, page: 0, size: 5, hasMore: false },
    }

    const wrapper = mountPage()
    const status = wrapper.get('[role="status"]')

    expect(status.attributes()).toMatchObject({
      'aria-live': 'polite',
      'aria-atomic': 'true',
    })
    expect(status.text()).toBe('search.resultSummary:15')
    expect(wrapper.get('nav').attributes('aria-label')).toBe('common.pagination')
  })

  it('renders comment and user results and includes them in empty and total decisions', () => {
    routeState.query = { q: 'community' }
    searchState.searchData = {
      postResults: [],
      boardResults: [],
      commentResults: [{
        commentId: 7,
        content: 'matching comment',
        postId: 3,
        boardUrl: 'notice',
        postTitle: 'Notice title',
        author: { displayName: 'Commenter' },
      }],
      userResults: [{ userId: 9, displayName: 'Matched User' }],
    }

    const wrapper = mountPage()

    expect(wrapper.get('[data-testid="comment-results"]').text()).toContain('matching comment')
    expect(wrapper.get('[data-testid="user-results"]').text()).toContain('Matched User')
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)
    expect(wrapper.get('[role="status"]').text()).toBe('search.resultSummary:2')
  })

  it('passes boardUrl to semantic search and disables it for unsupported filters', () => {
    routeState.query = { q: 'vue', boardUrl: 'notice' }
    mountPage()

    expect(searchState.lastParams?.value).toMatchObject({ boardUrl: 'notice' })
    expect(searchState.lastSemanticParams?.value).toEqual({
      q: 'vue',
      size: 5,
      contentType: 'ALL',
      boardUrl: 'notice',
    })
    expect(searchState.semanticEnabled?.value).toBe(true)

    routeState.query = { q: 'vue', boardUrl: 'notice', period: 'WEEK' }
    mountPage()
    expect(searchState.semanticEnabled?.value).toBe(false)
  })

  it('shows a retryable error instead of an empty result when search fails', async () => {
    routeState.query = { q: 'failed' }
    searchState.error = new Error('network failed')
    const wrapper = mountPage()

    expect(wrapper.get('[role="alert"]').text()).toContain('common.messages.loadFailed')
    expect(wrapper.find('[data-testid="empty-state"]').exists()).toBe(false)

    await wrapper.get('[role="alert"] button').trigger('click')
    expect(searchState.refetchIntegrated).toHaveBeenCalledOnce()
    expect(searchState.refetchSemantic).toHaveBeenCalledOnce()
  })

  it('renders active filter chips and removes filters through the route query', async () => {
    routeState.query = {
      q: 'vue',
      author: 'hong',
      period: 'CUSTOM',
      from: '2026-07-01',
      to: '2026-07-08',
    }

    const wrapper = mountPage()

    expect(wrapper.text()).toContain('search.authorFilterChip:hong')
    expect(wrapper.text()).toContain('search.periodFilterChip')
    expect(wrapper.text()).toContain('search.dateRangeFilterChip:2026-07-01:2026-07-08')

    const authorChip = wrapper.findAll('button').find((button) => button.text().includes('search.authorFilterChip'))
    await authorChip?.trigger('click')

    expect(routeState.routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: {
        q: 'vue',
        period: 'CUSTOM',
        from: '2026-07-01',
        to: '2026-07-08',
      },
    })
  })

  it('shows all post scopes and applies the selected scope to the route', async () => {
    routeState.query = { q: 'vue' }
    const wrapper = mountPage()

    const filterToggle = wrapper.findAll('button').find((button) => button.text() === 'search.filterToggle')
    await filterToggle?.trigger('click')

    const scope = wrapper.get('select[name="searchType"]')
    expect(scope.findAll('option').map((option) => option.attributes('value'))).toEqual([
      'TITLE_CONTENT', 'TITLE', 'CONTENT', 'AUTHOR',
    ])
    await scope.setValue('CONTENT')
    await scope.element.closest('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))

    expect(routeState.routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: { q: 'vue', searchType: 'CONTENT' },
    })
  })

  it('uses AUTHOR as the keyword scope without sending the separate author filter', async () => {
    routeState.query = { q: 'hong', searchType: 'AUTHOR', author: 'duplicate' }
    const wrapper = mountPage()

    expect(searchState.lastParams?.value).toMatchObject({
      q: 'hong',
      searchType: 'AUTHOR',
    })
    expect(searchState.lastParams?.value.author).toBeUndefined()
    expect(wrapper.text()).toContain('search.scopeFilterChip')

    const filterToggle = wrapper.findAll('button').find((button) => button.text() === 'search.filterToggle')
    await filterToggle?.trigger('click')
    expect(wrapper.find('#search-author-filter').exists()).toBe(false)
  })

  it('keeps sidebar loading, failure, and empty states distinct and retryable', async () => {
    searchState.popularKeywordsLoading = true
    searchState.popularTagsError = true
    searchState.recentKeywordsError = true
    const wrapper = mountPage()
    const sections = wrapper.findAll('aside section')
      .filter((section) => section.attributes('aria-busy') !== undefined)

    expect(sections).toHaveLength(3)
    expect(sections[0].attributes('aria-busy')).toBe('true')
    expect(sections[0].findComponent({ name: 'BaseSpinner' }).exists()).toBe(true)
    expect(sections[0].text()).not.toContain('search.noResults')

    const tagAlert = sections[1].get('[role="alert"]')
    expect(tagAlert.text()).toContain('common.messages.loadFailed')
    expect(sections[1].text()).not.toContain('search.noResults')
    await tagAlert.get('button').trigger('click')
    expect(searchState.refetchPopularTags).toHaveBeenCalledOnce()

    const recentAlert = sections[2].get('[role="alert"]')
    await recentAlert.get('button').trigger('click')
    expect(searchState.refetchRecentKeywords).toHaveBeenCalledOnce()
  })

  it('aborts a recent-search deletion and skips stale cache work after a session boundary', async () => {
    searchState.recentKeywords = [{ logId: 7, keyword: 'old account' }]
    let resolveDelete!: () => void
    searchApiMocks.deleteRecentSearch.mockReturnValue(new Promise<void>((resolve) => {
      resolveDelete = resolve
    }))
    const wrapper = mountPage()

    await wrapper.get('button[aria-label="search.deleteRecent"]').trigger('click')
    const requestConfig = searchApiMocks.deleteRecentSearch.mock.calls[0][1]

    authState.sessionGeneration = 1
    notifyAuthSessionBoundary(1)
    resolveDelete()
    await flushPromises()

    expect(requestConfig.signal.aborted).toBe(true)
    expect(routeState.invalidateQueries).not.toHaveBeenCalled()
  })

  it('invalidates only the captured current-session recent-search key', async () => {
    searchState.recentKeywords = [{ logId: 8, keyword: 'current account' }]
    authState.sessionGeneration = 3
    const wrapper = mountPage()

    await wrapper.get('button[aria-label="search.deleteRecent"]').trigger('click')
    await flushPromises()

    expect(routeState.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 3, 'search', 'recent'],
    })
  })

  it('consumes a recent-search deletion rejection at the click boundary', async () => {
    searchState.recentKeywords = [{ logId: 8, keyword: 'failed account' }]
    searchApiMocks.deleteRecentSearch.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mountPage()

    await wrapper.get('button[aria-label="search.deleteRecent"]').trigger('click')
    await flushPromises()

    expect(searchApiMocks.deleteRecentSearch).toHaveBeenCalledOnce()
    expect(routeState.invalidateQueries).not.toHaveBeenCalled()
  })

  it('does not cancel an earlier destructive request in the same session', async () => {
    searchState.recentKeywords = [
      { logId: 9, keyword: 'first' },
      { logId: 10, keyword: 'second' },
    ]
    const resolvers: Array<() => void> = []
    searchApiMocks.deleteRecentSearch.mockImplementation(() => new Promise<void>((resolve) => {
      resolvers.push(resolve)
    }))
    const wrapper = mountPage()
    const buttons = wrapper.findAll('button[aria-label="search.deleteRecent"]')

    await buttons[0].trigger('click')
    await buttons[1].trigger('click')

    const firstSignal = searchApiMocks.deleteRecentSearch.mock.calls[0][1].signal
    const secondSignal = searchApiMocks.deleteRecentSearch.mock.calls[1][1].signal
    expect(firstSignal.aborted).toBe(false)
    expect(secondSignal.aborted).toBe(false)

    resolvers[0]()
    resolvers[1]()
    await flushPromises()

    expect(routeState.invalidateQueries).toHaveBeenCalledTimes(2)
  })
})
