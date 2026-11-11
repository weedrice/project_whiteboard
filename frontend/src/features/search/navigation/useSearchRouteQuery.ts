import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSearchSubmitNavigation } from '@/features/search/navigation/useSearchSubmitNavigation'
import { firstQueryValue } from '@/utils/routeQueryValue'
import { DEFAULT_POST_LIST_SORT, normalizePostListSort } from '@/utils/postListSort'
import type { PostSearchType, SearchParams } from '@/types'

export type SearchPageGroup = 'post' | 'comment' | 'other'

type SearchPages = { page: number, postPage: number, commentPage: number }

export type SearchFilterKey = 'searchType' | 'author' | 'period' | 'dateRange'

const POST_SEARCH_TYPES = new Set<PostSearchType>(['TITLE_CONTENT', 'TITLE', 'CONTENT', 'AUTHOR'])

const normalizePage = (value: unknown) => {
  const parsed = Number.parseInt(firstQueryValue(value), 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

export const normalizePostSearchType = (value: unknown): PostSearchType => {
  const normalized = firstQueryValue(value).trim().toUpperCase() as PostSearchType
  return POST_SEARCH_TYPES.has(normalized) ? normalized : 'TITLE_CONTENT'
}

const normalizeSearchQuery = (query: Record<string, unknown>) => (
  [
    firstQueryValue(query.q),
    firstQueryValue(query.keyword),
    firstQueryValue(query.tag),
  ]
    .map((value) => value.trim())
    .find(Boolean) ?? ''
)

export function useSearchRouteQuery() {
  const route = useRoute()
  const router = useRouter()
  const searchInput = ref('')
  const authorInput = ref('')
  const searchTypeInput = ref<PostSearchType>('TITLE_CONTENT')
  const periodInput = ref('')
  const fromInput = ref('')
  const toInput = ref('')

  const searchQuery = computed(() => normalizeSearchQuery(route.query))
  const authorQuery = computed(() => firstQueryValue(route.query.author).trim())
  const searchTypeQuery = computed(() => normalizePostSearchType(route.query.searchType))
  const periodQuery = computed(() => firstQueryValue(route.query.period).trim().toUpperCase())
  const fromQuery = computed(() => firstQueryValue(route.query.from).trim())
  const toQuery = computed(() => firstQueryValue(route.query.to).trim())
  const boardUrlQuery = computed(() => firstQueryValue(route.query.boardUrl).trim())
  const pageQuery = computed(() => normalizePage(route.query.page))
  const postPageQuery = computed(() => normalizePage(route.query.postPage ?? route.query.page))
  const commentPageQuery = computed(() => normalizePage(route.query.commentPage ?? route.query.page))
  const sortQuery = computed(() => normalizePostListSort(route.query.sort))
  const { submitSearch } = useSearchSubmitNavigation({
    getCurrentSearchQuery: () => searchQuery.value,
  })
  const hasSearchQuery = computed(() => searchQuery.value.length > 0)
  const isCustomDateRangeValid = computed(() => {
    if (periodInput.value.trim().toUpperCase() !== 'CUSTOM') return true
    const from = fromInput.value.trim()
    const to = toInput.value.trim()
    return !from || !to || from <= to
  })
  const params = computed<SearchParams>(() => {
    const nextParams: SearchParams = {
      q: searchQuery.value,
      page: pageQuery.value,
      postPage: postPageQuery.value,
      commentPage: commentPageQuery.value,
      size: 20,
      searchType: searchTypeQuery.value,
    }
    if (authorQuery.value && searchTypeQuery.value !== 'AUTHOR') nextParams.author = authorQuery.value
    if (periodQuery.value) nextParams.period = periodQuery.value
    if (periodQuery.value === 'CUSTOM') {
      if (fromQuery.value) nextParams.from = fromQuery.value
      if (toQuery.value) nextParams.to = toQuery.value
    }
    if (boardUrlQuery.value) nextParams.boardUrl = boardUrlQuery.value
    if (sortQuery.value !== DEFAULT_POST_LIST_SORT) nextParams.sort = sortQuery.value
    return nextParams
  })

  watch(searchQuery, (value) => {
    searchInput.value = value
  }, { immediate: true })

  watch(authorQuery, (value) => {
    authorInput.value = value
  }, { immediate: true })

  watch(searchTypeQuery, (value) => {
    searchTypeInput.value = value
    if (value === 'AUTHOR') authorInput.value = ''
  }, { immediate: true })

  watch(periodQuery, (value) => {
    periodInput.value = value
  }, { immediate: true })

  watch(fromQuery, (value) => {
    fromInput.value = value
  }, { immediate: true })

  watch(toQuery, (value) => {
    toInput.value = value
  }, { immediate: true })

  function handleSearchSubmit() {
    if (!searchInput.value.trim() || !isCustomDateRangeValid.value) return
    const { q, ...filters } = buildFilterQuery()
    const currentFilters = buildPreservedFilterQuery()
    const filterKeys = new Set([...Object.keys(filters), ...Object.keys(currentFilters)])
    const filtersChanged = [...filterKeys].some((key) => filters[key] !== currentFilters[key])
    if (q === searchQuery.value && (filtersChanged || pageQuery.value > 0
      || postPageQuery.value > 0 || commentPageQuery.value > 0)) {
      void router.push({ name: 'search', query: { q, ...filters } })
      return
    }
    submitSearch(q, filters)
  }

  function buildPreservedFilterQuery() {
    const query: Record<string, string> = {}
    if (sortQuery.value !== DEFAULT_POST_LIST_SORT) query.sort = sortQuery.value
    if (searchTypeQuery.value !== 'TITLE_CONTENT') query.searchType = searchTypeQuery.value
    if (searchTypeQuery.value !== 'AUTHOR' && authorQuery.value) query.author = authorQuery.value
    if (periodQuery.value) query.period = periodQuery.value
    if (periodQuery.value === 'CUSTOM') {
      if (fromQuery.value) query.from = fromQuery.value
      if (toQuery.value) query.to = toQuery.value
    }
    if (boardUrlQuery.value) query.boardUrl = boardUrlQuery.value
    return query
  }

  function buildFilterQuery() {
    const query: Record<string, string> = {
      q: searchInput.value.trim() || searchQuery.value,
    }
    if (sortQuery.value !== DEFAULT_POST_LIST_SORT) query.sort = sortQuery.value
    if (searchTypeInput.value !== 'TITLE_CONTENT') query.searchType = searchTypeInput.value
    if (searchTypeInput.value !== 'AUTHOR' && authorInput.value.trim()) query.author = authorInput.value.trim()
    const period = periodInput.value.trim().toUpperCase()
    if (period) query.period = period
    if (period === 'CUSTOM') {
      if (fromInput.value.trim()) query.from = fromInput.value.trim()
      if (toInput.value.trim()) query.to = toInput.value.trim()
    }
    if (boardUrlQuery.value) query.boardUrl = boardUrlQuery.value
    return query
  }

  function buildQueryWithoutFilter(filterKey: SearchFilterKey) {
    const query: Record<string, string> = {
      q: searchQuery.value,
    }
    if (sortQuery.value !== DEFAULT_POST_LIST_SORT) query.sort = sortQuery.value

    if (filterKey !== 'searchType' && searchTypeQuery.value !== 'TITLE_CONTENT') {
      query.searchType = searchTypeQuery.value
    }

    if (filterKey !== 'author' && searchTypeQuery.value !== 'AUTHOR' && authorQuery.value) {
      query.author = authorQuery.value
    }

    if (filterKey !== 'period' && periodQuery.value) {
      query.period = periodQuery.value
    }

    if (filterKey !== 'period' && filterKey !== 'dateRange' && periodQuery.value === 'CUSTOM') {
      if (fromQuery.value) query.from = fromQuery.value
      if (toQuery.value) query.to = toQuery.value
    }
    if (boardUrlQuery.value) query.boardUrl = boardUrlQuery.value

    return query
  }

  function buildPaginationQuery(overrides: Partial<SearchPages> = {}) {
    const pages = {
      page: pageQuery.value,
      postPage: postPageQuery.value,
      commentPage: commentPageQuery.value,
      ...overrides,
    }
    const query = { ...buildPreservedFilterQuery(), q: searchQuery.value } as Record<string, string>
    if (pages.page > 0) query.page = String(pages.page)
    // Explicit zero overrides a legacy shared page in saved links.
    if (pages.postPage > 0 || pages.page > 0) query.postPage = String(pages.postPage)
    if (pages.commentPage > 0 || pages.page > 0) query.commentPage = String(pages.commentPage)
    return query
  }

  function buildPageQuery(page: number, group: SearchPageGroup = 'post') {
    const key = group === 'post' ? 'postPage' : group === 'comment' ? 'commentPage' : 'page'
    return buildPaginationQuery({ [key]: page })
  }

  function buildSortQuery(sort: string) {
    const query = buildPageQuery(0)
    const normalizedSort = normalizePostListSort(sort)
    if (normalizedSort === DEFAULT_POST_LIST_SORT) delete query.sort
    else query.sort = normalizedSort
    return query
  }

  return {
    searchInput,
    searchTypeInput,
    authorInput,
    periodInput,
    fromInput,
    toInput,
    searchQuery,
    searchTypeQuery,
    authorQuery,
    periodQuery,
    fromQuery,
    toQuery,
    boardUrlQuery,
    pageQuery,
    postPageQuery,
    commentPageQuery,
    sortQuery,
    hasSearchQuery,
    isCustomDateRangeValid,
    params,
    handleSearchSubmit,
    buildFilterQuery,
    buildQueryWithoutFilter,
    buildPageQuery,
    buildPaginationQuery,
    buildSortQuery,
  }
}
