import type { QueryClient } from '@tanstack/vue-query'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import { invalidateQueryKeys } from '@/composables/cacheInvalidation'
import { homeQueryKeys } from '@/composables/homeQueryKeys'

export function invalidateBoardListCaches(queryClient: QueryClient) {
  invalidateQueryKeys(queryClient, [
    boardQueryKeys.all,
    boardQueryKeys.subscriptions,
    homeQueryKeys.landingRoot,
  ])
}

export function invalidateBoardSubscriptionCaches(queryClient: QueryClient, boardUrl: string) {
  invalidateQueryKeys(queryClient, [
    boardQueryKeys.detail(boardUrl),
    boardQueryKeys.all,
    boardQueryKeys.subscriptions,
    homeQueryKeys.landingRoot,
  ])
}
