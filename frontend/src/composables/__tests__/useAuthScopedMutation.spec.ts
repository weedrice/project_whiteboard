import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick, ref } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import {
  focusManager,
  MutationCache,
  onlineManager,
  QueryClient,
  VueQueryPlugin,
} from '@tanstack/vue-query'
import type { AxiosError } from 'axios'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import { configureAuthSessionEffects, useAuthStore } from '@/stores/auth'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { isCanceledRequestError } from '@/api/errorHandling'
import { closeAuthBoundaryChannelForTest } from '@/api/authBoundaryChannel'
import { closeAuthRefreshCoordinatorForTest } from '@/api/authRefreshCoordinator'
import { shouldSuppressGlobalErrorToast } from '@/utils/errorHandler'
import { clearStoredAuthTokens } from '@/utils/authTokenStorage'
import type { User } from '@/types'

// The store and Vue Query are real. Only remote auth and browser push teardown
// are replaced, so these tests cannot make requests or alter a subscription.
vi.mock('@/api/auth', () => ({ authApi: {} }))
vi.mock('@/features/notifications/pushSubscriptions', () => ({
  detachBrowserPushSubscriptionForSession: vi.fn(async () => undefined),
}))

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function rejectionOf(promise: Promise<unknown>): Promise<unknown> {
  // Attach immediately: a session boundary is allowed to reject synchronously
  // before a deferred mutation body or onMutate is released by the test.
  return promise.then(
    () => { throw new Error('Expected the stale mutation to reject') },
    error => error,
  )
}

function expectCancellation(error: unknown) {
  expect(isCanceledRequestError(error as AxiosError)).toBe(true)
  expect(shouldSuppressGlobalErrorToast(error)).toBe(true)
}

const user = (userId: number): User => ({
  userId,
  loginId: `user-${userId}`,
  displayName: `User ${userId}`,
  email: `user-${userId}@example.test`,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
})

let pinia: Pinia
let authStore: ReturnType<typeof useAuthStore>
const wrappers: VueWrapper[] = []
const clients: QueryClient[] = []

function setupComposable<T>(create: () => T, mutationCache = new MutationCache()) {
  const queryClient = new QueryClient({
    mutationCache,
    defaultOptions: { mutations: { retry: false, gcTime: 0 } },
  })
  clients.push(queryClient)
  let result!: T
  const wrapper = mount(defineComponent({
    setup() {
      result = create()
      return () => null
    },
  }), {
    global: { plugins: [pinia, [VueQueryPlugin, { queryClient }]] },
  })
  wrappers.push(wrapper)
  return { result, queryClient, wrapper }
}

function switchAccount(userId = 2) {
  authStore.setTokens(`token-${userId}`)
  authStore.user = user(userId)
}

beforeEach(() => {
  vi.clearAllMocks()
  onlineManager.setOnline(true)
  focusManager.setFocused(true)
  pinia = createPinia()
  setActivePinia(pinia)
  authStore = useAuthStore()
  configureAuthQueryScope(() => authStore.sessionGeneration)
  configureAuthSessionEffects({ onSessionBoundary: notifyAuthSessionBoundary })
  authStore.setTokens('token-a')
  authStore.user = user(1)
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  clients.splice(0).forEach(client => client.clear())
  configureAuthSessionEffects({})
  configureAuthQueryScope(() => 0)
  closeAuthBoundaryChannelForTest()
  closeAuthRefreshCoordinatorForTest()
  clearStoredAuthTokens(false)
  onlineManager.setOnline(true)
  focusManager.setFocused(undefined)
})

