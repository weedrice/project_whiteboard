import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, nextTick, reactive } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin, onlineManager, useInfiniteQuery } from '@tanstack/vue-query'
import axios, { type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { createAuthScopedQueryOptions, createReactiveAuthScopedQueryOptions } from '../authScopedQuery'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { configureApiStoreResolvers } from '@/api/apiStoreResolvers'
import { installApiInterceptors } from '@/api/apiInterceptors'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { createDeferred } from '@/test/async'

vi.mock('@/router', () => ({ default: {
  push: vi.fn(), replace: vi.fn(), currentRoute: { value: { meta: {}, fullPath: '/' } },
} }))

const clients: QueryClient[] = []
const wrappers: VueWrapper[] = []
let auth: ReturnType<typeof makeAuth>

function makeAuth() {
  return reactive({
    sessionGeneration: 4,
    user: { userId: 1 } as { userId: number } | null,
    accessToken: 'token-a',
    fetchUser: vi.fn(async () => true), setTokens: vi.fn(), clearSessionState: vi.fn(),
    applyTokenIfCurrent: vi.fn(() => false),
  })
}

function queryClient() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  client.mount()
  clients.push(client)
  return client
}

function response(config: InternalAxiosRequestConfig, data: unknown): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

function changeSession(userId: number | null = 2) {
  auth.sessionGeneration += 1
  auth.user = userId == null ? null : { userId }
  auth.accessToken = userId == null ? '' : 'token-b'
  persistAccessToken(auth.accessToken)
  notifyAuthSessionBoundary(auth.sessionGeneration)
}

beforeEach(() => {
  auth = makeAuth()
  onlineManager.setOnline(true)
  persistAccessToken(auth.accessToken)
  configureAuthQueryScope(() => auth.sessionGeneration, () => auth.user?.userId ?? null)
  configureApiStoreResolvers({ resolveAuthStore: () => auth, resolveToastStore: () => ({ addToast: vi.fn() }) })
})

afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  clients.splice(0).forEach(client => { client.clear(); client.unmount() })
  onlineManager.setOnline(true)
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  clearStoredAuthTokens(false)
})

