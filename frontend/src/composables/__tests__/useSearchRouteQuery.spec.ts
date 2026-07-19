import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, reactive } from 'vue'
import { useSearchRouteQuery } from '../useSearchRouteQuery'

const routerPush = vi.fn()
const invalidateQueries = vi.fn()
const routeState = reactive({
  name: 'search' as string,
  query: {} as Record<string, unknown>,
})

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({
    push: routerPush,
  }),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({
    invalidateQueries,
  }),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ sessionGeneration: 0 }),
}))

describe('useSearchRouteQuery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.name = 'search'
    routeState.query = {}
  })

  it('normalizes q, keyword, tag aliases for deep links', () => {
    routeState.query = { q: ' vue ', keyword: 'ignored', tag: 'ignored' }
    let routeQuery = useSearchRouteQuery()

    expect(routeQuery.searchQuery.value).toBe('vue')
    expect(routeQuery.params.value).toEqual({ q: 'vue', page: 0, size: 20, searchType: 'TITLE_CONTENT' })

    routeState.query = { keyword: ['pinia', 'second'] }
    routeQuery = useSearchRouteQuery()
    expect(routeQuery.searchQuery.value).toBe('pinia')

    routeState.query = { tag: 'notice' }
    routeQuery = useSearchRouteQuery()
    expect(routeQuery.searchQuery.value).toBe('notice')
  })

  it('keeps search input in sync with the route query', async () => {
    routeState.query = { q: 'initial' }
    const routeQuery = useSearchRouteQuery()

    expect(routeQuery.searchInput.value).toBe('initial')

    routeState.query = { q: 'next' }
    await nextTick()

    expect(routeQuery.searchInput.value).toBe('next')
  })

  it('pushes new searches with only the canonical q query', () => {
    const routeQuery = useSearchRouteQuery()

    routeQuery.searchInput.value = '  local query  '
    routeQuery.handleSearchSubmit()

    expect(routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: { q: 'local query' },
    })
  })

  it('invalidates the integrated search cache for the same submitted query', () => {
    routeState.query = { q: 'same' }
    const routeQuery = useSearchRouteQuery()

    routeQuery.searchInput.value = 'same'
    routeQuery.handleSearchSubmit()

    expect(routerPush).not.toHaveBeenCalled()
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'search', 'integrated'] })
  })

  it('builds route queries for removing individual filters', () => {
    routeState.query = {
      q: 'vue',
      author: 'hong',
      period: 'CUSTOM',
      from: '2026-07-01',
      to: '2026-07-08',
    }
    const routeQuery = useSearchRouteQuery()

    expect(routeQuery.buildQueryWithoutFilter('author')).toEqual({
      q: 'vue',
      period: 'CUSTOM',
      from: '2026-07-01',
      to: '2026-07-08',
    })
    expect(routeQuery.buildQueryWithoutFilter('dateRange')).toEqual({
      q: 'vue',
      author: 'hong',
      period: 'CUSTOM',
    })
    expect(routeQuery.buildQueryWithoutFilter('period')).toEqual({
      q: 'vue',
      author: 'hong',
    })
  })

  it('preserves the selected scope and filters when the search text changes', () => {
    routeState.query = { q: 'old', searchType: 'TITLE', author: 'hong', period: 'WEEK' }
    const routeQuery = useSearchRouteQuery()

    routeQuery.searchInput.value = 'new'
    routeQuery.handleSearchSubmit()

    expect(routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: {
        q: 'new',
        searchType: 'TITLE',
        author: 'hong',
        period: 'WEEK',
      },
    })
  })

  it('identifies reversed custom date input before navigation', () => {
    const routeQuery = useSearchRouteQuery()
    routeQuery.periodInput.value = 'CUSTOM'
    routeQuery.fromInput.value = '2026-07-20'
    routeQuery.toInput.value = '2026-07-10'

    expect(routeQuery.isCustomDateRangeValid.value).toBe(false)

    routeQuery.toInput.value = '2026-07-20'
    expect(routeQuery.isCustomDateRangeValid.value).toBe(true)

    routeQuery.toInput.value = ''
    expect(routeQuery.isCustomDateRangeValid.value).toBe(true)
  })

  it('preserves boardUrl as a supported integrated and semantic search scope', () => {
    routeState.query = { q: 'old', boardUrl: ' notice ' }
    const routeQuery = useSearchRouteQuery()

    expect(routeQuery.boardUrlQuery.value).toBe('notice')
    expect(routeQuery.params.value.boardUrl).toBe('notice')

    routeQuery.searchInput.value = 'new'
    routeQuery.handleSearchSubmit()
    expect(routerPush).toHaveBeenCalledWith({
      name: 'search',
      query: { q: 'new', boardUrl: 'notice' },
    })
  })

  it('normalizes the result page and preserves filters in pagination links', () => {
    routeState.query = { q: 'vue', page: '2', searchType: 'TITLE', period: 'WEEK' }
    const routeQuery = useSearchRouteQuery()

    expect(routeQuery.pageQuery.value).toBe(2)
    expect(routeQuery.params.value.page).toBe(2)
    expect(routeQuery.buildPageQuery(3)).toEqual({
      q: 'vue',
      page: '3',
      searchType: 'TITLE',
      period: 'WEEK',
    })
    expect(routeQuery.buildPageQuery(0)).toEqual({
      q: 'vue',
      searchType: 'TITLE',
      period: 'WEEK',
    })
  })

  it.each(['TITLE_CONTENT', 'TITLE', 'CONTENT', 'AUTHOR'] as const)(
    'round-trips the %s post search scope',
    (searchType) => {
      routeState.query = { q: 'vue', searchType, author: 'hong' }
      const routeQuery = useSearchRouteQuery()

      expect(routeQuery.searchTypeQuery.value).toBe(searchType)
      expect(routeQuery.params.value.searchType).toBe(searchType)
      expect(routeQuery.params.value.author).toBe(searchType === 'AUTHOR' ? undefined : 'hong')

      routeQuery.searchTypeInput.value = searchType
      expect(routeQuery.buildFilterQuery()).toEqual({
        q: 'vue',
        ...(searchType === 'TITLE_CONTENT' ? {} : { searchType }),
        ...(searchType === 'AUTHOR' ? {} : { author: 'hong' }),
      })
    },
  )

  it('normalizes unknown scopes and removes a scope without losing other filters', () => {
    routeState.query = { q: 'vue', searchType: 'UNKNOWN', period: 'WEEK' }
    let routeQuery = useSearchRouteQuery()

    expect(routeQuery.searchTypeQuery.value).toBe('TITLE_CONTENT')
    expect(routeQuery.params.value.searchType).toBe('TITLE_CONTENT')

    routeState.query = { q: 'vue', searchType: 'CONTENT', period: 'WEEK' }
    routeQuery = useSearchRouteQuery()
    expect(routeQuery.buildQueryWithoutFilter('searchType')).toEqual({
      q: 'vue',
      period: 'WEEK',
    })
  })
})
