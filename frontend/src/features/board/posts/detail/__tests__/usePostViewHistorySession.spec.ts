import { defineComponent, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import type { AuthStoreLike } from '@/api/authRefreshSession'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { usePostViewHistory } from '@/features/board/posts/detail/usePostViewHistory'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { createDeferred } from '@/test/async'

vi.mock('@/router', () => ({
  default: { push: vi.fn(), replace: vi.fn(), currentRoute: { value: { meta: {} } } },
}))

const TestHost = defineComponent({
  setup() {
    return usePostViewHistory({
      postId: ref(15), enabled: ref(true), initialLastReadCommentId: ref(4),
      sessionGeneration: ref(1), userId: ref(7),
    })
  },
  template: '<div />',
})
const originalAdapter = api.defaults.adapter
const toast = { addToast: vi.fn() }
const responseFor = (config: InternalAxiosRequestConfig) => ({
  config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data: null },
})
const transport = vi.fn(async (config: InternalAxiosRequestConfig) => responseFor(config))
let auth: AuthStoreLike
let now = 0

function switchAccount() {
  auth.sessionGeneration += 1
  auth.user = { userId: 8 }
  auth.accessToken = 'test-account-b'
  persistAccessToken(auth.accessToken)
  notifyAuthSessionBoundary(auth.sessionGeneration)
}

beforeEach(() => {
  vi.clearAllMocks()
  now = 0
  vi.spyOn(performance, 'now').mockImplementation(() => now)
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
  Object.defineProperty(document, 'hasFocus', { configurable: true, value: vi.fn(() => true) })
  auth = {
    sessionGeneration: 1, user: { userId: 7 }, accessToken: 'test-account-a',
    fetchUser: vi.fn(async () => true), setTokens: vi.fn(),
    applyTokenIfCurrent: vi.fn(() => true), clearSessionState: vi.fn(),
  }
  persistAccessToken(auth.accessToken!)
  configureAuthQueryScope(() => auth.sessionGeneration)
  configureApiStoreResolvers({ resolveAuthStore: () => auth, resolveToastStore: () => toast })
  api.defaults.adapter = transport
})

afterEach(() => {
  api.defaults.adapter = originalAdapter
  configureAuthQueryScope(() => 0)
  configureApiStoreResolvers({ resolveAuthStore: () => null, resolveToastStore: () => ({ addToast: () => undefined }) })
  clearStoredAuthTokens(false)
  vi.restoreAllMocks()
})

describe('post view history final flush through the shared Axios client', () => {
  it('sends the unsent delta after unmount with a rotated token from the same session', async () => {
    const response = createDeferred<void>()
    transport.mockImplementationOnce(async config => {
      await response.promise
      return responseFor(config)
    })
    const wrapper = mount(TestHost)
    now = 30_000
    wrapper.vm.flush()
    await flushPromises()
    expect(transport).toHaveBeenCalledOnce()
    now = 35_000
    wrapper.vm.setLastReadCommentId(9)
    wrapper.unmount()
    auth.accessToken = 'test-rotated-token'
    persistAccessToken(auth.accessToken)
    response.resolve()
    await flushPromises()

    expect(transport).toHaveBeenCalledTimes(2)
    const finalConfig = transport.mock.calls[1]![0]
    expect(JSON.parse(finalConfig.data)).toEqual({ durationMs: 5_000, lastReadCommentId: 9 })
    expect(finalConfig).toMatchObject({
      url: '/posts/15/history', method: 'put',
      expectedSession: { sessionGeneration: 1, userId: 7 },
      headers: { Authorization: 'Bearer test-rotated-token' },
    })
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it('aborts the unmounted request and never dispatches its queued delta under a new account', async () => {
    const response = createDeferred<void>()
    transport.mockImplementationOnce(async config => {
      await response.promise
      return responseFor(config)
    })
    const wrapper = mount(TestHost)
    now = 30_000
    wrapper.vm.flush()
    await flushPromises()
    now = 35_000
    wrapper.vm.setLastReadCommentId(9)
    wrapper.unmount()
    switchAccount()
    expect(transport.mock.calls[0]![0].signal?.aborted).toBe(true)
    response.resolve()
    await flushPromises()

    expect(transport).toHaveBeenCalledOnce()
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it('cancels a final request before Axios dispatch when the account changes after unmount', async () => {
    const wrapper = mount(TestHost)
    now = 5_000
    wrapper.unmount()
    // The final PUT is queued in Axios, while the Vue component is already gone.
    switchAccount()
    await flushPromises()

    expect(transport).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })
})
