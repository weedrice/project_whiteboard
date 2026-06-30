import { beforeEach, describe, expect, it } from 'vitest'

import {
    createApiRequestConfig,
    createApiResponse,
    getApiIndexMocks,
    loadApiModule,
    resetApiIndexTestState,
} from './apiIndexTestHarness'

const mocks = getApiIndexMocks()

describe('API Interceptors', () => {
    beforeEach(() => {
        resetApiIndexTestState()
    })

    it('creates axios instance and registers interceptors', async () => {
        const { module } = await loadApiModule()
        expect(module.default).toBeDefined()
        expect(mocks.mockAxiosCreate).toHaveBeenCalled()
        expect(mocks.mockRequestUse).toHaveBeenCalledTimes(1)
        expect(mocks.mockResponseUse).toHaveBeenCalledTimes(1)
    })

    it('supports configuring resolvers independently', async () => {
        const module = await import('../index')

        module.configureApiStoreResolvers({
            resolveToastStore: () => ({ addToast: mocks.mockAddToast }),
        })
        module.configureApiStoreResolvers({
            resolveAuthStore: () => ({
                fetchUser: async () => false,
                setTokens: () => undefined,
                clearSessionState: () => undefined,
            }),
        })

        expect(typeof module.configureApiStoreResolvers).toBe('function')
    })

    it('detects login pathname through a testable helper', async () => {
        const module = await import('../index')

        expect(module.isLoginPathname('/login')).toBe(true)
        expect(module.isLoginPathname('/board/free')).toBe(false)
    })

    it('passes through successful response interceptor result', async () => {
        const { responseFulfilled } = await loadApiModule()
        const response = createApiResponse({ ok: true })

        expect(responseFulfilled(response)).toBe(response)
    })

    it('adds Authorization header in request interceptor for non-auth endpoint', async () => {
        const { requestFulfilled } = await loadApiModule()
        localStorage.setItem('accessToken', 'token-1')
        const config = createApiRequestConfig({ url: '/posts' })

        const result = requestFulfilled(config)

        expect(result.headers.Authorization).toBe('Bearer token-1')
    })

    it('does not attach token on auth endpoint except email verification', async () => {
        const { requestFulfilled } = await loadApiModule()
        localStorage.setItem('accessToken', 'token-2')

        const authConfig = createApiRequestConfig({ url: '/auth/login' })
        const verifyConfig = createApiRequestConfig({ url: '/auth/email/verify' })

        requestFulfilled(authConfig)
        requestFulfilled(verifyConfig)

        expect(authConfig.headers.Authorization).toBeUndefined()
        expect(verifyConfig.headers.Authorization).toBe('Bearer token-2')
    })

    it('propagates request interceptor rejection', async () => {
        const { requestRejected } = await loadApiModule()
        const requestError = new Error('request failed')

        await expect(requestRejected(requestError)).rejects.toBe(requestError)
    })
})
