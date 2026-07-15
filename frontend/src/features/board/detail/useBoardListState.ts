import { computed, ref, watch } from 'vue'
import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import {
  areQueriesEqual,
  parsePageFromQuery
} from '@/utils/routeQueryValue'
import {
  buildBoardListQueryFromSource,
  clampBoardListPage,
  createBoardListQueryParams,
  getResolvedBoardListSearchState,
  resolveBoardListRouteState,
  type BoardListFilterState,
  type BoardListQueryParams,
  type BoardListSearchState,
} from '@/composables/boardListStateModel'

type BoardListRoute = {
  path: string
  query: LocationQuery
}

type BoardListRouter = {
  replace: (to: { path: string; query: LocationQueryRaw }) => unknown
}

export function useBoardListState(route: BoardListRoute, router: BoardListRouter) {
  const page = ref(0)
  const size = ref(20)
  const searchQuery = ref('')
  const searchType = ref('TITLE_CONTENT')
  const isSearching = ref(false)
  const conceptOnly = ref(false)
  const selectedCategoryId = ref<number | null>(null)
  const sort = ref('createdAt,desc')
  let skipNextPageSync = false

  const getFilterState = (): BoardListFilterState => ({
    isSearching: isSearching.value,
    searchQuery: searchQuery.value,
    searchType: searchType.value,
    selectedCategoryId: selectedCategoryId.value,
    conceptOnly: conceptOnly.value,
  })

  const setPage = (nextPage: number, options?: { skipRouteSync?: boolean }) => {
    if (options?.skipRouteSync && page.value !== nextPage) {
      skipNextPageSync = true
    } else if (options?.skipRouteSync) {
      skipNextPageSync = false
    }

    page.value = nextPage
  }

  const buildListQueryFromSource = (
    sourceQuery: LocationQuery | LocationQueryRaw,
    targetPage: number,
    nextSearchState?: BoardListSearchState | null,
    nextCategoryId?: number | null,
    nextConceptOnly?: boolean
  ): LocationQueryRaw => buildBoardListQueryFromSource({
    sourceQuery,
    targetPage,
    filterState: getFilterState(),
    nextSearchState,
    nextCategoryId,
    nextConceptOnly,
  })

  const buildListQuery = (
    targetPage: number,
    nextSearchState?: BoardListSearchState | null,
    nextCategoryId?: number | null,
    nextConceptOnly?: boolean
  ) => (
    buildListQueryFromSource(route.query, targetPage, nextSearchState, nextCategoryId, nextConceptOnly)
  )

  const syncListQuery = (
    targetPage: number,
    nextSearchState?: BoardListSearchState | null,
    nextCategoryId?: number | null,
    nextConceptOnly?: boolean
  ) => {
    router.replace({
      path: route.path,
      query: buildListQuery(targetPage, nextSearchState, nextCategoryId, nextConceptOnly)
    })
  }

  const buildPaginationRoute = (targetPage: number) => ({
    path: route.path,
    query: buildListQuery(targetPage)
  })

  const queryParams = computed<BoardListQueryParams>(() => createBoardListQueryParams({
    ...getFilterState(),
    page: page.value,
    size: size.value,
    sort: sort.value,
  }))

  const resetToDefaultList = () => {
    searchQuery.value = ''
    isSearching.value = false
    conceptOnly.value = false
    selectedCategoryId.value = null
    setPage(0, { skipRouteSync: true })
  }

  function handleSearch() {
    const trimmedQuery = searchQuery.value.trim()
    if (!trimmedQuery) {
      clearSearch()
      return
    }

    isSearching.value = true
    conceptOnly.value = false
    selectedCategoryId.value = null
    setPage(0, { skipRouteSync: true })
    syncListQuery(0, {
      q: trimmedQuery,
      searchType: searchType.value
    }, null, false)
  }

  function clearSearch() {
    resetToDefaultList()
    syncListQuery(0, null, null, false)
  }

  function activateAllPostsFilter() {
    const nextSearchState = getResolvedBoardListSearchState(isSearching.value, searchQuery.value, searchType.value)

    conceptOnly.value = false
    selectedCategoryId.value = null
    setPage(0, { skipRouteSync: true })
    syncListQuery(0, nextSearchState, null, false)
  }

  function toggleConceptPosts() {
    const nextConceptOnly = !conceptOnly.value
    conceptOnly.value = nextConceptOnly
    selectedCategoryId.value = null
    searchQuery.value = ''
    isSearching.value = false
    setPage(0, { skipRouteSync: true })
    syncListQuery(0, null, null, nextConceptOnly)
  }

  function toggleCategory(categoryId: number | null) {
    const nextCategoryId = selectedCategoryId.value === categoryId ? null : categoryId
    conceptOnly.value = false
    selectedCategoryId.value = nextCategoryId
    searchQuery.value = ''
    isSearching.value = false
    setPage(0, { skipRouteSync: true })
    syncListQuery(0, null, nextCategoryId, false)
  }

  function handleSortChange(newSort: string) {
    if (sort.value === newSort) {
      return
    }

    sort.value = newSort
    setPage(0)
  }

  function handlePageChange(newPage: number, totalPages: number) {
    setPage(clampBoardListPage(newPage, totalPages))
  }

  function resetListState() {
    searchQuery.value = ''
    isSearching.value = false
    conceptOnly.value = false
    selectedCategoryId.value = null
    sort.value = 'createdAt,desc'
    setPage(0, { skipRouteSync: true })
  }

  watch(() => route.query, (newQuery) => {
    const routeState = resolveBoardListRouteState(newQuery)
    if (page.value !== routeState.page) {
      page.value = routeState.page
    }

    const normalizedQuery = buildListQueryFromSource(
      newQuery,
      routeState.page,
      routeState.searchState,
      routeState.selectedCategoryId,
      routeState.conceptOnly
    )

    if (!areQueriesEqual(newQuery, normalizedQuery)) {
      router.replace({
        path: route.path,
        query: normalizedQuery
      })
    }

    if (searchQuery.value !== routeState.routeQuery) {
      searchQuery.value = routeState.routeQuery
    }
    if (searchType.value !== routeState.routeSearchType) {
      searchType.value = routeState.routeSearchType
    }
    if (isSearching.value !== routeState.shouldSearch) {
      isSearching.value = routeState.shouldSearch
    }
    if (selectedCategoryId.value !== routeState.selectedCategoryId) {
      selectedCategoryId.value = routeState.selectedCategoryId
    }
    if (conceptOnly.value !== routeState.conceptOnly) {
      conceptOnly.value = routeState.conceptOnly
    }
  }, { immediate: true })

  watch(page, (newPage) => {
    if (skipNextPageSync) {
      skipNextPageSync = false
      return
    }
    if (newPage === parsePageFromQuery(route.query.page)) {
      return
    }
    syncListQuery(newPage)
  })

  return {
    page,
    size,
    searchQuery,
    searchType,
    isSearching,
    conceptOnly,
    selectedCategoryId,
    sort,
    queryParams,
    buildPaginationRoute,
    handleSearch,
    clearSearch,
    activateAllPostsFilter,
    toggleConceptPosts,
    toggleCategory,
    handleSortChange,
    handlePageChange,
    resetListState
  }
}
