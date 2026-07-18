import { describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
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
      ['session', 3, 'user', '17', 'posts'],
      ['session', 3, 'user', '17', 'comments'],
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
})
