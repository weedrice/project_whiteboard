import { describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
import { sessionQueryKey } from '@/queryAuthScope'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { expectQueriesRefetchedOnce } from '@/test/queryInvalidation'
import { invalidateProfileAuthorCaches } from '@/features/user/profile/profileCacheInvalidation'

describe('invalidateProfileAuthorCaches', () => {
  it('invalidates public profile and cached author projections in the current session', async () => {
    const invalidateQueries = vi.fn().mockResolvedValue(undefined)
    const queryClient = { invalidateQueries } as unknown as QueryClient

    await invalidateProfileAuthorCaches(queryClient, 3, 17)

    const invalidatedKeys = invalidateQueries.mock.calls.map(([filters]) => filters.queryKey)
    expect(invalidatedKeys).toEqual(expect.arrayContaining([
      ['session', 3, 'user', 'me'],
      ['session', 3, 'user', '17'],
      ['session', 3, 'post'],
      ['session', 3, 'posts'],
      ['session', 3, 'board', 'posts'],
      ['session', 3, 'board', 'notices'],
      ['session', 3, 'comments'],
      ['session', 3, 'home'],
      ['session', 3, 'search'],
      ['session', 3, 'feed'],
    ]))
  })

  it('refreshes the profile and public lists once while preserving other users and sessions', async () => {
    const affectedKeys = [
      userQueryKeys.profile(17),
      userQueryKeys.publicPosts(17, { page: 0 }),
      userQueryKeys.publicPosts(17, { page: 1 }),
      userQueryKeys.publicComments(17, { page: 0 }),
      userQueryKeys.me,
      userQueryKeys.myPosts({ page: 0 }),
      userQueryKeys.scrapsRoot,
      ['feed'],
      ['comments'],
    ].map((key) => sessionQueryKey(3, key))
    const refreshedKeys = await expectQueriesRefetchedOnce(
      (queryClient) => { void invalidateProfileAuthorCaches(queryClient, 3, 17) },
      affectedKeys,
      [
        sessionQueryKey(3, userQueryKeys.profile(99)),
        sessionQueryKey(3, userQueryKeys.publicPosts(99, { page: 0 })),
        sessionQueryKey(3, userQueryKeys.publicComments(99, { page: 0 })),
        sessionQueryKey(4, userQueryKeys.profile(17)),
        sessionQueryKey(4, userQueryKeys.publicPosts(17, { page: 0 })),
        sessionQueryKey(4, userQueryKeys.publicComments(17, { page: 0 })),
        sessionQueryKey(3, userQueryKeys.settings),
      ],
    )
    expect(refreshedKeys).toEqual(affectedKeys)
  })
})
