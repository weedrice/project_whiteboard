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
    expect(routeQuery.params.value).toEqual({ q: 'vue', page: 0, size: 20 })

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
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['search', 'integrated'] })
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
})
