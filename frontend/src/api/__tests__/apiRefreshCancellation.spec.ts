import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { flushPromises } from '@vue/test-utils'
import { installApiInterceptors } from '@/api/apiInterceptors'
import { configureApiStoreResolvers } from '@/api/apiStoreResolvers'
import { resetAuthRefreshFailureCooldownForTest } from '@/api/apiRefreshRetry'
import {
  cancelPendingAuthRefresh,
  resetSessionExpiredToastDebounce,
  type AuthStoreLike,
} from '@/api/authRefreshSession'
import { closeAuthRefreshCoordinatorForTest } from '@/api/authRefreshCoordinator'
import { configureAuthQueryScope } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { createDeferred } from '@/test/async'
import router from '@/router'

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    replace: vi.fn(),
    currentRoute: { value: { meta: { requiresAuth: true }, fullPath: '/protected' } },
  },
}))

function response(config: InternalAxiosRequestConfig, data: unknown, status = 200): AxiosResponse {
  return { data, status, statusText: status === 200 ? 'OK' : 'Error', headers: {}, config }
}

async function settle<T>(promise: Promise<T>) {
  try {
    return { value: await promise, error: undefined }
  } catch (error) {
    return { value: undefined, error }
  }
}

const expectedSession = { sessionGeneration: 4, userId: 1 }
let authStore: AuthStoreLike
const fetchUser = vi.fn(async () => true)
const toast = { addToast: vi.fn() }
const clearSessionState = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
  fetchUser.mockResolvedValue(true)
  closeAuthRefreshCoordinatorForTest()
  cancelPendingAuthRefresh()
  resetAuthRefreshFailureCooldownForTest()
  resetSessionExpiredToastDebounce()
  vi.stubGlobal('navigator', {
    ...navigator,
    locks: { request: async (_name: string, callback: () => Promise<string>) => callback() },
  })
  vi.stubGlobal('BroadcastChannel', undefined)
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
  clearSessionState.mockImplementation(() => {
    authStore.sessionGeneration += 1
    authStore.user = null
    authStore.accessToken = null
    clearStoredAuthTokens(false)
  })
  persistAccessToken('token-a')
  configureAuthQueryScope(() => authStore.sessionGeneration, () => authStore.user?.userId ?? null)
  configureApiStoreResolvers({ resolveAuthStore: () => authStore, resolveToastStore: () => toast })
})

afterEach(() => {
  closeAuthRefreshCoordinatorForTest()
  cancelPendingAuthRefresh()
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null, resolveToastStore: () => ({ addToast: () => undefined }) })
  clearStoredAuthTokens(false)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

// Axios, interceptors, the refresh queue and coordinator are real. Only remote
// transport and the browser Web Locks entry point are replaced.
async function startSharedRefresh() {
  const refreshGate = createDeferred<AxiosResponse>()
  const refresh = vi.spyOn(axios, 'post').mockReturnValue(refreshGate.promise)
  const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
    if (!config._retry) {
      throw new AxiosError('Expired access token', 'ERR_BAD_REQUEST', config, undefined, response(config, {}, 401))
    }
    return response(config, 'saved after refresh')
  })
  const api = axios.create({ baseURL: '/api/v1', adapter })
  installApiInterceptors(api)
  const firstController = new AbortController()
  const first = settle(api.get('/posts/1', { expectedSession, signal: firstController.signal }))
  await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())

  const secondController = new AbortController()
  const second = settle(api.post('/comments', { content: 'still wanted' }, {
    expectedSession,
    signal: secondController.signal,
  }))
  await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(2))
  await flushPromises()

  const resolveRefresh = () => refreshGate.resolve(response(adapter.mock.calls[0]![0], {
    success: true, data: { accessToken: 'refreshed-token' },
  }))
  return { adapter, refresh, refreshGate, resolveRefresh, firstController, secondController, first, second }
}

