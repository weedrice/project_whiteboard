import type { QueryClient } from '@tanstack/vue-query'
import { boardQueryKeys } from '@/composables/boardQueryKeys'

export function invalidateBoardListCaches(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: boardQueryKeys.all })
  queryClient.invalidateQueries({ queryKey: boardQueryKeys.subscriptions })
}
