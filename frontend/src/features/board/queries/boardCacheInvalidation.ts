import type { QueryClient } from '@tanstack/vue-query'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import { invalidateQueryKeys } from '@/composables/cacheInvalidation'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'

export function invalidateBoardListCaches(queryClient: QueryClient, sessionGeneration: number) {
  invalidateQueryKeys(queryClient, [
    sessionQueryKey(sessionGeneration, boardQueryKeys.all),
    sessionQueryKey(sessionGeneration, boardQueryKeys.subscriptions),
    sessionQueryKey(sessionGeneration, homeQueryKeys.landingRoot),
  ])
}

export function invalidateBoardSubscriptionCaches(
  queryClient: QueryClient,
  boardUrl: string,
  sessionGeneration: number,
) {
  invalidateQueryKeys(queryClient, [
    sessionQueryKey(sessionGeneration, boardQueryKeys.detail(boardUrl)),
    sessionQueryKey(sessionGeneration, boardQueryKeys.all),
    sessionQueryKey(sessionGeneration, boardQueryKeys.subscriptions),
    sessionQueryKey(sessionGeneration, homeQueryKeys.landingRoot),
  ])
}
