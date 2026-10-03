import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'
import api, { configureApiStoreResolvers } from '@/api'
import { commonCodeApi } from '@/api/commonCode'
import type { AuthStoreLike } from '@/api/authRefreshSession'
import { configureAuthQueryScope, notifyAuthSessionBoundary } from '@/queryAuthScope'
import { runAuthScopedAction } from '@/utils/authSessionScope'
import { clearStoredAuthTokens, persistAccessToken } from '@/utils/authTokenStorage'
import { createDeferred } from '@/test/async'

vi.mock('@/router', () => ({
  default: { push: vi.fn(), replace: vi.fn(), currentRoute: { value: { meta: {} } } },
}))

const code = { typeCode: 'REPORT_REASON', typeName: 'Reasons' }
const detail = { codeValue: 'SPAM', codeName: 'Spam', sortOrder: 1, isActive: true }
const writes: Array<[string, (config: AxiosRequestConfig) => Promise<unknown>]> = [
  ['create', config => commonCodeApi.create(code, config)],
  ['update', config => commonCodeApi.update(code.typeCode, code, config)],
  ['createDetail', config => commonCodeApi.createDetail(code.typeCode, detail, config)],
  ['updateDetail', config => commonCodeApi.updateDetail(1, detail, config)],
  ['deleteDetail', config => commonCodeApi.deleteDetail(1, config)],
]
const originalAdapter = api.defaults.adapter
const toast = { addToast: vi.fn() }
const transport = vi.fn(async (config: InternalAxiosRequestConfig) => ({
  config, status: 200, statusText: 'OK', headers: {},
  data: { success: true, data: code },
}))
let auth: AuthStoreLike

function switchAccount() {
  auth.sessionGeneration += 1
  auth.user = { userId: 2 }
  auth.accessToken = 'test-account-b'
  persistAccessToken(auth.accessToken)
  notifyAuthSessionBoundary(auth.sessionGeneration)
}

beforeEach(() => {
  vi.clearAllMocks()
  auth = {
    sessionGeneration: 1, user: { userId: 1 }, accessToken: 'test-account-a',
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
})

describe('common-code actions through the shared Axios client', () => {
  it.each(writes)('blocks %s before dispatch if the initiating account changes', async (_name, write) => {
    const success = vi.fn()
    const pending = runAuthScopedAction(auth, async scope => {
      await write(scope.requestConfig)
      scope.runIfCurrent(success)
    })

    // Axios has queued its interceptors but has not sent the request yet.
    switchAccount()

    await expect(pending).resolves.toBeUndefined()
    expect(transport).not.toHaveBeenCalled()
    expect(success).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })

  it('preserves the original identity and body during a same-session token refresh', async () => {
    const pending = runAuthScopedAction(auth, scope => commonCodeApi.update(code.typeCode, code, scope.requestConfig))
    auth.accessToken = 'test-refreshed-token'
    persistAccessToken(auth.accessToken)

    await expect(pending).resolves.toMatchObject({ data: { success: true, data: code } })
    expect(transport).toHaveBeenCalledOnce()
    const config = transport.mock.calls[0]![0]
    expect(config.url).toBe('/common-codes/REPORT_REASON')
    expect(JSON.parse(config.data)).toEqual(code)
    expect(config.expectedSession).toEqual({ sessionGeneration: 1, userId: 1 })
    expect(config.headers.Authorization).toBe('Bearer test-refreshed-token')
  })

  it('does not apply a response that arrives after the account changes', async () => {
    const response = createDeferred<void>()
    transport.mockImplementationOnce(async config => {
      await response.promise
      return { config, status: 200, statusText: 'OK', headers: {}, data: { success: true, data: code } }
    })
    const applyResult = vi.fn()
    const pending = runAuthScopedAction(auth, async scope => {
      await commonCodeApi.update(code.typeCode, code, scope.requestConfig)
      scope.runIfCurrent(applyResult)
    })
    await vi.waitFor(() => expect(transport).toHaveBeenCalledOnce())

    switchAccount()
    response.resolve()

    await expect(pending).resolves.toBeUndefined()
    expect(applyResult).not.toHaveBeenCalled()
    expect(toast.addToast).not.toHaveBeenCalled()
  })
})
