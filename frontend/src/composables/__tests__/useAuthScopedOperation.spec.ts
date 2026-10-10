import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, reactive, type EffectScope } from 'vue'
import { useAuthScopedOperation } from '@/composables/useAuthScopedOperation'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const owners: EffectScope[] = []
function setup() {
  const source = reactive({ sessionGeneration: 1, user: { userId: 1 }, accessToken: 'first' })
  const owner = effectScope()
  owners.push(owner)
  const operations = owner.run(() => useAuthScopedOperation(source))!
  return { source, owner, operations }
}
afterEach(() => owners.splice(0).forEach(owner => owner.stop()))

describe('session-owned local operations', () => {
  it('captures before confirmation and releases the UI immediately at a session boundary', async () => {
    const { source, operations } = setup()
    const confirmation = deferred<boolean>()
    const request = vi.fn()
    const first = operations.run('save', async scope => {
      if (!await confirmation.promise) return
      scope.assertCurrent()
      request(scope.requestConfig)
    })
    expect(operations.isPending('save')).toBe(true)
    source.sessionGeneration++
    expect(operations.isPending('save')).toBe(false)
    confirmation.resolve(true)
    await expect(first).resolves.toBeUndefined()
    expect(request).not.toHaveBeenCalled()
  })

  it('does not let an old completion release a new operation with the same key', async () => {
    const { source, operations } = setup()
    const oldRequest = deferred<string>()
    const newRequest = deferred<string>()
    const old = operations.run('save', () => oldRequest.promise)
    source.sessionGeneration++
    const current = operations.run('save', () => newRequest.promise)
    oldRequest.resolve('old')
    await expect(old).resolves.toBeUndefined()
    expect(operations.isPending('save')).toBe(true)
    newRequest.resolve('current')
    await expect(current).resolves.toBe('current')
    expect(operations.isAnyPending.value).toBe(false)
  })

  it('prevents duplicate keys while allowing independent rows to run together', async () => {
    const { operations } = setup()
    const gate = deferred<string>()
    const duplicate = vi.fn()
    const row = operations.run('row-a', () => gate.promise)
    await expect(operations.run('row-a', duplicate)).resolves.toBeUndefined()
    await expect(operations.run('row-b', () => 'saved')).resolves.toBe('saved')
    expect(duplicate).not.toHaveBeenCalled()
    expect(operations.isPending('row-a')).toBe(true)
    gate.resolve('a')
    await row
  })

  it.each(['generation', 'user'] as const)('suppresses stale errors on a %s change', async boundary => {
    const { source, operations } = setup()
    const gate = deferred<string>()
    const pending = operations.run('save', () => gate.promise)
    if (boundary === 'generation') source.sessionGeneration++
    else source.user.userId = 2
    gate.reject(new Error('old failure'))
    await expect(pending).resolves.toBeUndefined()
    expect(operations.isAnyPending.value).toBe(false)
  })

  it('preserves ordinary token rotation and surfaces a current-session error', async () => {
    const { source, operations } = setup()
    const gate = deferred<string>()
    const pending = operations.run('save', () => gate.promise)
    source.accessToken = 'refreshed'
    expect(operations.isPending('save')).toBe(true)
    gate.resolve('saved')
    await expect(pending).resolves.toBe('saved')
    await expect(operations.run('save', () => { throw new Error('validation') })).rejects.toThrow('validation')
    expect(operations.isAnyPending.value).toBe(false)
  })

  it('cancels manual UI ownership when its component scope is disposed', async () => {
    const { operations, owner } = setup()
    const gate = deferred<void>()
    const ui = vi.fn()
    const pending = operations.run('save', async scope => {
      await gate.promise
      scope.runIfCurrent(ui)
    })
    owner.stop()
    gate.resolve()
    await expect(pending).resolves.toBeUndefined()
    expect(ui).not.toHaveBeenCalled()
    await operations.run('save', ui)
    expect(ui).not.toHaveBeenCalled()
  })

  it('deduplicates reactive boundary notifications and unregisters local resets on disposal', () => {
    const { source, owner } = setup()
    const reset = vi.fn()
    owner.run(() => useAuthSessionReset(reset, source))
    source.sessionGeneration++
    notifyAuthSessionBoundary(source.sessionGeneration)
    expect(reset).toHaveBeenCalledOnce()
    owner.stop()
    source.sessionGeneration++
    notifyAuthSessionBoundary(source.sessionGeneration)
    expect(reset).toHaveBeenCalledOnce()
  })
})
