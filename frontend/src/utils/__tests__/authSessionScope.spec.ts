import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosRequestConfig, GenericAbortSignal } from 'axios'
import { notifyAuthSessionBoundary } from '@/queryAuthScope'
import { createAuthSessionScope, runAuthScopedAction } from '@/utils/authSessionScope'

type SessionScope = ReturnType<typeof createAuthSessionScope>

const scopes: SessionScope[] = []
const source = () => ({ sessionGeneration: 3, user: { userId: 7 }, accessToken: 'before-refresh' })

function openScope(session: ReturnType<typeof source>) {
  const scope = createAuthSessionScope(session)
  scopes.push(scope)
  return scope
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function changeAccount(session: ReturnType<typeof source>) {
  session.sessionGeneration += 1
  session.user = { userId: 8 }
  notifyAuthSessionBoundary(session.sessionGeneration)
}

function genericSignal(controller: AbortController): GenericAbortSignal {
  return {
    get aborted() { return controller.signal.aborted },
    addEventListener: controller.signal.addEventListener.bind(controller.signal),
    removeEventListener: controller.signal.removeEventListener.bind(controller.signal),
  }
}

afterEach(() => {
  scopes.splice(0).forEach(scope => scope.dispose())
  vi.restoreAllMocks()
})

describe('createAuthSessionScope', () => {
  it('keeps overlapping old and new account operations bound to their own starting identities', () => {
    const session = source()
    const oldScope = openScope(session)

    changeAccount(session)
    const newScope = openScope(session)

    expect(oldScope.intent).toEqual({ sessionGeneration: 3, userId: 7 })
    expect(oldScope.requestConfig.expectedSession).toEqual(oldScope.intent)
    expect(oldScope.requestConfig.signal.aborted).toBe(true)
    expect(oldScope.isCurrent()).toBe(false)
    expect(() => oldScope.assertCurrent()).toThrow(expect.objectContaining({
      name: 'AbortError', suppressGlobalErrorToast: true,
    }))
    expect(newScope.intent).toEqual({ sessionGeneration: 4, userId: 8 })
    expect(newScope.requestConfig.signal.aborted).toBe(false)
    expect(newScope.isCurrent()).toBe(true)
  })

  it.each(['same-user login', 'different user'] as const)('checks identity even without a boundary notification: %s', (change) => {
    const session = source()
    const scope = openScope(session)

    if (change === 'same-user login') session.sessionGeneration += 1
    else session.user = { userId: 8 }

    expect(scope.isCurrent()).toBe(false)
    expect(() => scope.assertCurrent()).toThrow(expect.objectContaining({ name: 'AbortError' }))
  })

  it('allows an access-token refresh for the same session and synchronous UI updates', () => {
    const session = source()
    const scope = openScope(session)
    const update = vi.fn(() => 'updated')

    session.accessToken = 'after-refresh'

    expect(scope.isCurrent()).toBe(true)
    expect(() => scope.assertCurrent()).not.toThrow()
    expect(scope.runIfCurrent(update)).toBe('updated')
    expect(update).toHaveBeenCalledOnce()
  })

  it('does not execute synchronous UI updates after a session boundary', () => {
    const session = source()
    const scope = openScope(session)
    const update = vi.fn()

    changeAccount(session)

    expect(scope.runIfCurrent(update)).toBeUndefined()
    expect(update).not.toHaveBeenCalled()
  })

  it('disposes idempotently without cancelling a completed operation', () => {
    const session = source()
    const scope = openScope(session)

    scope.dispose()
    scope.dispose()
    changeAccount(session)

    expect(scope.requestConfig.signal.aborted).toBe(false)
    expect(scope.isCurrent()).toBe(false)
  })

  it('preserves request options while rejecting an attempted replacement of the starting identity', () => {
    const scope = openScope(source())
    const config: AxiosRequestConfig = {
      headers: { 'X-Feature': 'common-codes' },
      timeout: 2000,
      skipGlobalErrorHandler: true,
      expectedSession: { sessionGeneration: 99, userId: 99 },
    }

    const merged = scope.withRequestConfig(config)

    expect(merged).toMatchObject({
      headers: config.headers,
      timeout: 2000,
      skipGlobalErrorHandler: true,
      expectedSession: { sessionGeneration: 3, userId: 7 },
    })
    expect(merged.signal).toBe(scope.requestConfig.signal)
    expect(config.expectedSession).toEqual({ sessionGeneration: 99, userId: 99 })
  })

  it.each(['native', 'generic'] as const)('honors caller cancellation with a %s Axios signal', (kind) => {
    const scope = openScope(source())
    const caller = new AbortController()
    const signal = kind === 'native' ? caller.signal : genericSignal(caller)
    const merged = scope.withRequestConfig({ signal })

    caller.abort()

    expect(merged.signal?.aborted).toBe(true)
    expect(scope.requestConfig.signal.aborted).toBe(false)
    expect(scope.isCurrent()).toBe(true)
  })

  it.each(['native', 'generic'] as const)('honors session cancellation when a %s caller signal was provided', (kind) => {
    const session = source()
    const scope = openScope(session)
    const caller = new AbortController()
    const signal = kind === 'native' ? caller.signal : genericSignal(caller)
    const merged = scope.withRequestConfig({ signal })

    changeAccount(session)

    expect(merged.signal?.aborted).toBe(true)
    expect(caller.signal.aborted).toBe(false)
  })

  it.each(['native', 'generic'] as const)('keeps an already cancelled %s caller signal cancelled', (kind) => {
    const scope = openScope(source())
    const caller = new AbortController()
    caller.abort()

    const merged = scope.withRequestConfig({ signal: kind === 'native' ? caller.signal : genericSignal(caller) })

    expect(merged.signal?.aborted).toBe(true)
  })

  it('removes the listener attached to a generic caller signal when disposed', () => {
    const scope = openScope(source())
    const signal = genericSignal(new AbortController())
    const add = vi.spyOn(signal, 'addEventListener')
    const remove = vi.spyOn(signal, 'removeEventListener')
    scope.withRequestConfig({ signal })

    scope.dispose()
    scope.dispose()

    expect(add).toHaveBeenCalledOnce()
    expect(remove).toHaveBeenCalledOnce()
    expect(remove.mock.calls[0]?.slice(0, 2)).toEqual(add.mock.calls[0]?.slice(0, 2))
  })
})

describe('runAuthScopedAction', () => {
  it('captures the initiating session before the action first yields and suppresses a stale result', async () => {
    const session = source()
    const gate = deferred<string>()
    let captured!: SessionScope
    const action = runAuthScopedAction(session, async (scope) => {
      captured = scope
      return gate.promise
    })

    expect(captured.intent).toEqual({ sessionGeneration: 3, userId: 7 })
    changeAccount(session)
    const currentAction = runAuthScopedAction(session, async scope => scope.intent.userId)
    gate.resolve('old private response')

    await expect(action).resolves.toBeUndefined()
    await expect(currentAction).resolves.toBe(8)
  })

  it('suppresses a stale failure after switching accounts', async () => {
    const session = source()
    const gate = deferred<void>()
    const action = runAuthScopedAction(session, async () => gate.promise)

    changeAccount(session)
    gate.reject(new Error('Old account request failed'))

    await expect(action).resolves.toBeUndefined()
  })

  it('checks again after a non-request await before a chained write', async () => {
    const session = source()
    const confirmation = deferred<boolean>()
    const write = vi.fn()
    const action = runAuthScopedAction(session, async (scope) => {
      if (!(await confirmation.promise)) return
      scope.assertCurrent()
      write(scope.requestConfig)
    })

    changeAccount(session)
    confirmation.resolve(true)

    await expect(action).resolves.toBeUndefined()
    expect(write).not.toHaveBeenCalled()
  })

  it('can skip post-await UI effects through the same scope', async () => {
    const session = source()
    const refetch = deferred<void>()
    const closeModal = vi.fn()
    const action = runAuthScopedAction(session, async (scope) => {
      await refetch.promise
      scope.runIfCurrent(closeModal)
    })

    changeAccount(session)
    refetch.resolve()

    await expect(action).resolves.toBeUndefined()
    expect(closeModal).not.toHaveBeenCalled()
  })

  it.each(['success', 'error'] as const)('releases its boundary listener after %s without cancelling the settled signal', async (outcome) => {
    const session = source()
    const failure = new Error('Current session request failed')
    let captured!: SessionScope
    const action = runAuthScopedAction(session, async (scope) => {
      captured = scope
      if (outcome === 'error') throw failure
      return 'saved'
    })

    const settled = action.then(value => ({ value }), error => ({ error }))
    const expected = outcome === 'error' ? { error: failure } : { value: 'saved' }
    await expect(settled).resolves.toEqual(expected)
    changeAccount(session)

    expect(captured.requestConfig.signal.aborted).toBe(false)
  })

  it('releases its boundary listener after cancellation', async () => {
    const session = source()
    const gate = deferred<void>()
    const action = runAuthScopedAction(session, async () => gate.promise)
    changeAccount(session)
    gate.resolve()
    await expect(action).resolves.toBeUndefined()
    const abort = vi.spyOn(AbortController.prototype, 'abort')

    notifyAuthSessionBoundary(session.sessionGeneration + 1)

    expect(abort).not.toHaveBeenCalled()
  })
})
