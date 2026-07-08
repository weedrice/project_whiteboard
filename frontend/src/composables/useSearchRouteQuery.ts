import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useSearchSubmitNavigation } from '@/composables/useSearchSubmitNavigation'
import { firstQueryValue } from '@/utils/routeQueryValue'
import type { SearchParams } from '@/types'

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
  const periodInput = ref('')
  const fromInput = ref('')
  const toInput = ref('')

  const searchQuery = computed(() => normalizeSearchQuery(route.query))
  const authorQuery = computed(() => firstQueryValue(route.query.author).trim())
  const periodQuery = computed(() => firstQueryValue(route.query.period).trim().toUpperCase())
  const fromQuery = computed(() => firstQueryValue(route.query.from).trim())
  const toQuery = computed(() => firstQueryValue(route.query.to).trim())
  const { submitSearch } = useSearchSubmitNavigation({
    getCurrentSearchQuery: () => searchQuery.value,
  })
  const hasSearchQuery = computed(() => searchQuery.value.length > 0)
  const params = computed<SearchParams>(() => {
    const nextParams: SearchParams = {
      q: searchQuery.value,
      page: 0,
      size: 20,
    }
    if (authorQuery.value) nextParams.author = authorQuery.value
    if (periodQuery.value) nextParams.period = periodQuery.value
    if (periodQuery.value === 'CUSTOM') {
      if (fromQuery.value) nextParams.from = fromQuery.value
      if (toQuery.value) nextParams.to = toQuery.value
    }
    return nextParams
  })

  watch(searchQuery, (value) => {
    searchInput.value = value
  }, { immediate: true })

  watch(authorQuery, (value) => {
    authorInput.value = value
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
    submitSearch(searchInput.value)
  }

  function buildFilterQuery() {
    const query: Record<string, string> = {
      q: searchInput.value.trim() || searchQuery.value,
    }
    if (authorInput.value.trim()) query.author = authorInput.value.trim()
    const period = periodInput.value.trim().toUpperCase()
    if (period) query.period = period
    if (period === 'CUSTOM') {
      if (fromInput.value.trim()) query.from = fromInput.value.trim()
      if (toInput.value.trim()) query.to = toInput.value.trim()
    }
    return query
  }

  return {
    searchInput,
    authorInput,
    periodInput,
    fromInput,
    toInput,
    searchQuery,
    hasSearchQuery,
    params,
    handleSearchSubmit,
    buildFilterQuery,
  }
}
