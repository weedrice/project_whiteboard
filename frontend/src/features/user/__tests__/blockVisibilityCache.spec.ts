import { describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
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
      ['session', 7, 'user', '42', 'posts'],
      ['session', 7, 'user', '42', 'comments'],
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
    ]))
    expect(invalidateQueries).toHaveBeenCalledTimes(14)
  })
})
