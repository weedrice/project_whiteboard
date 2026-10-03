import { computed, defineComponent, nextTick, reactive, ref } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { LocationQuery } from 'vue-router'
import { boardApi } from '@/api/board'
import { searchApi } from '@/api/search'
import { apiDataResponse } from '@/test/apiResponseFixtures'
import { useBoardListState } from '@/features/board/detail/useBoardListState'
import { useBoardQueries } from '../useBoardQueries'

const authStore = reactive({ sessionGeneration: 0 })
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('@/api/board', () => ({ boardApi: { getPosts: vi.fn() } }))
vi.mock('@/api/search', () => ({ searchApi: { searchPosts: vi.fn() } }))

let queryClient: QueryClient
let wrapper: VueWrapper | undefined
const responsePage = (page: number, title: string) => ({
  content: [{ postId: page + 1, title }],
  number: page,
  size: 20,
  totalElements: 200,
  totalPages: 10,
  last: page >= 9,
  first: page === 0,
  empty: false,
})

function setup(initiallyMobile = true) {
  const boardUrl = ref('free')
  const mobile = ref(initiallyMobile)
  const route = reactive({ path: '/board/free', query: { page: '5' } as LocationQuery })
  let state!: ReturnType<typeof useBoardListState>
  let infinite!: ReturnType<ReturnType<typeof useBoardQueries>['useInfiniteBoardPosts']>
  let paged!: ReturnType<ReturnType<typeof useBoardQueries>['useBoardPosts']>
  wrapper = mount(defineComponent({
    setup() {
      state = useBoardListState(route, {
        replace: ({ query }) => { route.query = query as LocationQuery },
      })
      const queries = useBoardQueries()
      infinite = queries.useInfiniteBoardPosts(boardUrl, state.queryParams, state.isSearching, mobile)
      paged = queries.useBoardPosts(boardUrl, state.queryParams, state.isSearching, computed(() => !mobile.value))
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
  return { boardUrl, mobile, route, state, infinite, paged }
}

describe('infinite board list navigation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authStore.sessionGeneration = 0
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: Infinity } } })
    vi.mocked(boardApi.getPosts).mockImplementation(async (boardUrl, params) => (
      apiDataResponse<typeof boardApi.getPosts>(responsePage(params?.page ?? 0, boardUrl))
    ))
    vi.mocked(searchApi.searchPosts).mockImplementation(async (params) => (
      apiDataResponse<typeof searchApi.searchPosts>(responsePage(params.page ?? 0, params.q ?? 'search'))
    ))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    queryClient.clear()
  })

  it('resets category, search and sort lists to page zero after a later-page entry', async () => {
    const { state, infinite } = setup()
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([4]))
    state.toggleCategory(7)
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([0]))
    expect(boardApi.getPosts).toHaveBeenLastCalledWith('free', expect.objectContaining({ page: 0, categoryId: 7 }), expect.anything())

    state.searchQuery.value = 'keyword'
    state.handleSearch()
    await vi.waitFor(() => expect(searchApi.searchPosts).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, q: 'keyword', boardUrl: 'free' }), expect.anything(),
    ))
    await vi.waitFor(() => expect(infinite.data.value?.pages[0]?.content[0]?.title).toBe('keyword'))
    state.handleSortChange('likeCount,desc')
    await vi.waitFor(() => expect(searchApi.searchPosts).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 0, sort: 'likeCount,desc' }), expect.anything(),
    ))
  })

  it('follows URL back navigation and page correction without reusing a different starting page', async () => {
    const { route, state, infinite } = setup()
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([4]))
    route.query = { page: '3' }
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([2]))
    await infinite.fetchNextPage()
    expect(infinite.data.value?.pageParams).toEqual([2, 3])
    route.query = { page: '5' }
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([4]))

    route.query = { page: '100' }
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([99]))
    state.handlePageChange(state.page.value, infinite.data.value!.pages[0]!.totalPages)
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([9]))
    expect(route.query.page).toBe('10')
  })

  it('uses the current desktop page when switching to mobile and retains loaded mobile pages', async () => {
    const { mobile, state, infinite, paged } = setup(false)
    await vi.waitFor(() => expect(paged.data.value?.number).toBe(4))
    state.handlePageChange(6, 10)
    await vi.waitFor(() => expect(paged.data.value?.number).toBe(6))
    mobile.value = true
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([6]))
    await infinite.fetchNextPage()
    expect(infinite.data.value?.pageParams).toEqual([6, 7])
    mobile.value = false
    await nextTick()
    expect(paged.data.value?.number).toBe(6)
    mobile.value = true
    await nextTick()
    expect(infinite.data.value?.pageParams).toEqual([6, 7])
  })

  it('starts a different board and a new session without previous list pages', async () => {
    const { boardUrl, route, state, infinite } = setup()
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([4]))
    boardUrl.value = 'another'
    route.path = '/board/another'
    route.query = {}
    state.resetListState()
    await vi.waitFor(() => expect(infinite.data.value?.pages[0]?.content[0]?.title).toBe('another'))
    expect(infinite.data.value?.pageParams).toEqual([0])
    await infinite.fetchNextPage()
    expect(infinite.data.value?.pageParams).toEqual([0, 1])
    authStore.sessionGeneration += 1
    await nextTick()
    expect(infinite.data.value).toBeUndefined()
    await vi.waitFor(() => expect(infinite.data.value?.pageParams).toEqual([0]))
  })
})
