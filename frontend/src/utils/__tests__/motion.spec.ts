import { afterEach, describe, expect, it, vi } from 'vitest'
import { getMotionAwareScrollBehavior, prefersReducedMotion } from '../motion'

const originalMatchMedia = window.matchMedia

afterEach(() => {
  window.matchMedia = originalMatchMedia
})

describe('motion preferences', () => {
  it.each([
    [true, 'auto'],
    [false, 'smooth'],
  ] as const)('returns the matching scroll behavior when reduced motion is %s', (matches, behavior) => {
    window.matchMedia = vi.fn().mockReturnValue({ matches })

    expect(prefersReducedMotion()).toBe(matches)
    expect(getMotionAwareScrollBehavior()).toBe(behavior)
  })
})
