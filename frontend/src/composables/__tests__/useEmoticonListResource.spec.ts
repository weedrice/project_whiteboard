import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref, type ComputedRef } from 'vue'
import { useEmoticonListResource } from '../useEmoticonListResource'

const mocks = vi.hoisted(() => ({
  useApiQueryOptions: [] as Array<Record<string, unknown>>,
  useApiPageQueryOptions: [] as Array<Record<string, unknown>>,
  push: vi.fn(),
  getPopularEmoticons: vi.fn(),
  searchAll: vi.fn(),
}))

vi.mock('vue-router', () => ({
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
    }
  },
}))

describe('useEmoticonListResource', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.useApiQueryOptions.length = 0
    mocks.useApiPageQueryOptions.length = 0
  })

  it('uses API query helpers with value-based keys and axios API requests', async () => {
    const resource = useEmoticonListResource()
    const popularOptions = mocks.useApiQueryOptions[0]
    const pageOptions = mocks.useApiPageQueryOptions[0]

    expect((popularOptions.queryKey as ComputedRef<unknown[]>).value).toEqual(['emoticons', 'popular', 'daily'])
    expect((pageOptions.queryKey as ComputedRef<unknown[]>).value).toEqual(['emoticons', 'list', 0, 'latest', '', 'ALL'])

    mocks.getPopularEmoticons.mockResolvedValueOnce({ data: { success: true, data: [] } })
    await (popularOptions.request as () => Promise<unknown>)()
    expect(mocks.getPopularEmoticons).toHaveBeenCalledWith('daily')

    mocks.searchAll.mockResolvedValueOnce({ data: { success: true, data: { content: [] } } })
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
})
