import { mount, RouterLinkStub } from '@vue/test-utils'
import { computed, defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({
  query: {} as Record<string, unknown>,
}))

const searchState = vi.hoisted(() => ({
  lastParams: null as ReturnType<typeof computed<Record<string, unknown>>> | null,
  searchData: {
    posts: { content: [] },
    boards: [],
  },
  isLoading: false,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}))

vi.mock('@/composables/useSearch', () => ({
  useSearch: () => ({
    useIntegratedSearch: (params: ReturnType<typeof computed<Record<string, unknown>>>) => {
      searchState.lastParams = params
      return {
        data: ref(searchState.searchData),
        isLoading: ref(searchState.isLoading),
      }
    },
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
      posts: { content: [] },
      boards: [],
    }
    searchState.isLoading = false
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
    expect(wrapper.find('[data-testid="empty-state"]').attributes('data-description')).toBe('search.noResultsFor:vue')
  })

  it('shows a search prompt instead of no results before a query is provided', () => {
    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="empty-state"]').text()).toBe('search.placeholder')
    expect(wrapper.find('[data-testid="empty-state"]').attributes('data-description')).toBeUndefined()
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
})
