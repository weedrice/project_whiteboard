import { computed, ref, watch } from 'vue'
import type { Router, RouteLocationNormalizedLoaded } from 'vue-router'

interface SearchState {
  q: string
  searchType: string
}

interface BoardListQueryParams {
  page: number
  size: number
  sort: string
  q?: string
  searchType?: string
  categoryId?: number
  minLikes?: number
}

export function useBoardListState(route: RouteLocationNormalizedLoaded, router: Router) {
  const page = ref(0)
  const size = ref(20)
  const searchQuery = ref('')
  const searchType = ref('TITLE_CONTENT')
  const isSearching = ref(false)
  const conceptOnly = ref(false)
  const selectedCategoryId = ref<number | null>(null)
  const sort = ref('createdAt,desc')
  let skipNextPageSync = false

  const setPage = (nextPage: number, options?: { skipRouteSync?: boolean }) => {
    if (options?.skipRouteSync && page.value !== nextPage) {
      skipNextPageSync = true
    } else if (options?.skipRouteSync) {
      skipNextPageSync = false
    }

    page.value = nextPage
  }

  const parsePageFromQuery = (value: unknown): number => {
    const parsed = Number.parseInt(String(value ?? '1'), 10)
    if (Number.isNaN(parsed) || parsed < 1) {
      return 0
    }
    return parsed - 1
  }

  const parseCategoryIdFromQuery = (value: unknown): number | null => {
    const parsed = Number.parseInt(String(value ?? ''), 10)
    if (Number.isNaN(parsed) || parsed <= 0) {
      return null
    }
    return parsed
  }

  const parseConceptFromQuery = (value: unknown): boolean => {
    const raw = String(value ?? '').trim().toLowerCase()
    return raw === '1' || raw === 'true'
  }

  const buildListQuery = (
    targetPage: number,
    nextSearchState?: SearchState | null,
    nextCategoryId?: number | null,
    nextConceptOnly?: boolean
  ) => {
    const query = { ...route.query }

    if (targetPage <= 0) {
      delete query.page
    } else {
      query.page = String(targetPage + 1)
    }

    const resolvedSearchState = nextSearchState === undefined
      ? (isSearching.value && searchQuery.value.trim()
        ? { q: searchQuery.value.trim(), searchType: searchType.value }
        : null)
      : nextSearchState

    if (resolvedSearchState?.q) {
      query.q = resolvedSearchState.q
      query.type = resolvedSearchState.searchType
    } else {
      delete query.q
      delete query.type
    }

    const resolvedCategoryId = nextCategoryId === undefined ? selectedCategoryId.value : nextCategoryId
    const resolvedConceptOnly = nextConceptOnly === undefined ? conceptOnly.value : nextConceptOnly

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

  const syncListQuery = (
    targetPage: number,
    nextSearchState?: SearchState | null,
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

  const queryParams = computed<BoardListQueryParams>(() => {
    const params: BoardListQueryParams = {
      page: page.value,
      size: size.value,
      sort: sort.value
    }

    if (isSearching.value && searchQuery.value.trim()) {
      params.q = searchQuery.value.trim()
      params.searchType = searchType.value
    } else if (conceptOnly.value) {
      params.minLikes = 5
    } else if (selectedCategoryId.value !== null) {
      params.categoryId = selectedCategoryId.value
    }

    return params
  })

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

  function selectAllPosts() {
    resetToDefaultList()
    syncListQuery(0, null, null, false)
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
    const maxPage = Math.max(totalPages - 1, 0)
    setPage(Math.min(Math.max(newPage, 0), maxPage))
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
    const nextPage = parsePageFromQuery(newQuery.page)
    if (page.value !== nextPage) {
      page.value = nextPage
    }

    const routeQuery = typeof newQuery.q === 'string' ? newQuery.q.trim() : ''
    const routeSearchType = typeof newQuery.type === 'string' ? newQuery.type : 'TITLE_CONTENT'
    const routeCategoryId = parseCategoryIdFromQuery(newQuery.categoryId)
    const shouldSearch = routeQuery.length > 0
    const shouldShowConcept = !shouldSearch && routeCategoryId === null && parseConceptFromQuery(newQuery.concept)

    if (searchQuery.value !== routeQuery) {
      searchQuery.value = routeQuery
    }
    if (searchType.value !== routeSearchType) {
      searchType.value = routeSearchType
    }
    if (isSearching.value !== shouldSearch) {
      isSearching.value = shouldSearch
    }
    if (selectedCategoryId.value !== routeCategoryId) {
      selectedCategoryId.value = routeCategoryId
    }
    if (conceptOnly.value !== shouldShowConcept) {
      conceptOnly.value = shouldShowConcept
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
    selectAllPosts,
    toggleConceptPosts,
    toggleCategory,
    handleSortChange,
    handlePageChange,
    resetListState
  }
}