describe('shared refresh and request cancellation', () => {
  it.each(['refresh response', 'user hydration'])('continues other requests when the initiator is cancelled during %s', async phase => {
    const hydration = createDeferred<boolean>()
    fetchUser.mockReturnValue(hydration.promise)
    const flow = await startSharedRefresh()
    if (phase === 'user hydration') {
      flow.resolveRefresh()
      await vi.waitUntil(() => fetchUser.mock.calls.length === 1)
    }

    flow.firstController.abort()
    flow.resolveRefresh()
    hydration.resolve(true)

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.error).toMatchObject({ name: 'AbortError' })
    expect(second.error).toBeUndefined()
    expect(second.value).toMatchObject({ data: 'saved after refresh' })
    expect(authStore.accessToken).toBe('refreshed-token')
    expect(fetchUser).toHaveBeenCalledExactlyOnceWith({ skipAuthRefresh: true }, expectedSession.userId)
    expect(flow.adapter).toHaveBeenCalledTimes(3)
    expect(flow.adapter.mock.calls[2]![0]).toMatchObject({ url: '/comments', expectedSession })
    expect(flow.adapter.mock.calls[2]![0].headers.Authorization).toBe('Bearer refreshed-token')
    expect(flow.refresh).toHaveBeenCalledOnce()
    expect(clearSessionState).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it('only suppresses the cancelled waiter while the initiator still replays', async () => {
    const flow = await startSharedRefresh()
    flow.secondController.abort()
    flow.resolveRefresh()

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.value).toMatchObject({ data: 'saved after refresh' })
    expect(second.error).toMatchObject({ name: 'AbortError' })
    expect(flow.adapter).toHaveBeenCalledTimes(3)
    expect(flow.adapter.mock.calls[2]![0].url).toBe('/posts/1')
    expect(authStore.accessToken).toBe('refreshed-token')
  })

  it.each(['refresh response', 'user hydration'])('rejects all old requests if the account changes during %s', async phase => {
    const hydration = createDeferred<boolean>()
    fetchUser.mockReturnValue(hydration.promise)
    const flow = await startSharedRefresh()
    if (phase === 'user hydration') {
      flow.resolveRefresh()
      await vi.waitUntil(() => fetchUser.mock.calls.length === 1)
    }
    authStore.sessionGeneration += 1
    authStore.user = { userId: 2 }
    authStore.accessToken = 'token-b'
    persistAccessToken('token-b')
    flow.resolveRefresh()
    hydration.resolve(true)

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.error).toMatchObject({ name: 'AbortError' })
    expect(second.error).toMatchObject({ name: 'AbortError' })
    expect(flow.adapter).toHaveBeenCalledTimes(2)
    expect(authStore.accessToken).toBe('token-b')
    expect(clearSessionState).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it.each([401, 500])('preserves shared refresh failure policy for HTTP %s after initiator cancellation', async status => {
    const flow = await startSharedRefresh()
    flow.firstController.abort()
    const config = flow.adapter.mock.calls[0]![0]
    const error = new AxiosError('Refresh failed', 'ERR_BAD_RESPONSE', config, undefined, response(config, {}, status))
    flow.refreshGate.reject(error)

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.error).toBe(error)
    expect(second.error).toBe(error)
    expect(flow.adapter).toHaveBeenCalledTimes(2)
    expect(fetchUser).not.toHaveBeenCalled()
    expect(clearSessionState).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
    expect(router.replace).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
    expect(authStore.accessToken).toBe(status === 401 ? null : 'token-a')
  })

  it('still rejects a changed principal even if the generation did not advance', async () => {
    const flow = await startSharedRefresh()
    authStore.user = { userId: 2 }
    flow.resolveRefresh()

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.error).toMatchObject({ name: 'AbortError' })
    expect(second.error).toMatchObject({ name: 'AbortError' })
    expect(flow.adapter).toHaveBeenCalledTimes(2)
    expect(authStore.accessToken).toBe('token-a')
    expect(fetchUser).not.toHaveBeenCalled()
    expect(clearSessionState).not.toHaveBeenCalled()
  })

  it('keeps hydration failure shared even when the initiating request was cancelled', async () => {
    const hydration = createDeferred<boolean>()
    fetchUser.mockReturnValue(hydration.promise)
    const flow = await startSharedRefresh()
    flow.resolveRefresh()
    await vi.waitFor(() => expect(fetchUser).toHaveBeenCalledOnce())
    flow.firstController.abort()
    hydration.resolve(false)

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.error).toMatchObject({ isUserHydrationFailure: true, isAuthRefreshFailure: true })
    expect(second.error).toBe(first.error)
    expect(flow.adapter).toHaveBeenCalledTimes(2)
    expect(authStore.accessToken).toBe('refreshed-token')
    expect(clearSessionState).not.toHaveBeenCalled()
  })
})
