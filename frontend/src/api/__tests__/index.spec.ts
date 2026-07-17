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
                accessToken: null,
                sessionGeneration: 0,
                fetchUser: async () => false,
                setTokens: () => undefined,
                applyTokenIfCurrent: () => false,
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
        const { persistAccessToken } = await import('@/utils/authTokenStorage')
        persistAccessToken('token-1')
        const config = createApiRequestConfig({ url: '/posts' })

        const result = requestFulfilled(config)

        expect(result.headers.Authorization).toBe('Bearer token-1')
        expect(result._authSessionGeneration).toBe(0)
        expect(result._authAccessToken).toBe('token-1')
    })

    it('does not attach token on auth endpoint except email verification', async () => {
        const { requestFulfilled } = await loadApiModule()
        const { persistAccessToken } = await import('@/utils/authTokenStorage')
        persistAccessToken('token-2')

        const authConfig = createApiRequestConfig({ url: '/auth/login' })
        const verifyConfig = createApiRequestConfig({ url: '/auth/email/verify' })

        requestFulfilled(authConfig)
        requestFulfilled(verifyConfig)

        expect(authConfig.headers.Authorization).toBeUndefined()
        expect(verifyConfig.headers.Authorization).toBe('Bearer token-2')
    })

    it('uses only the request pathname when detecting auth endpoints', async () => {
        const { requestFulfilled } = await loadApiModule()
        const { persistAccessToken } = await import('@/utils/authTokenStorage')
        persistAccessToken('token-3')

        const redirectedPostConfig = createApiRequestConfig({ url: '/posts?redirect=/auth/login' })
        const absoluteAuthConfig = createApiRequestConfig({ url: 'https://api.noviis.kr/auth/login?next=/posts' })

        requestFulfilled(redirectedPostConfig)
        requestFulfilled(absoluteAuthConfig)

        expect(redirectedPostConfig.headers.Authorization).toBe('Bearer token-3')
        expect(absoluteAuthConfig.headers.Authorization).toBeUndefined()
    })

    it('propagates request interceptor rejection', async () => {
        const { requestRejected } = await loadApiModule()
        const requestError = new Error('request failed')

        await expect(requestRejected(requestError)).rejects.toBe(requestError)
    })
})
