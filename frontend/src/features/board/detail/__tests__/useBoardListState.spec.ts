import { effectScope, nextTick, reactive } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardListState } from '../useBoardListState'
import type { LocationQuery } from 'vue-router'

describe('useBoardListState', () => {
  const route = {
    path: '/board/free',
    query: {} as LocationQuery
  }

  const router = {
    replace: vi.fn()
  }

  beforeEach(() => {
    route.query = {}
    router.replace.mockReset()
  })

  const mountListState = () => useBoardListState(route, router)

  it('hydrates page and search state from the route query', async () => {
    route.query = {
      page: '3',
      q: 'vue',
      type: 'TITLE'
    }

    const state = mountListState()
    await nextTick()

    expect(state.page.value).toBe(2)
    expect(state.searchQuery.value).toBe('vue')
    expect(state.searchType.value).toBe('TITLE')
    expect(state.isSearching.value).toBe(true)
    expect(state.selectedCategoryId.value).toBeNull()
  })

  it('hydrates concept state from the route query and keeps it in pagination routes', async () => {
    route.query = {
      concept: '1'
    }

    const state = mountListState()
    await nextTick()

    expect(state.conceptOnly.value).toBe(true)
    expect(state.selectedCategoryId.value).toBeNull()
    expect(state.buildPaginationRoute(2)).toEqual({
      path: '/board/free',
      query: {
        page: '3',
        concept: '1'
      }
    })
  })

  it('syncs a search once and resets the page without duplicate route updates', async () => {
    const state = mountListState()
    state.page.value = 4
    await nextTick()
    router.replace.mockReset()

    state.searchQuery.value = 'keyword'
    state.searchType.value = 'AUTHOR'
    state.handleSearch()
    await nextTick()

    expect(state.page.value).toBe(0)
    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        q: 'keyword',
        type: 'AUTHOR'
      }
    })
  })

  it('does not suppress the next page sync when a filtered action keeps the page at zero', async () => {
    const state = mountListState()
    await nextTick()

    state.searchQuery.value = 'keyword'
    state.handleSearch()
    await nextTick()

    router.replace.mockReset()

    state.handlePageChange(1, 5)
    await nextTick()

    expect(state.page.value).toBe(1)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        page: '2',
        q: 'keyword',
        type: 'TITLE_CONTENT'
      }
    })
  })

  it('clears search state back to the default board query', async () => {
    route.query = {
      page: '2',
      q: 'keyword',
      type: 'TITLE_CONTENT'
    }

    const state = mountListState()
    await nextTick()
    router.replace.mockReset()

    state.clearSearch()
    await nextTick()

    expect(state.searchQuery.value).toBe('')
    expect(state.isSearching.value).toBe(false)
    expect(state.page.value).toBe(0)
    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {}
    })
  })

  it('clears category and concept state when selecting all posts', async () => {
    route.query = {
      q: 'keyword',
      type: 'TITLE',
      concept: '1'
    }

    const state = mountListState()
    await nextTick()
    router.replace.mockReset()

    state.activateAllPostsFilter()
    await nextTick()

    expect(state.searchQuery.value).toBe('keyword')
    expect(state.isSearching.value).toBe(true)
    expect(state.conceptOnly.value).toBe(false)
    expect(state.selectedCategoryId.value).toBeNull()
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        q: 'keyword',
        type: 'TITLE'
      }
    })
  })

  it('normalizes mixed search and category queries to the effective search-only state', async () => {
    route.query = {
      q: 'keyword',
      type: 'TITLE',
      categoryId: '7'
    }

    const state = mountListState()
    await nextTick()

    expect(state.isSearching.value).toBe(true)
    expect(state.selectedCategoryId.value).toBeNull()
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        q: 'keyword',
        type: 'TITLE'
      }
    })
  })

  it('switches to a category filter and clears the active search', async () => {
    route.query = {
      q: 'keyword',
      type: 'TITLE'
    }

    const state = mountListState()
    await nextTick()
    router.replace.mockReset()

    state.toggleCategory(3)
    await nextTick()

    expect(state.searchQuery.value).toBe('')
    expect(state.isSearching.value).toBe(false)
    expect(state.selectedCategoryId.value).toBe(3)
    expect(router.replace).toHaveBeenCalledTimes(1)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        categoryId: '3'
      }
    })
  })

  it('clears the current category when the same category is selected again', async () => {
    route.query = {
      categoryId: '3'
    }

    const state = mountListState()
    await nextTick()
    router.replace.mockReset()

    state.toggleCategory(3)
    await nextTick()

    expect(state.selectedCategoryId.value).toBeNull()
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {}
    })
  })

  it('switches to concept posts through the existing minLikes board filter', async () => {
    const state = mountListState()
    await nextTick()
    router.replace.mockReset()

    state.toggleConceptPosts()
    await nextTick()

    expect(state.conceptOnly.value).toBe(true)
    expect(state.selectedCategoryId.value).toBeNull()
    expect(state.queryParams.value.minLikes).toBe(5)
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {
        concept: '1'
      }
    })
  })

  it('clears concept mode when the concept filter is pressed again', async () => {
    route.query = {
      concept: '1'
    }

    const state = mountListState()
    await nextTick()
    router.replace.mockReset()

    state.toggleConceptPosts()
    await nextTick()

    expect(state.conceptOnly.value).toBe(false)
    expect(state.queryParams.value.minLikes).toBeUndefined()
    expect(router.replace).toHaveBeenCalledWith({
      path: '/board/free',
      query: {}
    })
  })

  it('clamps page changes to the available pagination range', () => {
    const state = mountListState()

    state.handlePageChange(10, 4)
    expect(state.page.value).toBe(3)

    state.handlePageChange(-2, 4)
    expect(state.page.value).toBe(0)
  })

  it('resets sort state when the list state is cleared for another board', () => {
    const state = mountListState()

    state.toggleConceptPosts()
    state.handleSortChange('viewCount,desc')
    expect(state.conceptOnly.value).toBe(true)
    expect(state.sort.value).toBe('viewCount,desc')

    state.resetListState()
    expect(state.conceptOnly.value).toBe(false)
    expect(state.sort.value).toBe('createdAt,desc')
  })
})


