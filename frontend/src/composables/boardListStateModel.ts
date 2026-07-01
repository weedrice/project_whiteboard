import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import {
  parseCategoryIdFromQuery,
  parseConceptFromQuery,
  parsePageFromQuery
} from '@/utils/routeQueryValue'

export interface BoardListSearchState {
  q: string
  searchType: string
}

export interface BoardListQueryParams {
  page: number
  size: number
  sort: string
  q?: string
  searchType?: string
  categoryId?: number
  minLikes?: number
}

export type BoardListFilterState = {
  isSearching: boolean
  searchQuery: string
  searchType: string
  selectedCategoryId: number | null
  conceptOnly: boolean
}

export type BoardListRouteState = {
  page: number
  routeQuery: string
  routeSearchType: string
  shouldSearch: boolean
  searchState: BoardListSearchState | null
  selectedCategoryId: number | null
  conceptOnly: boolean
}

export function getResolvedBoardListSearchState(
  isSearching: boolean,
  searchQuery: string,
  searchType: string,
): BoardListSearchState | null {
  const trimmedQuery = searchQuery.trim()
  if (!isSearching || !trimmedQuery) {
    return null
  }

  return {
    q: trimmedQuery,
    searchType
  }
}

export function createBoardListQueryParams({
  page,
  size,
  sort,
  isSearching,
  searchQuery,
  searchType,
  selectedCategoryId,
  conceptOnly,
}: BoardListFilterState & {
  page: number
  size: number
  sort: string
}): BoardListQueryParams {
  const params: BoardListQueryParams = {
    page,
    size,
    sort
  }

  if (isSearching && searchQuery.trim()) {
    params.q = searchQuery.trim()
    params.searchType = searchType
  } else if (conceptOnly) {
    params.minLikes = 5
  } else if (selectedCategoryId !== null) {
    params.categoryId = selectedCategoryId
  }

  return params
}

export function buildBoardListQueryFromSource({
  sourceQuery,
  targetPage,
  filterState,
  nextSearchState,
  nextCategoryId,
  nextConceptOnly,
}: {
  sourceQuery: LocationQuery | LocationQueryRaw
  targetPage: number
  filterState: BoardListFilterState
  nextSearchState?: BoardListSearchState | null
  nextCategoryId?: number | null
  nextConceptOnly?: boolean
}): LocationQueryRaw {
  const query: LocationQueryRaw = { ...sourceQuery }

  if (targetPage <= 0) {
    delete query.page
  } else {
    query.page = String(targetPage + 1)
  }

  const resolvedSearchState = nextSearchState === undefined
    ? getResolvedBoardListSearchState(filterState.isSearching, filterState.searchQuery, filterState.searchType)
    : nextSearchState

  if (resolvedSearchState?.q) {
    query.q = resolvedSearchState.q
    query.type = resolvedSearchState.searchType
  } else {
    delete query.q
    delete query.type
  }

  const resolvedCategoryId = nextCategoryId === undefined ? filterState.selectedCategoryId : nextCategoryId
  const resolvedConceptOnly = nextConceptOnly === undefined ? filterState.conceptOnly : nextConceptOnly

  if (resolvedSearchState?.q) {
    delete query.categoryId
    delete query.concept
  } else if (resolvedConceptOnly) {
    query.concept = '1'
    delete query.categoryId
  } else {
    delete query.concept

    if (resolvedCategoryId !== null) {
      query.categoryId = String(resolvedCategoryId)
    } else {
      delete query.categoryId
    }
  }

  return query
}

export function resolveBoardListRouteState(query: LocationQuery): BoardListRouteState {
  const page = parsePageFromQuery(query.page)
  const routeQuery = typeof query.q === 'string' ? query.q.trim() : ''
  const routeSearchType = typeof query.type === 'string' ? query.type : 'TITLE_CONTENT'
  const routeCategoryId = parseCategoryIdFromQuery(query.categoryId)
  const shouldSearch = routeQuery.length > 0
  const searchState = shouldSearch
    ? { q: routeQuery, searchType: routeSearchType }
    : null
  const selectedCategoryId = shouldSearch ? null : routeCategoryId
  const conceptOnly = !shouldSearch && routeCategoryId === null && parseConceptFromQuery(query.concept)

  return {
    page,
    routeQuery,
    routeSearchType,
    shouldSearch,
    searchState,
    selectedCategoryId,
    conceptOnly,
  }
}

export function clampBoardListPage(page: number, totalPages: number): number {
  const maxPage = Math.max(totalPages - 1, 0)
  return Math.min(Math.max(page, 0), maxPage)
}
