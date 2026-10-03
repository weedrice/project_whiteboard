import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount, type VueWrapper } from '@vue/test-utils'
import { focusManager, onlineManager, QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import type { InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { useComment } from '../useComment'

const auth = vi.hoisted(() => ({
  sessionGeneration: 1,
  user: { userId: 1 },
  accessToken: 'token-a',
  fetchUser: vi.fn(async () => true),
  setTokens: vi.fn(),
  applyTokenIfCurrent: vi.fn(() => true),
  clearSessionState: vi.fn(),
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => auth }))
vi.mock('@/router', () => ({ default: { push: vi.fn(), currentRoute: { value: { meta: {} } } } }))

const originalAdapter = api.defaults.adapter
let client: QueryClient
let wrapper: VueWrapper
let mutation: ReturnType<ReturnType<typeof useComment>['useCreateComment']>
const transport = vi.fn(async (config: InternalAxiosRequestConfig) => ({
  config, status: 201, statusText: 'Created', headers: {},
  data: { success: true, data: 7 },
}))

function switchAccount() {
  auth.sessionGeneration = 2
  auth.user = { userId: 2 }
  auth.accessToken = 'token-b'
  persistAccessToken(auth.accessToken)
  notifyAuthSessionBoundary(2)
}

beforeEach(() => {
  vi.clearAllMocks()
  auth.sessionGeneration = 1
  auth.user = { userId: 1 }
  auth.accessToken = 'token-a'
  persistAccessToken(auth.accessToken)
  configureAuthQueryScope(() => auth.sessionGeneration)
  configureApiStoreResolvers({ resolveAuthStore: () => auth })
  api.defaults.adapter = transport
  onlineManager.setOnline(true)
  focusManager.setFocused(true)
  client = new QueryClient({ defaultOptions: { mutations: { retry: false, gcTime: 0 } } })
  wrapper = mount(defineComponent({
    setup() {
      mutation = useComment().useCreateComment()
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient: client }]] } })
})

afterEach(() => {
  wrapper.unmount()
  client.clear()
  api.defaults.adapter = originalAdapter
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null })
  clearStoredAuthTokens(false)
  onlineManager.setOnline(true)
  focusManager.setFocused(undefined)
})

describe('comment mutations through real Vue Query and Axios', () => {
  it('never dispatches an offline comment after switching accounts', async () => {
    onlineManager.setOnline(false)
    focusManager.setFocused(false)
    const pending = mutation.mutateAsync({ postId: 3, data: { content: 'account A comment' } })
    const cancelled = pending.then(() => null, error => error)
    await vi.waitFor(() => expect(mutation.isPaused.value).toBe(true))
    switchAccount()
    onlineManager.setOnline(true)
    focusManager.setFocused(true)
    expect(await cancelled).toMatchObject({ name: 'AbortError', suppressGlobalErrorToast: true })
    expect(transport).not.toHaveBeenCalled()
    expect(auth.user.userId).toBe(2)
  })

  it('carries the original intent through the API module while preserving the body and URL', async () => {
    await mutation.mutateAsync({ postId: 3, data: { content: 'comment' } })
    expect(transport).toHaveBeenCalledOnce()
    const config = transport.mock.calls[0]![0]
    expect(config.url).toBe('/posts/3/comments')
    expect(JSON.parse(config.data)).toEqual({ content: 'comment' })
    expect(config.expectedSession).toEqual({ sessionGeneration: 1, userId: 1 })
    expect(config.headers.Authorization).toBe('Bearer token-a')
  })

  it('blocks a switch while the request interceptor is resolving the auth store', async () => {
    let resolveStore!: (value: typeof auth) => void
    const resolver = vi.fn(() => new Promise<typeof auth>((resolve) => { resolveStore = resolve }))
    configureApiStoreResolvers({ resolveAuthStore: resolver })
    const pending = mutation.mutateAsync({ postId: 3, data: { content: 'account A comment' } })
    const cancelled = pending.then(() => null, error => error)
    await vi.waitFor(() => expect(resolver).toHaveBeenCalledOnce())
    switchAccount()
    resolveStore(auth)
    expect(await cancelled).toMatchObject({ name: 'AbortError', suppressGlobalErrorToast: true })
    expect(transport).not.toHaveBeenCalled()
  })
})
