import { describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
import { sessionQueryKey } from '@/queryAuthScope'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { expectQueriesRefetchedOnce } from '@/test/queryInvalidation'
import { invalidateBlockVisibilityCaches } from '@/features/user/blockVisibilityCache'

describe('invalidateBlockVisibilityCaches', () => {
  it('invalidates every current-session cache whose visibility depends on blocking', async () => {
    const invalidateQueries = vi.fn().mockResolvedValue(undefined)
    const queryClient = { invalidateQueries } as unknown as QueryClient

    await invalidateBlockVisibilityCaches(queryClient, 7, 42)

    const invalidatedKeys = invalidateQueries.mock.calls.map(([filters]) => filters.queryKey)
    expect(invalidatedKeys).toEqual(expect.arrayContaining([
      ['session', 7, 'user', 'blocks'],
      ['session', 7, 'user', '42'],
      ['session', 7, 'post'],
      ['session', 7, 'posts'],
      ['session', 7, 'board', 'posts'],
      ['session', 7, 'home'],
      ['session', 7, 'search'],
      ['session', 7, 'tags'],
      ['session', 7, 'feed'],
      ['session', 7, 'comments'],
      ['session', 7, 'messages'],
      ['session', 7, 'messages', 'unread-count'],
      ['session', 7, 'notifications'],
    ]))
    expect(invalidateQueries).toHaveBeenCalledTimes(13)
  })

  it('refreshes the profile and public lists once while preserving other users and sessions', async () => {
    const affectedKeys = [
      userQueryKeys.profile(42),
      userQueryKeys.publicPosts(42, { page: 0 }),
      userQueryKeys.publicPosts(42, { page: 1 }),
      userQueryKeys.publicComments(42, { page: 0 }),
      userQueryKeys.blocksRoot,
      ['feed'],
      ['comments'],
    ].map((key) => sessionQueryKey(7, key))
    const refreshedKeys = await expectQueriesRefetchedOnce(
      (queryClient) => { void invalidateBlockVisibilityCaches(queryClient, 7, 42) },
      affectedKeys,
      [
        sessionQueryKey(7, userQueryKeys.profile(99)),
        sessionQueryKey(7, userQueryKeys.publicPosts(99, { page: 0 })),
        sessionQueryKey(7, userQueryKeys.publicComments(99, { page: 0 })),
        sessionQueryKey(8, userQueryKeys.profile(42)),
        sessionQueryKey(8, userQueryKeys.publicPosts(42, { page: 0 })),
        sessionQueryKey(8, userQueryKeys.publicComments(42, { page: 0 })),
        sessionQueryKey(7, userQueryKeys.settings),
      ],
    )
    expect(refreshedKeys).toEqual(affectedKeys)
  })
})
