import { mount, RouterLinkStub } from '@vue/test-utils'
import { computed, defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({
  name: 'search' as string,
  query: {} as Record<string, unknown>,
  routerPush: vi.fn(),
  invalidateQueries: vi.fn(),
}))

const searchState = vi.hoisted(() => ({
  lastParams: null as ReturnType<typeof computed<Record<string, unknown>>> | null,
  searchData: {
    postResults: [] as Array<Record<string, unknown>>,
    boardResults: [] as Array<Record<string, unknown>>,
  },
  isLoading: false,
  error: null as Error | null,
  refetchIntegrated: vi.fn(),
  refetchSemantic: vi.fn(),
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
  useAuthStore: () => ({
    isAuthenticated: false,
  }),
}))

vi.mock('@/api/search', () => ({
  searchApi: {
    deleteRecentSearch: vi.fn(),
    deleteAllRecentSearches: vi.fn(),
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
    useSemanticSearch: () => ({
      data: ref({ content: [] }),
      isLoading: ref(false),
      error: ref(searchState.error),
      refetch: searchState.refetchSemantic,
    }),
    usePopularKeywords: () => ({
      data: ref([]),
    }),
    useRecentSearches: () => ({
      data: ref({ content: [] }),
    }),
  }),
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: () => ({
    data: ref({ tags: [] }),
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
    searchState.searchData = {
      postResults: [],
      boardResults: [],
    }
    searchState.isLoading = false
    searchState.error = null
    searchState.refetchIntegrated.mockClear()
    searchState.refetchSemantic.mockClear()
    routeState.routerPush.mockClear()
    routeState.invalidateQueries.mockClear()
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
    expect(routeState.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['search', 'integrated'] })
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

  it('announces the total number of completed search results', () => {
    routeState.query = { q: 'vue' }
    searchState.searchData = {
      postResults: [{ postId: 1, title: 'Post' }],
      boardResults: [{ boardId: 1, boardName: 'Board' }],
    }

    const wrapper = mountPage()
    const status = wrapper.get('[role="status"]')

    expect(status.attributes()).toMatchObject({
      'aria-live': 'polite',
      'aria-atomic': 'true',
    })
    expect(status.text()).toBe('search.resultSummary:2')
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
})
