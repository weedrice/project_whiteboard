import { flushPromises, mount, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import { computed, defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({
  name: 'search' as string,
  query: {} as Record<string, unknown>,
  routerPush: vi.fn(),
  routerReplace: vi.fn(),
  invalidateQueries: vi.fn(),
  cancelQueries: vi.fn(),
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
  isFetching: false,
  integratedError: null as Error | null,
  semanticError: null as Error | null,
  popularKeywordsLoading: false,
  popularKeywordsError: false,
  recentKeywordsLoading: false,
  recentKeywordsError: false,
  recentKeywords: [] as Array<{ logId: number, keyword: string }>,
  refetchIntegrated: vi.fn(),
  refetchSemantic: vi.fn(),
  refetchPopularKeywords: vi.fn(),
  refetchRecentKeywords: vi.fn(),
}))

const authState = vi.hoisted(() => ({
  isAuthenticated: true,
  sessionGeneration: 0,
  user: { userId: 7 } as { userId: number } | null,
}))

const searchApiMocks = vi.hoisted(() => ({
  deleteRecentSearch: vi.fn(),
  deleteAllRecentSearches: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: routeState.routerPush, replace: routeState.routerReplace }),
}))

vi.mock('@tanstack/vue-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/vue-query')>()
  return {
    ...actual,
    useQueryClient: () => ({
      invalidateQueries: routeState.invalidateQueries,
      cancelQueries: routeState.cancelQueries,
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

vi.mock('@/features/search/queries/useSearch', () => ({
  useSearch: () => ({
    useIntegratedSearch: (params: ReturnType<typeof computed<Record<string, unknown>>>) => {
      searchState.lastParams = params
      return {
        data: ref(searchState.searchData),
        isLoading: ref(searchState.isLoading),
        isFetching: ref(searchState.isFetching),
        error: ref(searchState.integratedError),
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
        error: ref(searchState.semanticError),
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
      data: ref({ content: searchState.recentKeywords.map(item => ({ ...item, searchedAt: '2026-11-10T00:00:00Z' })) }),
      isLoading: ref(searchState.recentKeywordsLoading),
      isError: ref(searchState.recentKeywordsError),
      refetch: searchState.refetchRecentKeywords,
    }),
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
      currentSort: { type: String, default: undefined },
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
    localStorage.clear()
    authState.user = { userId: 7 }
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
    searchState.isFetching = false
    searchState.integratedError = null
    searchState.semanticError = null
    searchState.popularKeywordsLoading = false
    searchState.popularKeywordsError = false
    searchState.recentKeywordsLoading = false
    searchState.recentKeywordsError = false
    searchState.recentKeywords = []
    authState.isAuthenticated = true
    authState.sessionGeneration = 0
    searchState.refetchIntegrated.mockClear()
    searchState.refetchSemantic.mockClear()
    searchState.refetchPopularKeywords.mockClear()
    searchState.refetchRecentKeywords.mockClear()
    routeState.routerPush.mockClear()
    routeState.routerReplace.mockClear()
    routeState.invalidateQueries.mockClear()
    routeState.cancelQueries.mockReset().mockResolvedValue(undefined)
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
      postPage: 0,
      commentPage: 0,
      size: 20,
      searchType: 'TITLE_CONTENT',
    })
    expect(wrapper.text()).toContain('"vue"')
    expect(wrapper.find('[data-testid="search-page-empty"]').attributes('data-description')).toBe('search.noResultsFor:vue search.noResultsSuggestion')
  })

  it('shows a search prompt instead of no results before a query is provided', () => {
    const wrapper = mountPage()

    expect(wrapper.get('h1').text()).toBe('search.results')
    expect(wrapper.get('label[for="search-page-query"]').text()).toBe('search.placeholder')
    expect(wrapper.get('#search-page-query').attributes()).toMatchObject({
      name: 'searchPageQuery',
      autocomplete: 'off',
    })
    expect(wrapper.find('[data-testid="search-page-empty"]').text()).toBe('search.placeholder')
    expect(wrapper.find('[data-testid="search-page-empty"]').attributes('data-description')).toBeUndefined()
  })

  it('connects post sorting to the request and resets pagination without losing filters', async () => {
    routeState.query = { q: 'vue', postPage: '2', commentPage: '1', author: 'hong', sort: 'likeCount,desc' }
    searchState.searchData.postResults = [{ postId: 1, title: 'Post' }]
    const wrapper = mountPage()
    const posts = wrapper.getComponent({ name: 'PostListStub' })

    expect(searchState.lastParams?.value.sort).toBe('likeCount,desc')
    expect(posts.props('currentSort')).toBe('likeCount,desc')
    posts.vm.$emit('update:sort', 'viewCount,desc')
    await flushPromises()

    expect(routeState.routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: { q: 'vue', commentPage: '1', author: 'hong', sort: 'viewCount,desc' },
    })
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
    expect(wrapper.get('nav').attributes('aria-label')).toBe('common.post common.pagination')
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
    expect(wrapper.find('[data-testid="search-page-empty"]').exists()).toBe(false)
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
    searchState.integratedError = new Error('network failed')
    const wrapper = mountPage()

    expect(wrapper.get('[role="alert"]').text()).toContain('common.messages.loadFailed')
    expect(wrapper.find('[data-testid="search-page-empty"]').exists()).toBe(false)

    await wrapper.get('[role="alert"] button').trigger('click')
    expect(searchState.refetchIntegrated).toHaveBeenCalledOnce()
    expect(searchState.refetchSemantic).not.toHaveBeenCalled()
  })

  it('keeps integrated results visible and retries semantic search independently', async () => {
    routeState.query = { q: 'partial' }
    searchState.searchData = {
      postResults: [{ postId: 1, title: 'Keyword result' }],
      commentResults: [],
      userResults: [],
      boardResults: [],
    }
    searchState.semanticError = new Error('semantic unavailable')
    const wrapper = mountPage()

    expect(wrapper.get('[data-testid="post-list"]').text()).toBe('1')
    const alert = wrapper.get('[data-testid="search-results"] [role="alert"]')
    expect(alert.text()).toContain('common.messages.loadFailed')

    await alert.get('button').trigger('click')
    expect(searchState.refetchSemantic).toHaveBeenCalledOnce()
    expect(searchState.refetchIntegrated).not.toHaveBeenCalled()
  })

  it('replaces an out-of-range result page with the last valid page', async () => {
    routeState.query = { q: 'vue', postPage: '99', commentPage: '1' }
    searchState.searchData = {
      postResults: [],
      commentResults: [],
      userResults: [],
      boardResults: [],
      postPage: { totalElements: 21, totalPages: 3, page: 99, size: 10, hasMore: false },
      commentPage: { totalElements: 20, totalPages: 2, page: 1, size: 10, hasMore: false },
    }

    mountPage()
    await flushPromises()

    expect(routeState.routerReplace).toHaveBeenCalledWith({
      name: 'search',
      query: { q: 'vue', postPage: '2', commentPage: '1' },
    })
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

    expect(wrapper.find('#search-author-filter').exists()).toBe(false)
  })

  it('shows the default period and scope before searching and submits edited filters together', async () => {
    const wrapper = mountPage()
    expect(wrapper.get<HTMLSelectElement>('select[name="period"]').element.value).toBe('')
    expect(wrapper.get<HTMLSelectElement>('select[name="searchType"]').element.value).toBe('TITLE_CONTENT')
    expect(wrapper.text()).not.toContain('search.filterToggle')
    await wrapper.get('select[name="period"]').setValue('WEEK')
    await wrapper.get('select[name="searchType"]').setValue('TITLE')
    await wrapper.get('#search-page-query').setValue('vue')
    await wrapper.get('form[role="search"]').trigger('submit')
    expect(routeState.routerPush).toHaveBeenCalledWith({
      name: 'search', query: { q: 'vue', searchType: 'TITLE', period: 'WEEK' },
    })
  })

  it('opens the native calendar on click and preserves manual entry when the picker is unavailable', async () => {
    const wrapper = mountPage()
    await wrapper.get('select[name="period"]').setValue('CUSTOM')
    const dateInput = wrapper.get('#search-from-filter')
    const showPicker = vi.fn()
    Object.defineProperty(dateInput.element, 'showPicker', { configurable: true, value: showPicker })
    await dateInput.trigger('click')
    expect(showPicker).toHaveBeenCalledOnce()
    showPicker.mockImplementation(() => { throw new DOMException('Unavailable', 'NotAllowedError') })
    await dateInput.trigger('click')
    await dateInput.setValue('2026-11-01')
    expect((dateInput.element as HTMLInputElement).value).toBe('2026-11-01')
    wrapper.unmount()
  })

  it('does not navigate for an invalid custom date range', async () => {
    routeState.query = { q: 'vue' }
    const wrapper = mountPage()
    await wrapper.get('select[name="period"]').setValue('CUSTOM')
    await wrapper.get('#search-from-filter').setValue('2026-11-10')
    await wrapper.get('#search-to-filter').setValue('2026-11-01')
    expect(wrapper.get('form[role="search"] button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.get('form[role="search"]').trigger('submit')
    expect(routeState.routerPush).not.toHaveBeenCalled()
    expect(routeState.invalidateQueries).not.toHaveBeenCalled()
  })

  it('renders independent page links with each result group', () => {
    routeState.query = { q: 'vue', postPage: '1', commentPage: '2' }
    searchState.searchData = {
      postResults: [{ postId: 1, title: 'Post' }],
      commentResults: [{ commentId: 2, postId: 1, boardUrl: 'general', content: 'Comment' }],
      userResults: [], boardResults: [],
      postPage: { totalElements: 60, totalPages: 3, page: 1, size: 20, hasMore: true },
      commentPage: { totalElements: 80, totalPages: 4, page: 2, size: 20, hasMore: true },
    }
    const wrapper = mountPage()
    const postLinks = wrapper.get('[data-testid="post-results"] nav').findAllComponents(RouterLinkStub)
    const commentLinks = wrapper.get('[data-testid="comment-results"] nav').findAllComponents(RouterLinkStub)
    expect(postLinks.find((link: VueWrapper) => link.text() === '3')?.props('to')).toEqual({
      name: 'search', query: { q: 'vue', postPage: '2', commentPage: '2' },
    })
    expect(commentLinks.find((link: VueWrapper) => link.text() === '2')?.props('to')).toEqual({
      name: 'search', query: { q: 'vue', postPage: '1', commentPage: '1' },
    })
  })

  it('keeps keyword loading and failures distinct and retryable in their new locations', async () => {
    searchState.popularKeywordsLoading = true
    searchState.recentKeywordsError = true
    const wrapper = mountPage()
    const popular = wrapper.get('[data-testid="search-results"] [data-testid="popular-keywords"]')
    const recent = wrapper.get('[data-testid="search-query-panel"] [data-testid="recent-keywords"]')

    expect(wrapper.find('aside').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('common.tags')
    expect(popular.attributes('aria-busy')).toBe('true')
    expect(popular.findComponent({ name: 'BaseSpinner' }).exists()).toBe(true)
    expect(popular.text()).not.toContain('search.noResults')

    const recentAlert = recent.get('[role="alert"]')
    expect(recentAlert.text()).toContain('common.messages.loadFailed')
    await recentAlert.get('button').trigger('click')
    expect(searchState.refetchRecentKeywords).toHaveBeenCalledOnce()
    wrapper.unmount()

    searchState.popularKeywordsLoading = false
    searchState.popularKeywordsError = true
    const failedWrapper = mountPage()
    await failedWrapper.get('[data-testid="popular-keywords"] [role="alert"] button').trigger('click')
    expect(searchState.refetchPopularKeywords).toHaveBeenCalledOnce()
  })

  it('hides empty recent keywords without an empty-results message', () => {
    const wrapper = mountPage()
    expect(wrapper.find('[data-testid="recent-keywords"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('keeps recent keywords visible when refreshing the server history fails', () => {
    searchState.recentKeywords = [{ logId: 8, keyword: 'cached keyword' }]
    searchState.recentKeywordsError = true
    const wrapper = mountPage()
    const recent = wrapper.get('[data-testid="recent-keywords"]')
    expect(recent.text()).toContain('cached keyword')
    expect(recent.get('[role="alert"]').text()).toContain('common.messages.loadFailed')
    wrapper.unmount()
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

    expect(routeState.cancelQueries).toHaveBeenCalledWith({
      queryKey: ['session', 3, 'search', 'recent'],
    })
    expect(routeState.cancelQueries.mock.invocationCallOrder[0]).toBeLessThan(routeState.invalidateQueries.mock.invocationCallOrder[0])
    expect(routeState.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 3, 'search', 'recent'],
    })
  })

  it('skips deletion cache work if the session changes while cancelling a history read', async () => {
    searchState.recentKeywords = [{ logId: 8, keyword: 'old account' }]
    let finishCancellation!: () => void
    routeState.cancelQueries.mockReturnValueOnce(new Promise<void>(resolve => { finishCancellation = resolve }))
    const wrapper = mountPage()
    await wrapper.get('button[aria-label="search.deleteRecent"]').trigger('click')
    await flushPromises()
    expect(routeState.cancelQueries).toHaveBeenCalledOnce()
    authState.sessionGeneration += 1
    notifyAuthSessionBoundary(authState.sessionGeneration)
    finishCancellation()
    await flushPromises()
    expect(routeState.invalidateQueries).not.toHaveBeenCalled()
    wrapper.unmount()
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