describe('useAuthScopedMutation with real Vue Query', () => {
  it('rejects completion after switching during an asynchronous success callback', async () => {
    const gate = deferred<void>()
    const entered = vi.fn()
    const callSuccess = vi.fn()
    const cleanup = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async () => 'saved',
      onSuccess: async () => { entered(); await gate.promise },
      onCleanup: cleanup,
    }))
    const failure = rejectionOf(result.mutateAsync(undefined, { onSuccess: callSuccess }))
    await vi.waitFor(() => expect(entered).toHaveBeenCalledOnce())
    switchAccount()
    gate.resolve()
    expectCancellation(await failure)
    expect(callSuccess).not.toHaveBeenCalled()
    expect(result.data.value).toBeUndefined()
    expect(cleanup).toHaveBeenCalledOnce()
  })

  it('runs resource cleanup only once if a settled callback fails', async () => {
    const cleanup = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async () => 'saved',
      onSettled: vi.fn().mockRejectedValueOnce(new Error('callback failed')),
      onCleanup: cleanup,
    }))
    await expect(result.mutateAsync()).rejects.toThrow('callback failed')
    expect(cleanup).toHaveBeenCalledOnce()
  })

  it('preserves variables, optimistic context and both kinds of successful callback', async () => {
    const variables = { postId: 7 }
    const optimisticContext = { previousLikes: 2 }
    const expectedSession = { sessionGeneration: authStore.sessionGeneration, userId: 1 }
    const onSuccess = vi.fn()
    const onSettled = vi.fn()
    const callSuccess = vi.fn()
    const callSettled = vi.fn()
    const onCleanup = vi.fn()
    const request = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async (value: typeof variables, context) => {
        context.assertCurrent()
        request(value, context.requestConfig)
        return { likes: 3 }
      },
      onMutate: (value, context) => {
        expect(value).toBe(variables)
        expect(context.requestConfig.expectedSession).toEqual(expectedSession)
        context.assertCurrent()
        return optimisticContext
      },
      onSuccess,
      onSettled,
      onCleanup,
    }))

    await expect(result.mutateAsync(variables, {
      onSuccess: callSuccess,
      onSettled: callSettled,
    })).resolves.toEqual({ likes: 3 })

    expect(request).toHaveBeenCalledWith(variables, expect.objectContaining({
      expectedSession,
      signal: expect.any(AbortSignal),
    }))
    for (const callback of [onSuccess, callSuccess]) {
      expect(callback.mock.calls[0]?.slice(0, 3)).toEqual([{ likes: 3 }, variables, optimisticContext])
    }
    for (const callback of [onSettled, callSettled, onCleanup]) {
      expect(callback.mock.calls[0]?.slice(0, 4)).toEqual([{ likes: 3 }, null, variables, optimisticContext])
    }
    expect(result.variables.value).toEqual(variables)
    expect(result.data.value).toEqual({ likes: 3 })
  })

  it('does not execute an offline mutation under the account present when connectivity returns', async () => {
    onlineManager.setOnline(false)
    focusManager.setFocused(false)
    const request = vi.fn(async (value: number) => value)
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({ mutationFn: request, onSuccess, onError }))
    const failure = rejectionOf(result.mutateAsync(7))
    await vi.waitFor(() => expect(result.isPaused.value).toBe(true))

    switchAccount()
    onlineManager.setOnline(true)
    focusManager.setFocused(true)

    expectCancellation(await failure)
    expect(request).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })

  it('protects mutate as well as mutateAsync and suppresses stale per-call UI callbacks', async () => {
    onlineManager.setOnline(false)
    const request = vi.fn(async () => 'saved')
    const callSuccess = vi.fn()
    const callError = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({ mutationFn: request }))
    result.mutate(undefined, { onSuccess: callSuccess, onError: callError })
    await vi.waitFor(() => expect(result.isPaused.value).toBe(true))

    switchAccount()
    onlineManager.setOnline(true)

    await vi.waitFor(() => expect(result.isPending.value).toBe(false))
    expect(request).not.toHaveBeenCalled()
    expect(callSuccess).not.toHaveBeenCalled()
    expect(callError).not.toHaveBeenCalled()
  })

  it('captures intent before an asynchronous MutationCache onMutate callback', async () => {
    const gate = deferred<void>()
    const cacheOnMutate = vi.fn(async () => gate.promise)
    const request = vi.fn(async () => 'saved')
    const onMutate = vi.fn(() => ({ previous: 'value' }))
    const onCleanup = vi.fn()
    const { result } = setupComposable(
      () => useAuthScopedMutation({ mutationFn: request, onMutate, onCleanup }),
      new MutationCache({ onMutate: cacheOnMutate }),
    )
    const failure = rejectionOf(result.mutateAsync())
    await vi.waitFor(() => expect(cacheOnMutate).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve()

    expectCancellation(await failure)
    expect(onMutate).not.toHaveBeenCalled()
    expect(request).not.toHaveBeenCalled()
    expect(onCleanup).toHaveBeenCalledOnce()
    expectCancellation(onCleanup.mock.calls[0]?.[1])
    expect(onCleanup.mock.calls[0]?.[3]).toBeUndefined()
  })

  it('rechecks after an asynchronous onMutate before executing the mutation', async () => {
    const gate = deferred<void>()
    const entered = vi.fn()
    const request = vi.fn(async () => 'saved')
    const onError = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: request,
      onMutate: async () => {
        entered()
        await gate.promise
        return { previous: 'old' }
      },
      onError,
    }))
    const failure = rejectionOf(result.mutateAsync())
    await vi.waitFor(() => expect(entered).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve()

    expectCancellation(await failure)
    expect(request).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })

  it('classifies a stale rejected onMutate as cancellation before global error handling', async () => {
    const gate = deferred<void>()
    const entered = vi.fn()
    const globalError = vi.fn()
    const request = vi.fn(async () => 'saved')
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: request,
      onMutate: async () => {
        entered()
        await gate.promise
      },
    }), new MutationCache({ onError: globalError }))
    const failure = rejectionOf(result.mutateAsync())
    await vi.waitFor(() => expect(entered).toHaveBeenCalledOnce())

    switchAccount()
    gate.reject(new Error('Old account snapshot failed'))

    expectCancellation(await failure)
    expectCancellation(globalError.mock.calls[0]?.[0])
    expect(request).not.toHaveBeenCalled()
  })

  it('provides an assertion for side effects after an await inside the mutation body', async () => {
    const gate = deferred<void>()
    const entered = vi.fn()
    const secondRequest = vi.fn()
    const cleanup = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async (_variables: void, context) => {
        entered()
        try {
          await gate.promise
          context.assertCurrent()
          secondRequest(context.requestConfig)
        } finally {
          cleanup()
        }
      },
    }))
    const failure = rejectionOf(result.mutateAsync())
    await vi.waitFor(() => expect(entered).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve()

    expectCancellation(await failure)
    expect(secondRequest).not.toHaveBeenCalled()
    expect(cleanup).toHaveBeenCalledOnce()
  })

  it('isolates overlapping invocations with identical primitive variables', async () => {
    const firstResponse = deferred<string>()
    const requests: Array<{ generation: number, userId: number | string | null, signal?: AbortSignal }> = []
    const onSuccess = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async (_value: number, context) => {
        const session = context.requestConfig.expectedSession
        requests.push({
          generation: session.sessionGeneration,
          userId: session.userId,
          signal: context.requestConfig.signal,
        })
        return requests.length === 1 ? firstResponse.promise : 'second account'
      },
      onSuccess,
    }))
    const firstGeneration = authStore.sessionGeneration
    const firstFailure = rejectionOf(result.mutateAsync(7))
    await vi.waitFor(() => expect(requests).toHaveLength(1))

    switchAccount()
    await expect(result.mutateAsync(7)).resolves.toBe('second account')
    firstResponse.resolve('first account')

    expectCancellation(await firstFailure)
    expect(requests.map(({ generation, userId }) => ({ generation, userId }))).toEqual([
      { generation: firstGeneration, userId: 1 },
      { generation: authStore.sessionGeneration, userId: 2 },
    ])
    expect(requests[0]?.signal?.aborted).toBe(true)
    expect(requests[1]?.signal?.aborted).toBe(false)
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(onSuccess.mock.calls[0]?.[0]).toBe('second account')
  })

  it('rejects a stale successful response before either success callback runs', async () => {
    const response = deferred<string>()
    const request = vi.fn((_value: number) => response.promise)
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const onSettled = vi.fn()
    const onCleanup = vi.fn()
    const optimisticContext = { previous: 'old account state' }
    const callSuccess = vi.fn()
    const callError = vi.fn()
    const callSettled = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: request,
      onMutate: () => optimisticContext,
      onSuccess,
      onError,
      onSettled,
      onCleanup,
    }))
    const failure = rejectionOf(result.mutateAsync(7, {
      onSuccess: callSuccess,
      onError: callError,
      onSettled: callSettled,
    }))
    await vi.waitFor(() => expect(request).toHaveBeenCalledOnce())

    switchAccount()
    response.resolve('private response from old account')

    expectCancellation(await failure)
    for (const callback of [onSuccess, onError, onSettled, callSuccess, callError, callSettled]) {
      expect(callback).not.toHaveBeenCalled()
    }
    expect(onCleanup).toHaveBeenCalledOnce()
    expectCancellation(onCleanup.mock.calls[0]?.[1])
    expect(onCleanup.mock.calls[0]?.slice(2, 4)).toEqual([7, optimisticContext])
    expect(onCleanup.mock.calls[0]?.[4].requestConfig.expectedSession).toEqual({
      sessionGeneration: authStore.sessionGeneration - 1,
      userId: 1,
    })
    expect(onCleanup.mock.calls[0]?.[4].requestConfig.signal.aborted).toBe(true)
  })

  it('allows ordinary token refresh for the same session while the request is running', async () => {
    const response = deferred<string>()
    const request = vi.fn()
    const onSuccess = vi.fn()
    let signal: AbortSignal | undefined
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async (_variables: void, context) => {
        signal = context.requestConfig.signal
        request(context.requestConfig.expectedSession)
        await response.promise
        context.assertCurrent()
        return 'saved'
      },
      onSuccess,
    }))
    const generation = authStore.sessionGeneration
    const pending = result.mutateAsync()
    await vi.waitFor(() => expect(request).toHaveBeenCalledOnce())

    expect(authStore.applyTokenIfCurrent(generation, 'token-a', 'refreshed-token')).toBe(true)
    response.resolve('ok')

    await expect(pending).resolves.toBe('saved')
    expect(authStore.sessionGeneration).toBe(generation)
    expect(signal?.aborted).toBe(false)
    expect(onSuccess).toHaveBeenCalledOnce()
    switchAccount()
    // A settled invocation must have released its boundary listener.
    expect(signal?.aborted).toBe(false)
  })

  it('does not reuse an intent after logout and login to the same user', async () => {
    onlineManager.setOnline(false)
    const request = vi.fn(async () => 'saved')
    const { result } = setupComposable(() => useAuthScopedMutation({ mutationFn: request }))
    const failure = rejectionOf(result.mutateAsync())
    await vi.waitFor(() => expect(result.isPaused.value).toBe(true))

    authStore.clearSessionState()
    switchAccount(1)
    onlineManager.setOnline(true)

    expectCancellation(await failure)
    expect(request).not.toHaveBeenCalled()
  })

  it('rechecks the original intent when a failed request is resumed for retry', async () => {
    const response = deferred<string>()
    const request = vi.fn(() => response.promise)
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: request,
      retry: 1,
      retryDelay: 0,
    }))
    const failure = rejectionOf(result.mutateAsync())
    await vi.waitFor(() => expect(request).toHaveBeenCalledOnce())
    onlineManager.setOnline(false)
    response.reject(new Error('Temporary transport failure'))
    await vi.waitFor(() => expect(result.isPaused.value).toBe(true))

    switchAccount()
    onlineManager.setOnline(true)

    expectCancellation(await failure)
    expect(request).toHaveBeenCalledOnce()
  })

  it('preserves ordinary error rollback and settled cleanup with the original context', async () => {
    const variables = { postId: 7 }
    const optimisticContext = { snapshot: ['previous post'] }
    const serverError = new Error('The update was rejected')
    const onError = vi.fn()
    const onSettled = vi.fn()
    const onCleanup = vi.fn()
    const callError = vi.fn()
    const callSettled = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async (_value: typeof variables) => { throw serverError },
      onMutate: () => optimisticContext,
      onError,
      onSettled,
      onCleanup,
    }))

    await expect(result.mutateAsync(variables, {
      onError: callError,
      onSettled: callSettled,
    })).rejects.toBe(serverError)

    for (const callback of [onError, callError]) {
      expect(callback.mock.calls[0]?.slice(0, 3)).toEqual([serverError, variables, optimisticContext])
    }
    for (const callback of [onSettled, callSettled, onCleanup]) {
      expect(callback.mock.calls[0]?.slice(0, 4)).toEqual([undefined, serverError, variables, optimisticContext])
    }
    expect(shouldSuppressGlobalErrorToast(serverError)).toBe(false)
  })

  it('preserves an onMutate failure before optimistic context exists', async () => {
    const setupError = new Error('Snapshot could not be prepared')
    const request = vi.fn(async () => 'saved')
    const onError = vi.fn()
    const onSettled = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: request,
      onMutate: () => { throw setupError },
      onError,
      onSettled,
    }))

    await expect(result.mutateAsync()).rejects.toBe(setupError)

    expect(request).not.toHaveBeenCalled()
    expect(onError.mock.calls[0]?.slice(0, 3)).toEqual([setupError, undefined, undefined])
    expect(onSettled.mock.calls[0]?.slice(0, 4)).toEqual([undefined, setupError, undefined, undefined])
  })

  it('still blocks an old paused mutation after its component has unmounted', async () => {
    onlineManager.setOnline(false)
    const request = vi.fn(async () => 'saved')
    const callSuccess = vi.fn()
    const { result, queryClient, wrapper } = setupComposable(() => useAuthScopedMutation({ mutationFn: request }))
    const failure = rejectionOf(result.mutateAsync(undefined, { onSuccess: callSuccess }))
    await vi.waitFor(() => expect(result.isPaused.value).toBe(true))

    wrapper.unmount()
    switchAccount()
    onlineManager.setOnline(true)
    focusManager.setFocused(true)
    await queryClient.resumePausedMutations()

    expectCancellation(await failure)
    expect(request).not.toHaveBeenCalled()
    expect(callSuccess).not.toHaveBeenCalled()
  })

  it('preserves same-session completion, cache callbacks and cleanup after unmount', async () => {
    const response = deferred<string>()
    const request = vi.fn(() => response.promise)
    const onSuccess = vi.fn()
    const onCleanup = vi.fn()
    const callSuccess = vi.fn()
    const { result, wrapper } = setupComposable(() => useAuthScopedMutation({
      mutationFn: request,
      onSuccess,
      onCleanup,
    }))
    const pending = result.mutateAsync(undefined, { onSuccess: callSuccess })
    await vi.waitFor(() => expect(request).toHaveBeenCalledOnce())

    wrapper.unmount()
    response.resolve('saved')

    await expect(pending).resolves.toBe('saved')
    expect(onSuccess).toHaveBeenCalledOnce()
    expect(onCleanup).toHaveBeenCalledOnce()
    // Vue Query intentionally drops observer callbacks once the observer unmounts.
    expect(callSuccess).not.toHaveBeenCalled()
  })

  it('keeps overlapping void invocations independent when they finish out of order', async () => {
    const firstResponse = deferred<string>()
    const secondResponse = deferred<string>()
    const signals: AbortSignal[] = []
    const onSuccess = vi.fn()
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async (_variables: void, context) => {
        signals.push(context.requestConfig.signal)
        return signals.length === 1 ? firstResponse.promise : secondResponse.promise
      },
      onSuccess,
    }))
    const first = result.mutateAsync()
    const second = result.mutateAsync()
    await vi.waitFor(() => expect(signals).toHaveLength(2))

    secondResponse.resolve('second')
    await expect(second).resolves.toBe('second')
    firstResponse.resolve('first')
    await expect(first).resolves.toBe('first')

    expect(signals[0]).not.toBe(signals[1])
    expect(onSuccess.mock.calls.map(args => args[0])).toEqual(['second', 'first'])
  })

  it('retains reactive options between separate mutation invocations', async () => {
    const revision = ref(1)
    const { result } = setupComposable(() => useAuthScopedMutation(() => {
      const currentRevision = revision.value
      return {
        mutationKey: ['save', currentRevision],
        mutationFn: async (value: number) => `${currentRevision}:${value}`,
      }
    }))

    await expect(result.mutateAsync(7)).resolves.toBe('1:7')
    revision.value = 2
    await nextTick()
    await expect(result.mutateAsync(7)).resolves.toBe('2:7')
  })

  it('preserves request options and joins caller cancellation without allowing session replacement', async () => {
    const externalController = new AbortController()
    const generation = authStore.sessionGeneration
    const { result } = setupComposable(() => useAuthScopedMutation({
      mutationFn: async (_variables: void, context) => {
        const requestConfig = context.withRequestConfig({
          expectedSession: { sessionGeneration: generation + 1, userId: 2 },
          signal: externalController.signal,
          params: { version: 4 },
          headers: { 'X-Example': 'preserved' },
          skipGlobalErrorHandler: true,
        })
        expect(requestConfig).toMatchObject({
          expectedSession: { sessionGeneration: generation, userId: 1 },
          params: { version: 4 },
          headers: { 'X-Example': 'preserved' },
          skipGlobalErrorHandler: true,
        })
        expect(requestConfig.signal?.aborted).toBe(false)
        externalController.abort()
        expect(requestConfig.signal?.aborted).toBe(true)
        return 'merged'
      },
    }))

    await expect(result.mutateAsync()).resolves.toBe('merged')
  })
})
