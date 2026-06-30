import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
