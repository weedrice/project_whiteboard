import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSearchSubmitNavigation } from '@/composables/useSearchSubmitNavigation'
import { firstQueryValue } from '@/utils/routeQueryValue'
import type { PostSearchType, SearchParams } from '@/types'

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
    submitSearch(searchInput.value, buildPreservedFilterQuery())
  }

  function buildPreservedFilterQuery() {
    const query: Record<string, string> = {}
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

  function buildPageQuery(page: number) {
    const query = buildPreservedFilterQuery()
    query.q = searchQuery.value
    if (page > 0) query.page = String(page)
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
    hasSearchQuery,
    isCustomDateRangeValid,
    params,
    handleSearchSubmit,
    buildFilterQuery,
    buildQueryWithoutFilter,
    buildPageQuery,
  }
}
