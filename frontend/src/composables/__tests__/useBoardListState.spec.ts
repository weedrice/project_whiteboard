import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useBoardListState } from '../useBoardListState'

describe('useBoardListState', () => {
  const route = {
    path: '/board/free',
    query: {} as Record<string, string>
  }

  const router = {
    replace: vi.fn()
  }

  beforeEach(() => {
    route.query = {}
    router.replace.mockReset()
  })

  it('hydrates page, search, and category state from the route query', async () => {
    route.query = {
      page: '3',
      q: 'vue',
      type: 'TITLE',
      categoryId: '7'
    }

    const state = useBoardListState(route as never, router as never)
    await nextTick()

    expect(state.page.value).toBe(2)
    expect(state.searchQuery.value).toBe('vue')
    expect(state.searchType.value).toBe('TITLE')
    expect(state.isSearching.value).toBe(true)
    expect(state.selectedCategoryId.value).toBe(7)
  })

  it('syncs a search once and resets the page without duplicate route updates', async () => {
    const state = useBoardListState(route as never, router as never)
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

  it('clears search state back to the default board query', async () => {
    route.query = {
      page: '2',
      q: 'keyword',
      type: 'TITLE_CONTENT'
    }

    const state = useBoardListState(route as never, router as never)
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

  it('switches to a category filter and clears the active search', async () => {
    route.query = {
      q: 'keyword',
      type: 'TITLE'
    }

    const state = useBoardListState(route as never, router as never)
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

  it('clamps page changes to the available pagination range', () => {
    const state = useBoardListState(route as never, router as never)

    state.handlePageChange(10, 4)
    expect(state.page.value).toBe(3)

    state.handlePageChange(-2, 4)
    expect(state.page.value).toBe(0)
  })

  it('resets sort state when the list state is cleared for another board', () => {
    const state = useBoardListState(route as never, router as never)

    state.handleSortChange('viewCount,desc')
    expect(state.sort.value).toBe('viewCount,desc')

    state.resetListState()
    expect(state.sort.value).toBe('createdAt,desc')
  })
})
