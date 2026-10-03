import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios, { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { installApiInterceptors } from '@/api/apiInterceptors'
import { configureApiStoreResolvers } from '@/api/apiStoreResolvers'
import { retryAfterRefresh, resetAuthRefreshFailureCooldownForTest } from '@/api/apiRefreshRetry'
import { cancelPendingAuthRefresh, type AuthStoreLike } from '@/api/authRefreshSession'
import { coordinateAuthRefresh } from '@/api/authRefreshCoordinator'
import { configureAuthQueryScope } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { shouldSuppressGlobalErrorToast } from '@/utils/errorHandler'
import router from '@/router'

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    replace: vi.fn(),
    currentRoute: { value: { meta: { requiresAuth: true }, fullPath: '/protected' } },
  },
}))

// The Axios instance, interceptors and refresh queue are real. Only the browser
// cross-tab coordinator and remote refresh transport are replaced.
vi.mock('@/api/authRefreshCoordinator', () => ({ coordinateAuthRefresh: vi.fn() }))

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  const promise = new Promise<T>((resolvePromise) => { resolve = resolvePromise })
  return { promise, resolve }
}

function response(config: InternalAxiosRequestConfig, data: unknown, status = 200): AxiosResponse {
  return { data, status, statusText: status === 200 ? 'OK' : 'Error', headers: {}, config }
}

function httpError(config: InternalAxiosRequestConfig, status: number) {
  return new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, undefined, response(config, {}, status))
}

async function expectCancellation(promise: Promise<unknown>) {
  const error = await promise.then(
    () => { throw new Error('Expected request cancellation') },
    value => value,
  )
  expect(error).toMatchObject({ name: 'AbortError' })
  expect(shouldSuppressGlobalErrorToast(error)).toBe(true)
}

const expectedSession = { sessionGeneration: 4, userId: 1 }
let authStore: AuthStoreLike
const toast = { addToast: vi.fn() }
const fetchUser = vi.fn(async () => true)
const clearSessionState = vi.fn()

function switchAccount() {
  authStore.sessionGeneration += 1
  authStore.user = { userId: 2 }
  authStore.accessToken = 'token-b'
  persistAccessToken('token-b')
}

function createClient(adapter: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>) {
  const api = axios.create({ baseURL: '/api/v1', adapter })
  installApiInterceptors(api)
  return api
}

function originalRequest(): InternalAxiosRequestConfig {
  return {
    url: '/users/me/settings',
    method: 'patch',
    headers: new AxiosHeaders({ Authorization: 'Bearer token-a' }),
    expectedSession: { ...expectedSession },
    _authSessionGeneration: expectedSession.sessionGeneration,
    _authAccessToken: 'token-a',
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  fetchUser.mockResolvedValue(true)
  authStore = {
    user: { userId: 1 },
    accessToken: 'token-a',
    sessionGeneration: expectedSession.sessionGeneration,
    fetchUser,
    setTokens: vi.fn(),
    applyTokenIfCurrent: (generation, previousToken, token) => {
      if (authStore.sessionGeneration !== generation || authStore.accessToken !== previousToken) return false
      authStore.accessToken = token
      persistAccessToken(token)
      return true
    },
    clearSessionState,
  }
  persistAccessToken('token-a')
  configureAuthQueryScope(() => authStore.sessionGeneration)
  configureApiStoreResolvers({ resolveAuthStore: () => authStore, resolveToastStore: () => toast })
  vi.mocked(coordinateAuthRefresh).mockImplementation(async operation => operation(new AbortController().signal))
  cancelPendingAuthRefresh()
  resetAuthRefreshFailureCooldownForTest()
})

afterEach(() => {
  vi.restoreAllMocks()
  cancelPendingAuthRefresh()
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null, resolveToastStore: () => ({ addToast: () => undefined }) })
  clearStoredAuthTokens(false)
})

