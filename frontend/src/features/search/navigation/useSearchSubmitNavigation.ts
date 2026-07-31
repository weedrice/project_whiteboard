import { useQueryClient } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { searchQueryKeys } from '@/features/search/queries/searchQueryKeys'
import { firstQueryValue } from '@/utils/routeQueryValue'
import { useAuthStore } from '@/stores/auth'
import { currentSessionQueryKey } from '@/queryAuthScope'

interface UseSearchSubmitNavigationOptions {
  getCurrentSearchQuery?: () => string
}

export function useSearchSubmitNavigation(options: UseSearchSubmitNavigationOptions = {}) {
  const router = useRouter()
  const route = useRoute()
  const queryClient = useQueryClient()
  const authStore = useAuthStore()

  function submitSearch(rawQuery: string, additionalQuery: Record<string, string> = {}) {
    const q = rawQuery.trim()
    if (!q) return false

    const currentQuery = options.getCurrentSearchQuery?.() ?? firstQueryValue(route.query.q).trim()
    if (route.name === 'search' && currentQuery === q) {
      queryClient.invalidateQueries({
        queryKey: currentSessionQueryKey(authStore, searchQueryKeys.integratedAll),
      })
      return true
    }

    router.push({
      name: 'search',
      query: { q, ...additionalQuery },
    })
    return true
  }

  return {
    submitSearch,
  }
}
