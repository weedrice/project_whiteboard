import { describe, expect, it } from 'vitest'
import {
  buildBoardListQueryFromSource,
  clampBoardListPage,
  createBoardListQueryParams,
  getResolvedBoardListSearchState,
  resolveBoardListRouteState,
  type BoardListFilterState,
} from '../boardListStateModel'

const filterState = (overrides: Partial<BoardListFilterState> = {}): BoardListFilterState => ({
  isSearching: false,
  searchQuery: '',
  searchType: 'TITLE_CONTENT',
  selectedCategoryId: null,
  conceptOnly: false,
  ...overrides,
})

describe('boardListStateModel', () => {
  it('creates API query params from the active filter state', () => {
    expect(createBoardListQueryParams({
      ...filterState({ isSearching: true, searchQuery: ' vue ', searchType: 'TITLE' }),
      page: 1,
      size: 20,
      sort: 'createdAt,desc',
    })).toEqual({
      page: 1,
      size: 20,
      sort: 'createdAt,desc',
      q: 'vue',
      searchType: 'TITLE',
    })
    expect(createBoardListQueryParams({
      ...filterState({ conceptOnly: true }),
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    }).minLikes).toBe(5)
    expect(createBoardListQueryParams({
      ...filterState({ selectedCategoryId: 7 }),
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    }).categoryId).toBe(7)
  })

  it('builds normalized list route queries from source query values', () => {
    expect(buildBoardListQueryFromSource({
      sourceQuery: { page: '4', categoryId: '7' },
      targetPage: 0,
      filterState: filterState({ selectedCategoryId: 7 }),
      nextSearchState: { q: 'keyword', searchType: 'AUTHOR' },
    })).toEqual({
      q: 'keyword',
      type: 'AUTHOR',
    })

    expect(buildBoardListQueryFromSource({
      sourceQuery: { q: 'keyword', type: 'TITLE' },
      targetPage: 2,
      filterState: filterState(),
      nextSearchState: null,
      nextCategoryId: null,
      nextConceptOnly: true,
    })).toEqual({
      page: '3',
      concept: '1',
    })
  })

  it('resolves route query state and clamps pagination', () => {
    expect(resolveBoardListRouteState({
      page: '3',
      q: ' vue ',
      type: 'TITLE',
      categoryId: '7',
    })).toMatchObject({
      page: 2,
      routeQuery: 'vue',
      routeSearchType: 'TITLE',
      shouldSearch: true,
      searchState: { q: 'vue', searchType: 'TITLE' },
      selectedCategoryId: null,
      conceptOnly: false,
    })
    expect(resolveBoardListRouteState({ concept: '1' })).toMatchObject({
      page: 0,
      shouldSearch: false,
      selectedCategoryId: null,
      conceptOnly: true,
    })
    expect(getResolvedBoardListSearchState(true, ' vue ', 'TITLE')).toEqual({ q: 'vue', searchType: 'TITLE' })
    expect(getResolvedBoardListSearchState(true, '   ', 'TITLE')).toBeNull()
    expect(clampBoardListPage(10, 4)).toBe(3)
    expect(clampBoardListPage(-1, 4)).toBe(0)
  })
})