describe('expected auth session at the Axios boundary', () => {
  it('blocks a request before transport when the account changed while resolving the store', async () => {
    const gate = deferred<AuthStoreLike>()
    const resolveStore = vi.fn(() => gate.promise)
    configureApiStoreResolvers({ resolveAuthStore: resolveStore })
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))
    const api = createClient(adapter)
    const cancelled = expectCancellation(api.patch('/users/me/settings', {}, { expectedSession }))
    await vi.waitFor(() => expect(resolveStore).toHaveBeenCalled())

    switchAccount()
    gate.resolve(authStore)

    await cancelled
    expect(adapter).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it.each(['unavailable', 'throws'] as const)('fails closed when the session resolver is %s', async (failure) => {
    configureApiStoreResolvers({ resolveAuthStore: () => {
      if (failure === 'throws') throw new Error('Store unavailable')
      return null
    } })
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))

    await expectCancellation(createClient(adapter).post('/posts', {}, { expectedSession }))

    expect(adapter).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it('checks the user identity even when a store has the same generation', async () => {
    authStore.user = { userId: 2 }
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))

    await expectCancellation(createClient(adapter).post('/posts', {}, { expectedSession }))

    expect(adapter).not.toHaveBeenCalled()
  })

  it('keeps legacy requests and explicit old-token cleanup headers unchanged', async () => {
    switchAccount()
    const resolveStore = vi.fn(() => null)
    configureApiStoreResolvers({ resolveAuthStore: resolveStore })
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'removed'))

    await expect(createClient(adapter).delete('/users/me/push-subscriptions', {
      headers: { Authorization: 'Bearer old-session-cleanup-token' },
      preserveAuthHeader: true,
      skipAuthRefresh: true,
    })).resolves.toMatchObject({ data: 'removed' })

    expect(adapter.mock.calls[0]?.[0].headers.Authorization).toBe('Bearer old-session-cleanup-token')
    expect(resolveStore).not.toHaveBeenCalled()
  })

  it('rejects a successful old-session response before returning it to callers', async () => {
    const gate = deferred<void>()
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      await gate.promise
      return response(config, 'old private result')
    })
    const cancelled = expectCancellation(createClient(adapter).post('/posts', {}, { expectedSession }))
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve()

    await cancelled
    expect(adapter.mock.calls[0]?.[0].headers.Authorization).toBe('Bearer token-a')
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it('rechecks after the toast resolver before showing an error or navigating', async () => {
    const gate = deferred<typeof toast>()
    const resolveToast = vi.fn(() => gate.promise)
    configureApiStoreResolvers({ resolveToastStore: resolveToast })
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => { throw httpError(config, 500) })
    const requestConfig = originalRequest()
    requestConfig.redirectOnError = true
    const cancelled = expectCancellation(createClient(adapter).post('/posts', {}, requestConfig))
    await vi.waitFor(() => expect(resolveToast).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve(toast)

    await cancelled
    expect(router.push).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it('checks a delayed refresh resolver before beginning a refresh', async () => {
    const gate = deferred<AuthStoreLike>()
    const resolveStore = vi.fn(() => gate.promise)
    configureApiStoreResolvers({ resolveAuthStore: resolveStore })
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))
    const refresh = vi.spyOn(axios, 'post').mockRejectedValue(new Error('Unexpected refresh transport'))
    const cancelled = expectCancellation(retryAfterRefresh(createClient(adapter), originalRequest()))
    await vi.waitFor(() => expect(resolveStore).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve(authStore)

    await cancelled
    expect(refresh).not.toHaveBeenCalled()
    expect(adapter).not.toHaveBeenCalled()
  })

  it('checks again when the shared coordinator is finally ready to send refresh', async () => {
    const gate = deferred<void>()
    vi.mocked(coordinateAuthRefresh).mockImplementation(async (operation) => {
      await gate.promise
      return operation(new AbortController().signal)
    })
    const refresh = vi.spyOn(axios, 'post').mockRejectedValue(new Error('Unexpected refresh transport'))
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))
    const cancelled = expectCancellation(retryAfterRefresh(createClient(adapter), originalRequest()))
    await vi.waitFor(() => expect(coordinateAuthRefresh).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve()

    await cancelled
    expect(refresh).not.toHaveBeenCalled()
    expect(adapter).not.toHaveBeenCalled()
    expect(clearSessionState).not.toHaveBeenCalled()
  })

  it('does not apply a refresh token or replay after the account changes during refresh', async () => {
    const gate = deferred<AxiosResponse>()
    const refresh = vi.spyOn(axios, 'post').mockReturnValue(gate.promise)
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))
    const cancelled = expectCancellation(retryAfterRefresh(createClient(adapter), originalRequest()))
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve(response(originalRequest(), { success: true, data: { accessToken: 'stale-refreshed-token' } }))

    await cancelled
    expect(authStore.accessToken).toBe('token-b')
    expect(fetchUser).not.toHaveBeenCalled()
    expect(adapter).not.toHaveBeenCalled()
    expect(clearSessionState).not.toHaveBeenCalled()
  })

  it('does not replay after a session change while refreshed user hydration is waiting', async () => {
    const gate = deferred<boolean>()
    fetchUser.mockReturnValue(gate.promise)
    vi.spyOn(axios, 'post').mockResolvedValue(response(originalRequest(), {
      success: true, data: { accessToken: 'refreshed-token' },
    }))
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))
    const cancelled = expectCancellation(retryAfterRefresh(createClient(adapter), originalRequest()))
    await vi.waitFor(() => expect(fetchUser).toHaveBeenCalledOnce())

    switchAccount()
    gate.resolve(true)

    await cancelled
    expect(adapter).not.toHaveBeenCalled()
    expect(clearSessionState).not.toHaveBeenCalled()
  })

  it('cancels queued refresh requests without replaying either request under another account', async () => {
    const gate = deferred<AxiosResponse>()
    const refresh = vi.spyOn(axios, 'post').mockReturnValue(gate.promise)
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, 'saved'))
    const api = createClient(adapter)
    const firstCancelled = expectCancellation(retryAfterRefresh(api, originalRequest()))
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())
    const queuedCancelled = expectCancellation(retryAfterRefresh(api, originalRequest()))
    // Let the second invocation reach the actual refresh queue.
    await Promise.resolve()
    await Promise.resolve()

    switchAccount()
    gate.resolve(response(originalRequest(), { success: true, data: { accessToken: 'stale-token' } }))

    await Promise.all([firstCancelled, queuedCancelled])
    expect(refresh).toHaveBeenCalledOnce()
    expect(adapter).not.toHaveBeenCalled()
    expect(clearSessionState).not.toHaveBeenCalled()
  })

  it('preserves expected identity through a successful same-session 401 refresh and replay', async () => {
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue(response(originalRequest(), {
      success: true, data: { accessToken: 'refreshed-token' },
    }))
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      if (!config._retry) throw httpError(config, 401)
      return response(config, 'saved after refresh')
    })

    await expect(createClient(adapter).patch('/users/me/settings', {}, { expectedSession }))
      .resolves.toMatchObject({ data: 'saved after refresh' })

    expect(refresh).toHaveBeenCalledOnce()
    expect(adapter).toHaveBeenCalledTimes(2)
    expect(adapter.mock.calls[1]?.[0]).toMatchObject({
      expectedSession,
      _authSessionGeneration: expectedSession.sessionGeneration,
      _authAccessToken: 'refreshed-token',
    })
    expect(adapter.mock.calls[1]?.[0].headers.Authorization).toBe('Bearer refreshed-token')
    expect(authStore.sessionGeneration).toBe(expectedSession.sessionGeneration)
    expect(toast.addToast).not.toHaveBeenCalled()
  })
})
