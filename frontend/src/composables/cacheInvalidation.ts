import type { QueryClient, QueryKey } from '@tanstack/vue-query'

export function invalidateQueryKeys(queryClient: QueryClient, queryKeys: readonly QueryKey[]) {
  queryKeys.forEach((queryKey) => {
    queryClient.invalidateQueries({ queryKey })
  })
}
