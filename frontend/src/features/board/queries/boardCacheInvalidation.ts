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

function boardResourceQueryKeys(boardUrl: string) {
  return [
    boardQueryKeys.detail(boardUrl),
    boardQueryKeys.postsByBoard(boardUrl),
    boardQueryKeys.infinitePostsByBoard(boardUrl),
    boardQueryKeys.notices(boardUrl),
    boardQueryKeys.categories(boardUrl),
    boardQueryKeys.managerCandidatesRoot(boardUrl),
  ] as const
}

export function invalidateBoardResourceCaches(
  queryClient: QueryClient,
  boardUrl: string,
  sessionGeneration: number,
) {
  invalidateQueryKeys(
    queryClient,
    boardResourceQueryKeys(boardUrl).map((queryKey) => sessionQueryKey(sessionGeneration, queryKey)),
  )
}

export function removeBoardResourceCaches(
  queryClient: QueryClient,
  boardUrl: string,
  sessionGeneration: number,
) {
  boardResourceQueryKeys(boardUrl).forEach((queryKey) => {
    queryClient.removeQueries({ queryKey: sessionQueryKey(sessionGeneration, queryKey) })
  })
}
