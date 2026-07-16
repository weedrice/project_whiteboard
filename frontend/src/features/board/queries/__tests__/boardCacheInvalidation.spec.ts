import { describe, expect, it, vi } from 'vitest'
import { invalidateBoardSubscriptionCaches } from '@/features/board/queries/boardCacheInvalidation'

describe('invalidateBoardSubscriptionCaches', () => {
  it('invalidates detail, board lists, subscriptions, and home landing caches', () => {
    const invalidateQueries = vi.fn()

    invalidateBoardSubscriptionCaches({ invalidateQueries } as never, 'general')

    expect(invalidateQueries).toHaveBeenNthCalledWith(1, {
      queryKey: ['board', 'detail', 'general'],
    })
    expect(invalidateQueries).toHaveBeenNthCalledWith(2, { queryKey: ['boards'] })
    expect(invalidateQueries).toHaveBeenNthCalledWith(3, {
      queryKey: ['boards', 'subscriptions'],
    })
    expect(invalidateQueries).toHaveBeenNthCalledWith(4, {
      queryKey: ['home', 'landing'],
    })
  })
})