describe('applied board search state', () => {
  let scope: ReturnType<typeof effectScope>

  afterEach(() => scope?.stop())

  function setup(query: LocationQuery = { q: 'original', type: 'TITLE', page: '3' }) {
    const route = reactive({ path: '/board/free', query })
    const router = { replace: vi.fn() }
    scope = effectScope()
    const state = scope.run(() => useBoardListState(route, router))!
    return { state, route, router }
  }

  it.each(['', 'replacement'])('keeps the applied request while the input is changed to %j', async (draft) => {
    const { state } = setup()
    state.searchQuery.value = draft
    state.searchType.value = 'AUTHOR'
    await nextTick()

    expect(state.isSearching.value).toBe(true)
    expect(state.queryParams.value).toMatchObject({ q: 'original', searchType: 'TITLE', page: 2 })
    expect(state.buildPaginationRoute(3).query).toEqual({ q: 'original', type: 'TITLE', page: '4' })
  })

  it('applies edited text and scope only when search is submitted', async () => {
    const { state, router } = setup()
    state.searchQuery.value = ' replacement '
    state.searchType.value = 'AUTHOR'
    state.handleSearch()
    await nextTick()

    expect(state.queryParams.value).toMatchObject({ q: 'replacement', searchType: 'AUTHOR', page: 0 })
    expect(router.replace).toHaveBeenCalledWith({ path: '/board/free', query: { q: 'replacement', type: 'AUTHOR' } })
  })

  it.each(['page', 'sort', 'all'] as const)('keeps the applied search during a %s action', async (action) => {
    const { state } = setup()
    state.searchQuery.value = 'unsubmitted'
    state.searchType.value = 'AUTHOR'
    if (action === 'page') state.handlePageChange(3, 5)
    if (action === 'sort') state.handleSortChange('viewCount,desc')
    if (action === 'all') state.activateAllPostsFilter()
    await nextTick()

    expect(state.queryParams.value).toMatchObject({ q: 'original', searchType: 'TITLE' })
    expect(state.buildPaginationRoute(1).query).toEqual({ q: 'original', type: 'TITLE', page: '2' })
  })

  it.each(['empty submit', 'clear', 'category', 'concept', 'reset'] as const)('clears the applied search on %s', async (action) => {
    const { state } = setup()
    state.searchQuery.value = ''
    if (action === 'empty submit') state.handleSearch()
    if (action === 'clear') state.clearSearch()
    if (action === 'category') state.toggleCategory(7)
    if (action === 'concept') state.toggleConceptPosts()
    if (action === 'reset') state.resetListState()
    await nextTick()

    expect(state.isSearching.value).toBe(false)
    expect(state.queryParams.value.q).toBeUndefined()
    expect(state.queryParams.value.searchType).toBeUndefined()
    expect(state.page.value).toBe(0)
  })

  it('restores both draft and applied search when navigating through URL history', async () => {
    const { state, route } = setup()
    state.searchQuery.value = 'unsubmitted'
    state.searchType.value = 'AUTHOR'
    route.query = { q: 'previous', type: 'CONTENT', page: '2' }
    await nextTick()

    expect(state.searchQuery.value).toBe('previous')
    expect(state.searchType.value).toBe('CONTENT')
    expect(state.queryParams.value).toMatchObject({ q: 'previous', searchType: 'CONTENT', page: 1 })

    route.query = {}
    await nextTick()
    expect(state.isSearching.value).toBe(false)
    expect(state.searchQuery.value).toBe('')
    expect(state.queryParams.value.q).toBeUndefined()

    route.query = { q: 'original', type: 'TITLE', page: '3' }
    await nextTick()
    expect(state.searchQuery.value).toBe('original')
    expect(state.queryParams.value).toMatchObject({ q: 'original', searchType: 'TITLE', page: 2 })
  })
})
