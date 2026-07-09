import { describe, expect, it } from 'vitest'
import { queryClient } from '@/queryClient'

describe('queryClient defaults', () => {
  it('keeps short-lived cached query data fresh for 30 seconds by default', () => {
    expect(queryClient.getDefaultOptions().queries?.staleTime).toBe(30_000)
  })
})
