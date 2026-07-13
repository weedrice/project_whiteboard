import { effectScope, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { usePwaReloadBlocker, whenPwaReloadSafe } from '@/pwaReloadGuard'

describe('pwaReloadGuard', () => {
  it('runs reload immediately when no blocker is active', () => {
    const reload = vi.fn()

    expect(whenPwaReloadSafe(reload)).toBe(true)
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('defers reload until an active blocker becomes safe', () => {
    const blocked = ref(true)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked))
    const reload = vi.fn()

    expect(whenPwaReloadSafe(reload)).toBe(false)
    expect(reload).not.toHaveBeenCalled()

    blocked.value = false

    expect(reload).toHaveBeenCalledTimes(1)
    scope.stop()
  })

  it('flushes a deferred reload when the blocking scope is disposed', () => {
    const blocked = ref(true)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked))
    const reload = vi.fn()

    expect(whenPwaReloadSafe(reload)).toBe(false)
    scope.stop()

    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('retains an in-flight blocker after dispose until the operation finishes', () => {
    const blocked = ref(true)
    const scope = effectScope()
    scope.run(() => usePwaReloadBlocker(blocked, { retainWhileBlockedOnDispose: true }))
    const reload = vi.fn()

    expect(whenPwaReloadSafe(reload)).toBe(false)
    scope.stop()
    expect(reload).not.toHaveBeenCalled()

    blocked.value = false
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
