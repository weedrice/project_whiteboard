import { useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { searchQueryKeys } from '@/composables/searchQueryKeys'
import { firstQueryValue } from '@/composables/searchRouteQueryValue'

interface UseSearchSubmitNavigationOptions {
  getCurrentSearchQuery?: () => string
}

export function useSearchSubmitNavigation(options: UseSearchSubmitNavigationOptions = {}) {
  const router = useRouter()
  const route = useRoute()
  const queryClient = useQueryClient()

  function submitSearch(rawQuery: string) {
    const q = rawQuery.trim()
    if (!q) return false

    const currentQuery = options.getCurrentSearchQuery?.() ?? firstQueryValue(route.query.q).trim()
    if (route.name === 'search' && currentQuery === q) {
      queryClient.invalidateQueries({ queryKey: searchQueryKeys.integratedAll })
      return true
    }

    router.push({
      name: 'search',
      query: { q },
    })
    return true
  }

  return {
    submitSearch,
  }
}
