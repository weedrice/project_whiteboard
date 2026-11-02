import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive, ref, nextTick, type ComputedRef } from 'vue'
import { useEmoticonListResource } from '../useEmoticonListResource'
import { apiSuccessDataResponse } from '@/test/apiResponseFixtures'

const mocks = vi.hoisted(() => ({
  useApiQueryOptions: [] as Array<Record<string, unknown>>,
  useApiPageQueryOptions: [] as Array<Record<string, unknown>>,
  push: vi.fn(),
  getPopularEmoticons: vi.fn(),
  searchAll: vi.fn(),
}))

const route = reactive<{ query: Record<string, string | undefined> }>({ query: {} })

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({
    push: mocks.push,
  }),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    getPopularEmoticons: mocks.getPopularEmoticons,
    searchAll: mocks.searchAll,
  },
}))

vi.mock('@/composables/useApiQuery', () => ({
  useApiQuery: (options: Record<string, unknown>) => {
    mocks.useApiQueryOptions.push(options)
    return {
      data: ref([]),
      isLoading: ref(false),
      isError: ref(false),
      refetch: vi.fn(),
    }
  },
  useApiPageQuery: (options: Record<string, unknown>) => {
    mocks.useApiPageQueryOptions.push(options)
    return {
      data: ref({
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
        first: true,
        last: true,
        empty: true,
      }),
      isLoading: ref(false),
      isError: ref(false),
      refetch: vi.fn(),
    }
  },
}))

describe('useEmoticonListResource', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.query = {}
    mocks.push.mockImplementation((target: { query?: Record<string, string | undefined> }) => {
      if (target.query) route.query = target.query
      return Promise.resolve()
    })
    mocks.useApiQueryOptions.length = 0
    mocks.useApiPageQueryOptions.length = 0
  })

  it('uses API query helpers with value-based keys and axios API requests', async () => {
    const resource = useEmoticonListResource()
    const popularOptions = mocks.useApiQueryOptions[0]
    const pageOptions = mocks.useApiPageQueryOptions[0]

    expect((popularOptions.queryKey as ComputedRef<unknown[]>).value).toEqual(['emoticons', 'popular', 'daily'])
    expect((pageOptions.queryKey as ComputedRef<unknown[]>).value).toEqual(['emoticons', 'list', 0, 'latest', '', 'ALL'])

    mocks.getPopularEmoticons.mockResolvedValueOnce(apiSuccessDataResponse<typeof mocks.getPopularEmoticons>([]))
    await (popularOptions.request as () => Promise<unknown>)()
    expect(mocks.getPopularEmoticons).toHaveBeenCalledWith('daily')

    mocks.searchAll.mockResolvedValueOnce(apiSuccessDataResponse<typeof mocks.searchAll>({ content: [] }))
    await (pageOptions.request as () => Promise<unknown>)()
    expect(mocks.searchAll).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      sortBy: 'latest',
    })

    resource.searchInput.value = '  cat  '
    resource.searchType.value = 'TAG'
    resource.handleSearch()
    await (pageOptions.request as () => Promise<unknown>)()
    expect(mocks.searchAll).toHaveBeenLastCalledWith({
      page: 0,
      size: 20,
      sortBy: 'latest',
      keyword: 'cat',
      searchType: 'TAG',
    })
  })

  it('navigates to detail pages', () => {
    const resource = useEmoticonListResource()

    resource.goToDetail(12)

    expect(mocks.push).toHaveBeenCalledWith({ name: 'emoticon-detail', params: { emoticonId: 12 } })
  })

  it('restores a shared list URL and reacts to browser history changes', async () => {
    route.query = { q: 'cat', type: 'TAG', sort: 'popular', page: '3', period: 'monthly' }
    const resource = useEmoticonListResource()
    const pageOptions = mocks.useApiPageQueryOptions[0]

    expect(resource.searchInput.value).toBe('cat')
    expect(resource.searchType.value).toBe('TAG')
    expect(resource.currentPage.value).toBe(2)
    expect(resource.popularPeriod.value).toBe('monthly')
    await (pageOptions.request as () => Promise<unknown>)()
    expect(mocks.searchAll).toHaveBeenLastCalledWith({
      page: 2, size: 20, sortBy: 'popular', keyword: 'cat', searchType: 'TAG',
    })

    route.query = { q: 'dog', type: 'NAME', page: '2' }
    await nextTick()
    expect(resource.searchInput.value).toBe('dog')
    expect(resource.searchType.value).toBe('NAME')
    expect(resource.currentPage.value).toBe(1)
    expect(resource.sortBy.value).toBe('latest')
  })

  it('resets pagination when searching or sorting and clears the active keyword', () => {
    route.query = { q: 'cat', type: 'TAG', sort: 'popular', page: '3', period: 'weekly' }
    const resource = useEmoticonListResource()
    resource.searchInput.value = ' dog '
    resource.searchType.value = 'NAME'
    resource.handleSearch()
    expect(route.query).toEqual({ q: 'dog', type: 'NAME', sort: 'popular', period: 'weekly', page: undefined })
    expect(resource.currentPage.value).toBe(0)

    resource.changeSortBy('oldest')
    expect(route.query.sort).toBe('oldest')
    expect(route.query.q).toBe('dog')
    resource.clearSearch()
    expect(resource.isSearching.value).toBe(false)
    expect(resource.searchInput.value).toBe('')
    expect(route.query.q).toBeUndefined()
  })

  it.each(['-1', '1.5', 'bad', '9007199254740992'])('normalizes invalid URL values (%s)', (page) => {
    route.query = { page, sort: 'invalid', type: 'invalid', period: 'invalid' }
    const resource = useEmoticonListResource()
    expect(resource.currentPage.value).toBe(0)
    expect(resource.sortBy.value).toBe('latest')
    expect(resource.searchType.value).toBe('ALL')
    expect(resource.popularPeriod.value).toBe('daily')
  })

})
