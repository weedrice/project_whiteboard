import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import type { SearchParams } from '@/types'

const firstQueryValue = (value: unknown): string => {
  if (Array.isArray(value)) {
    return String(value[0] ?? '')
  }
  return String(value ?? '')
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
  const queryClient = useQueryClient()
  const searchInput = ref('')

  const searchQuery = computed(() => normalizeSearchQuery(route.query as Record<string, unknown>))
  const hasSearchQuery = computed(() => searchQuery.value.length > 0)
  const params = computed<SearchParams>(() => ({
    q: searchQuery.value,
    page: 0,
    size: 20,
  }))

  watch(searchQuery, (value) => {
    searchInput.value = value
  }, { immediate: true })

  function handleSearchSubmit() {
    const q = searchInput.value.trim()
    if (!q) return

    if (route.name === 'search' && searchQuery.value === q) {
      queryClient.invalidateQueries({ queryKey: ['search', 'integrated'] })
      return
    }

    router.push({
      name: 'search',
      query: { q },
    })
  }

  return {
    searchInput,
    searchQuery,
    hasSearchQuery,
    params,
    handleSearchSubmit,
  }
}