describe('auth-scoped queries with real Vue Query and Axios', () => {
  it.each([1, null])('pins a known principal across paused reactive query execution (initial user %s)', async initialUserId => {
    auth.user = initialUserId == null ? null : { userId: initialUserId }
    const client = queryClient()
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config, {
      success: true, data: config.expectedSession?.userId,
    }))
    const api = axios.create({ adapter })
    installApiInterceptors(api)
    let query!: ReturnType<typeof useApiQuery<number>>
    onlineManager.setOnline(false)
    wrappers.push(mount(defineComponent({ setup() {
      query = useApiQuery<number>({
        queryKey: ['private', 'paused'], meta: { authScoped: true },
        request: ({ requestConfig }) => api.get('/private', requestConfig),
      })
      return () => null
    } }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }))
    expect(query.fetchStatus.value).toBe('paused')
    // Unknown -> known is hydration; known -> another known user must fail closed
    // even if a caller forgot the normal generation boundary.
    auth.user = { userId: initialUserId == null ? 1 : 2 }
    await nextTick()
    onlineManager.setOnline(true)
    await vi.waitFor(() => expect(query.fetchStatus.value).toBe('idle'))
    expect(query.data.value).toBe(initialUserId == null ? 1 : undefined)
    expect(query.error.value?.name ?? null).toBe(initialUserId == null ? null : 'AbortError')
    expect(adapter).toHaveBeenCalledTimes(initialUserId == null ? 1 : 0)
  })

  it.each([2, 1, null])('discards a late response after switching to user %s', async userId => {
    const gate = createDeferred<void>()
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      await gate.promise
      return response(config, 'old private data')
    })
    const api = axios.create({ adapter })
    installApiInterceptors(api)
    const client = queryClient()
    const options = createAuthScopedQueryOptions(auth, ['private'], async ({ requestConfig }) => (
      (await api.get('/private', requestConfig)).data
    ))
    const result = client.fetchQuery(options).catch(error => error)
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(1))
    expect(adapter.mock.calls[0]![0].expectedSession).toEqual({ sessionGeneration: 4, userId: 1 })

    changeSession(userId)
    expect(adapter.mock.calls[0]![0].signal?.aborted).toBe(true)
    gate.resolve()

    expect(await result).toMatchObject({ name: 'AbortError' })
    expect(client.getQueryData(options.queryKey)).toBeUndefined()
  })

  it('does not send an offline old-key query after resuming under a new account', async () => {
    const client = queryClient()
    const request = vi.fn(async () => 'private')
    const options = createAuthScopedQueryOptions(auth, ['private'], request)
    onlineManager.setOnline(false)
    const result = client.fetchQuery(options).catch(error => error)
    await vi.waitFor(() => expect(client.getQueryState(options.queryKey)?.fetchStatus).toBe('paused'))
    changeSession()
    onlineManager.setOnline(true)
    expect(await result).toMatchObject({ name: 'AbortError' })
    expect(request).not.toHaveBeenCalled()
  })

  it('keeps the original key identity across automatic retries', async () => {
    const client = queryClient()
    const request = vi.fn(async () => { throw new Error('temporary failure') })
    const options = createAuthScopedQueryOptions(auth, ['private'], request)
    const result = client.fetchQuery({ ...options, retry: 1, retryDelay: 20 }).catch(error => error)
    await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(1))
    changeSession()
    expect(await result).toMatchObject({ name: 'AbortError' })
    expect(request).toHaveBeenCalledTimes(1)
  })

  it('allows ordinary access-token rotation and releases settled scope listeners', async () => {
    const client = queryClient()
    const gate = createDeferred<void>()
    let signal!: AbortSignal
    const result = client.fetchQuery(createAuthScopedQueryOptions(auth, ['private'], async context => {
      signal = context.requestConfig.signal
      await gate.promise
      return 'same session'
    }))
    auth.accessToken = 'rotated-token'
    persistAccessToken(auth.accessToken)
    gate.resolve()
    await expect(result).resolves.toBe('same session')
    changeSession()
    expect(signal.aborted).toBe(false)
  })

  it('combines query cancellation with caller cancellation', async () => {
    const client = queryClient()
    const gate = createDeferred<void>()
    const caller = new AbortController()
    let signal!: AbortSignal
    const options = createAuthScopedQueryOptions(auth, ['private'], async context => {
      signal = context.withRequestConfig({ signal: caller.signal }).signal
      await gate.promise
      return 'late'
    })
    const result = client.fetchQuery(options).catch(error => error)
    await vi.waitFor(() => expect(signal).toBeDefined())
    await client.cancelQueries({ queryKey: options.queryKey })
    expect(signal.aborted).toBe(true)
    gate.resolve()
    await result
    expect(client.getQueryData(options.queryKey)).toBeUndefined()
  })

  it('uses the same protected options for API pages and infinite pages while retaining public cache', async () => {
    const client = queryClient()
    client.setQueryData(['public', 'tags'], ['shared'])
    const requests: InternalAxiosRequestConfig[] = []
    const api = axios.create({ adapter: async config => {
      requests.push(config)
      return response(config, { success: true, data: {
        content: [`user-${config.expectedSession?.userId}`], number: 0, size: 10,
        totalElements: 1, totalPages: 1, first: true, last: true, empty: false,
      } })
    } })
    installApiInterceptors(api)
    let pages!: ReturnType<typeof useApiPageQuery<string>>
    let infinite!: ReturnType<typeof useInfiniteQuery>
    wrappers.push(mount(defineComponent({ setup() {
      pages = useApiPageQuery<string>({
        queryKey: ['private', 'page'], meta: { authScoped: true },
        request: ({ requestConfig }) => api.get('/page', requestConfig),
      })
      infinite = useInfiniteQuery({
        ...createReactiveAuthScopedQueryOptions(auth, computed(() => ['private', 'infinite']), async ({ requestConfig }) => (
          (await api.get('/infinite', requestConfig)).data.data
        )),
        initialPageParam: 0, getNextPageParam: () => undefined,
      })
      return () => null
    } }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } }))

    await vi.waitFor(() => expect(pages.data.value?.content).toEqual(['user-1']))
    changeSession()
    await nextTick()
    await vi.waitFor(() => expect(pages.data.value?.content).toEqual(['user-2']))
    await vi.waitFor(() => expect(infinite.data.value).toBeDefined())
    expect(requests.filter(config => config.expectedSession?.userId === 2)).toHaveLength(2)
    expect(client.getQueryData(['session', 5, 'private', 'infinite'])).toMatchObject({ pages: [{ content: ['user-2'] }] })
    expect(client.getQueryData(['public', 'tags'])).toEqual(['shared'])
  })
})
