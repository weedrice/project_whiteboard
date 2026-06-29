import type { QueryClient } from '@tanstack/vue-query'
import { boardQueryKeys } from '@/composables/boardQueryKeys'
import { invalidateQueryKeys } from '@/composables/cacheInvalidation'

export function invalidateBoardListCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    boardQueryKeys.all,
    boardQueryKeys.subscriptions,
  ])
}
