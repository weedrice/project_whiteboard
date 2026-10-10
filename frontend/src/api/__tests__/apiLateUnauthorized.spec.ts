import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { flushPromises } from '@vue/test-utils'
import { installApiInterceptors } from '@/api/apiInterceptors'
import { configureApiStoreResolvers } from '@/api/apiStoreResolvers'
import { resetAuthRefreshFailureCooldownForTest } from '@/api/apiRefreshRetry'
import { cancelPendingAuthRefresh, type AuthStoreLike } from '@/api/authRefreshSession'
import { closeAuthRefreshCoordinatorForTest } from '@/api/authRefreshCoordinator'
import { configureAuthQueryScope } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { createDeferred } from '@/test/async'

vi.mock('@/router', () => ({ default: {
  push: vi.fn(), replace: vi.fn(),
  currentRoute: { value: { meta: { requiresAuth: true }, fullPath: '/protected' } },
} }))

const expectedSession = { sessionGeneration: 4, userId: 1 }
let auth: AuthStoreLike
const fetchUser = vi.fn(async () => true)

function response(config: InternalAxiosRequestConfig, data: unknown, status = 200): AxiosResponse {
  return { data, status, statusText: status === 200 ? 'OK' : 'Error', headers: {}, config }
}

function unauthorized(config: InternalAxiosRequestConfig) {
  return new AxiosError('Expired token', 'ERR_BAD_REQUEST', config, undefined, response(config, {}, 401))
}

async function settled<T>(promise: Promise<T>) {
  try {
    return { value: await promise, error: undefined }
  } catch (error) {
    return { value: undefined, error }
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  closeAuthRefreshCoordinatorForTest()
  cancelPendingAuthRefresh()
  resetAuthRefreshFailureCooldownForTest()
  vi.stubGlobal('navigator', {
    ...navigator,
    locks: { request: async (_name: string, callback: () => Promise<string>) => callback() },
  })
  vi.stubGlobal('BroadcastChannel', undefined)
  auth = {
    user: { userId: 1 }, accessToken: 'old-token', sessionGeneration: 4,
    fetchUser, setTokens: vi.fn(), clearSessionState: vi.fn(),
    applyTokenIfCurrent: (generation, previous, token) => {
      if (auth.sessionGeneration !== generation || auth.accessToken !== previous) return false
      auth.accessToken = token
      persistAccessToken(token)
      return true
    },
  }
  persistAccessToken('old-token')
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  configureApiStoreResolvers({ resolveAuthStore: () => auth, resolveToastStore: () => ({ addToast: vi.fn() }) })
})

afterEach(() => {
  closeAuthRefreshCoordinatorForTest()
  cancelPendingAuthRefresh()
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  clearStoredAuthTokens(false)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

// Real Axios, interceptors, queue and coordinator; only the server transport
// and browser Web Locks entry point are replaced.
async function startRefresh(config: AxiosRequestConfig = {}, rejectReplay = false) {
  const refreshGate = createDeferred<AxiosResponse>()
  const lateUnauthorized = createDeferred<void>()
  const hydration = createDeferred<boolean>()
  fetchUser.mockReturnValue(hydration.promise)
  const refresh = vi.spyOn(axios, 'post').mockReturnValue(refreshGate.promise)
  const adapter = vi.fn(async (request: InternalAxiosRequestConfig) => {
    if (!request._retry) {
      if (request.url === '/comments') await lateUnauthorized.promise
      throw unauthorized(request)
    }
    if (rejectReplay && request.url === '/comments') throw unauthorized(request)
    return response(request, 'saved')
  })
  const api = axios.create({ baseURL: '/api/v1', adapter })
  installApiInterceptors(api)
  const controller = new AbortController()
  const first = settled(api.get('/posts/1', { expectedSession }))
  const second = settled(api.post('/comments', { content: 'keep this action' }, {
    expectedSession, signal: controller.signal, ...config,
  }))
  await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(2))
  await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())
  const delayedConfig = adapter.mock.calls.find(([request]) => request.url === '/comments')![0]
  const applyRefresh = async () => {
    refreshGate.resolve(response(adapter.mock.calls[0]![0], {
      success: true, data: { accessToken: 'new-token' },
    }))
    await vi.waitFor(() => expect(fetchUser).toHaveBeenCalledOnce())
  }
  return { adapter, refresh, hydration, lateUnauthorized, first, second, delayedConfig, controller, applyRefresh }
}

