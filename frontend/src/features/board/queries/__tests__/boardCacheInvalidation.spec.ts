import { describe, expect, it } from 'vitest'
import {
  invalidateBoardListCaches,
  invalidateBoardSubscriptionCaches,
} from '@/features/board/queries/boardCacheInvalidation'
import { boardQueryKeys } from '@/features/board/queries/boardQueryKeys'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'
import { expectQueriesRefetchedOnce } from '@/test/queryInvalidation'

const listKeys = [
  [...boardQueryKeys.all, { page: 0 }],
  boardQueryKeys.subscriptionsBySize(10),
  boardQueryKeys.subscriptionsBySize(20),
  homeQueryKeys.landing('24h', 'user'),
]

describe('board cache invalidation', () => {
  it('refreshes board lists, subscriptions, and home once without cancelling requests or touching other sessions', async () => {
    const affectedKeys = listKeys
    const refreshedKeys = await expectQueriesRefetchedOnce(
      (queryClient) => invalidateBoardListCaches(queryClient, 3),
      listKeys.map((key) => sessionQueryKey(3, key)),
      [
        ...listKeys.map((key) => sessionQueryKey(4, key)),
        sessionQueryKey(3, boardQueryKeys.detail('general')),
      ],
    )
    expect(refreshedKeys).toEqual(affectedKeys.map((key) => sessionQueryKey(3, key)))
  })

  it('also refreshes the subscribed board detail once while preserving other board details', async () => {
    const affectedKeys = [...listKeys, boardQueryKeys.detail('general')]
    const refreshedKeys = await expectQueriesRefetchedOnce(
      (queryClient) => invalidateBoardSubscriptionCaches(queryClient, 'general', 3),
      affectedKeys.map((key) => sessionQueryKey(3, key)),
      [
        ...affectedKeys.map((key) => sessionQueryKey(4, key)),
        sessionQueryKey(3, boardQueryKeys.detail('other')),
      ],
    )
    expect(refreshedKeys).toEqual(affectedKeys.map((key) => sessionQueryKey(3, key)))
  })
})