describe('late unauthorized responses after same-session token rotation', () => {
  it.each(['after hydration', 'during hydration'])('replays a valid mutation without another refresh: %s', async phase => {
    const flow = await startRefresh()
    expect(flow.adapter.mock.calls.map(([config]) => config.headers.Authorization))
      .toEqual(['Bearer old-token', 'Bearer old-token'])
    await flow.applyRefresh()
    expect(auth.accessToken).toBe('new-token')

    if (phase === 'after hydration') {
      flow.hydration.resolve(true)
      await flow.first
    }
    flow.lateUnauthorized.resolve()
    await flushPromises()
    const transportsBeforeHydration = flow.adapter.mock.calls.length
    flow.hydration.resolve(true)

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.value).toMatchObject({ data: 'saved' })
    expect(second.error).toBeUndefined()
    expect(second.value).toMatchObject({ data: 'saved' })
    expect(transportsBeforeHydration).toBe(phase === 'during hydration' ? 2 : 4)
    expect(flow.adapter).toHaveBeenCalledTimes(4)
    expect(flow.adapter.mock.calls.find(([config]) => config.url === '/comments' && config._retry)?.[0])
      .toMatchObject({ expectedSession, headers: { Authorization: 'Bearer new-token' } })
    expect(flow.refresh).toHaveBeenCalledOnce()
    expect(auth.sessionGeneration).toBe(expectedSession.sessionGeneration)
    expect(auth.user?.userId).toBe(expectedSession.userId)
    expect(auth.clearSessionState).not.toHaveBeenCalled()
  })

  it('also handles a legacy request with a complete same-session transport stamp', async () => {
    const flow = await startRefresh({ expectedSession: undefined })
    await flow.applyRefresh()
    flow.hydration.resolve(true)
    await flow.first
    flow.lateUnauthorized.resolve()

    expect((await flow.second).value).toMatchObject({ data: 'saved' })
    expect(flow.adapter).toHaveBeenCalledTimes(4)
    expect(flow.refresh).toHaveBeenCalledOnce()
  })

  it.each(['generation', 'principal'])('rejects an old intent after changing the %s', async boundary => {
    const flow = await startRefresh()
    await flow.applyRefresh()
    flow.hydration.resolve(true)
    await flow.first
    if (boundary === 'generation') auth.sessionGeneration += 1
    auth.user = { userId: 2 }
    auth.accessToken = 'other-account-token'
    persistAccessToken('other-account-token')
    flow.lateUnauthorized.resolve()

    expect((await flow.second).error).toMatchObject({ name: 'AbortError' })
    expect(flow.adapter).toHaveBeenCalledTimes(3)
    expect(flow.refresh).toHaveBeenCalledOnce()
    expect(auth.accessToken).toBe('other-account-token')
  })

  it.each(['generation', 'token', 'anonymous token'])('does not reuse a newer token when the original stamp lacks a valid %s', async missing => {
    const flow = await startRefresh({ expectedSession: undefined })
    await flow.applyRefresh()
    flow.hydration.resolve(true)
    await flow.first
    if (missing === 'generation') delete flow.delayedConfig._authSessionGeneration
    if (missing === 'token') delete flow.delayedConfig._authAccessToken
    if (missing === 'anonymous token') flow.delayedConfig._authAccessToken = null
    flow.lateUnauthorized.resolve()

    expect((await flow.second).error).toMatchObject({ name: 'AuthSessionChangedError' })
    expect(flow.adapter).toHaveBeenCalledTimes(3)
    expect(flow.refresh).toHaveBeenCalledOnce()
  })

  it('does not substitute current credentials for explicit old-session cleanup', async () => {
    const flow = await startRefresh({
      preserveAuthHeader: true,
      headers: { Authorization: 'Bearer cleanup-token' },
    })
    await flow.applyRefresh()
    flow.hydration.resolve(true)
    await flow.first
    flow.lateUnauthorized.resolve()

    expect((await flow.second).error).toMatchObject({ name: 'AuthSessionChangedError' })
    expect(flow.delayedConfig.headers.Authorization).toBe('Bearer cleanup-token')
    expect(flow.adapter).toHaveBeenCalledTimes(3)
    expect(flow.refresh).toHaveBeenCalledOnce()
  })

  it('respects cancellation while a late 401 is waiting for hydration', async () => {
    const flow = await startRefresh()
    await flow.applyRefresh()
    flow.lateUnauthorized.resolve()
    await flushPromises()
    flow.controller.abort()
    flow.hydration.resolve(true)

    const [first, second] = await Promise.all([flow.first, flow.second])
    expect(first.value).toMatchObject({ data: 'saved' })
    expect(second.error).toMatchObject({ name: 'AbortError' })
    expect(flow.adapter).toHaveBeenCalledTimes(3)
    expect(flow.refresh).toHaveBeenCalledOnce()
  })

  it('does not loop if replaying with the current token also returns 401', async () => {
    const flow = await startRefresh({}, true)
    await flow.applyRefresh()
    flow.hydration.resolve(true)
    await flow.first
    flow.lateUnauthorized.resolve()

    expect((await flow.second).error).toMatchObject({ response: { status: 401 } })
    expect(flow.adapter).toHaveBeenCalledTimes(4)
    expect(flow.refresh).toHaveBeenCalledOnce()
  })

  it.each(['before failure', 'after failure'])('does not bypass failed hydration when the late 401 arrives %s', async phase => {
    const flow = await startRefresh()
    await flow.applyRefresh()
    if (phase === 'before failure') {
      flow.lateUnauthorized.resolve()
      await flushPromises()
    }
    flow.hydration.resolve(false)
    const first = await flow.first
    flow.lateUnauthorized.resolve()

    expect(first.error).toMatchObject({ isUserHydrationFailure: true })
    expect((await flow.second).error).toMatchObject({ isAuthRefreshFailure: true })
    expect(flow.adapter).toHaveBeenCalledTimes(2)
    expect(flow.refresh).toHaveBeenCalledOnce()
    expect(auth.accessToken).toBe('new-token')
    expect(auth.clearSessionState).not.toHaveBeenCalled()
  })
})
