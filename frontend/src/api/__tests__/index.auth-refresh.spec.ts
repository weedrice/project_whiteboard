import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
    createApiError,
    createApiRequestConfig,
    createApiRequestConfigWithoutHeaders,
    getApiIndexMocks,
    loadApiModule,
    resetApiIndexTestState,
} from './apiIndexTestHarness'
import { closeAuthRefreshCoordinatorForTest } from '@/api/authRefreshCoordinator'

const mocks = getApiIndexMocks()

async function setAccessToken(token: string) {
    const { persistAccessToken } = await import('@/utils/authTokenStorage')
    persistAccessToken(token)
}

async function getAccessToken() {
    const { getStoredAccessToken } = await import('@/utils/authTokenStorage')
    return getStoredAccessToken()
}

describe('API Interceptors', () => {
    beforeEach(() => {
        closeAuthRefreshCoordinatorForTest()
        resetApiIndexTestState()
    })

    it('does not refresh token when skipAuthRefresh is enabled', async () => {
        const { responseRejected } = await loadApiModule()
        const error = createApiError({
            message: 'unauthorized',
            config: { skipAuthRefresh: true, headers: {} },
            response: {
                status: 401,
                data: { message: 'unauthorized' },
            },
        })

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAxiosPost).not.toHaveBeenCalled()
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(error.suppressGlobalErrorToast).toBeUndefined()
    })

    it('does not suppress global query toast for retried 401 failures without an axios toast', async () => {
        const { responseRejected } = await loadApiModule()
        const error = createApiError({
            message: 'unauthorized after retry',
            config: { _retry: true, headers: {} },
            response: {
                status: 401,
                data: { message: 'unauthorized after retry' },
            },
        })

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAxiosPost).not.toHaveBeenCalled()
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(error.suppressGlobalErrorToast).toBeUndefined()
    })

    it('refreshes token and retries original request on 401', async () => {
        const { responseRejected, authStore } = await loadApiModule({ user: { id: 10 }, accessToken: 'old-access' })
        mocks.mockFetchUser.mockResolvedValueOnce(true)
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    accessToken: 'new-access',
                },
            },
        })
        mocks.mockApiRequest.mockResolvedValue({ data: { ok: true } })

        const originalRequest = createApiRequestConfig({ _authAccessToken: 'old-access' })
        const error = createApiError({ config: originalRequest, response: { status: 401 } })

        const result = await responseRejected(error)

        expect(mocks.mockAxiosPost).toHaveBeenCalledWith('/api/v1/auth/refresh', undefined, {
            withCredentials: true,
            timeout: 10000,
            signal: expect.any(AbortSignal),
        })
        expect(await getAccessToken()).toBe('new-access')
        expect(localStorage.getItem('refreshToken')).toBeNull()
        expect(authStore.applyTokenIfCurrent).toHaveBeenCalledWith(0, 'old-access', 'new-access')
        expect(mocks.mockFetchUser).toHaveBeenCalledWith({ skipAuthRefresh: true }, 10)
        expect(originalRequest.headers.Authorization).toBe('Bearer new-access')
        expect(mocks.mockApiRequest).toHaveBeenCalledWith(originalRequest)
        expect(result).toEqual({ data: { ok: true } })
    })

    it('does not restore a session when refresh completes after logout', async () => {
        const { responseRejected, authStore } = await loadApiModule({ user: { id: 10 }, accessToken: 'old-access' })
        let resolveRefresh!: (value: unknown) => void
        mocks.mockAxiosPost.mockReturnValueOnce(new Promise((resolve) => {
            resolveRefresh = resolve
        }))

        const request = createApiRequestConfig({ _authAccessToken: 'old-access' })
        const refreshResult = responseRejected(createApiError({ config: request, response: { status: 401 } }))
        await vi.waitFor(() => expect(mocks.mockAxiosPost).toHaveBeenCalledTimes(1))
        authStore.sessionGeneration += 1
        authStore.accessToken = null

        resolveRefresh({
            data: { success: true, data: { accessToken: 'late-access' } },
        })

        await expect(refreshResult).rejects.toMatchObject({ name: 'AuthSessionChangedError' })
        expect(authStore.accessToken).toBeNull()
        expect(mocks.mockApiRequest).not.toHaveBeenCalled()
    })

    it('does not replay original or queued requests when the account changes during user hydration', async () => {
        const { responseRejected, authStore } = await loadApiModule({ user: { id: 10 }, accessToken: 'old-access' })
        let resolveHydration!: (value: boolean) => void
        mocks.mockFetchUser.mockReturnValueOnce(new Promise((resolve) => {
            resolveHydration = resolve
        }))
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: { success: true, data: { accessToken: 'refreshed-a' } },
        })

        const firstRequest = createApiRequestConfig({ _authAccessToken: 'old-access' })
        const queuedRequest = createApiRequestConfig({ _authAccessToken: 'old-access' })
        const firstResult = responseRejected(createApiError({ config: firstRequest, response: { status: 401 } }))
        const queuedResult = responseRejected(createApiError({ config: queuedRequest, response: { status: 401 } }))

        await vi.waitFor(() => expect(mocks.mockFetchUser).toHaveBeenCalledTimes(1))
        authStore.sessionGeneration += 1
        authStore.accessToken = 'account-b'
        authStore.user = { id: 20 }
        resolveHydration(true)

        await expect(firstResult).rejects.toMatchObject({ name: 'AuthSessionChangedError' })
        await expect(queuedResult).rejects.toMatchObject({ name: 'AuthSessionChangedError' })
        expect(mocks.mockApiRequest).not.toHaveBeenCalled()
        expect(authStore.accessToken).toBe('account-b')
    })

    it('rejects refresh when user hydration fails after refresh succeeds', async () => {
        const { responseRejected } = await loadApiModule({ user: { id: 10 }, accessToken: 'old-access' })
        mocks.mockFetchUser.mockResolvedValueOnce(false)
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    accessToken: 'new-access',
                },
            },
        })

        const originalRequest = createApiRequestConfig({ _authAccessToken: 'old-access' })
        const error = createApiError({ config: originalRequest, response: { status: 401 } })

        await expect(responseRejected(error)).rejects.toMatchObject({
            suppressGlobalErrorToast: true,
            isAuthRefreshFailure: true,
            isUserHydrationFailure: true,
        })
        expect(mocks.mockFetchUser).toHaveBeenCalledWith({ skipAuthRefresh: true }, 10)
        expect(await getAccessToken()).toBe('new-access')
        expect(mocks.mockApiRequest).not.toHaveBeenCalledWith(originalRequest)
    })

    it('refreshes and retries when auth store resolver is missing', async () => {
        const { responseRejected } = await loadApiModule(undefined, { configureResolvers: false })
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    accessToken: 'new-access-without-auth-resolver',
                },
            },
        })
        mocks.mockApiRequest.mockResolvedValue({ data: { ok: true } })

        const request = createApiRequestConfig()
        const error = createApiError({ config: request, response: { status: 401 } })

        const result = await responseRejected(error)

        expect(await getAccessToken()).toBe('new-access-without-auth-resolver')
        expect(request.headers.Authorization).toBe('Bearer new-access-without-auth-resolver')
        expect(result).toEqual({ data: { ok: true } })
    })

    it('does not refresh or replay a delayed request after the authenticated session changes', async () => {
        const { responseRejected, authStore } = await loadApiModule({ user: { id: 20 }, accessToken: 'account-b' })
        const delayedAccountARequest = createApiRequestConfig({
            method: 'post',
            data: { privateAction: 'from-account-a' },
            _authSessionGeneration: 0,
            _authAccessToken: 'account-a',
        })
        authStore.sessionGeneration = 1

        await expect(responseRejected(createApiError({
            config: delayedAccountARequest,
            response: { status: 401 },
        }))).rejects.toMatchObject({
            name: 'AuthSessionChangedError',
            suppressGlobalErrorToast: true,
        })

        expect(mocks.mockAxiosPost).not.toHaveBeenCalled()
        expect(mocks.mockApiRequest).not.toHaveBeenCalled()
    })

    it('refreshes and retries when auth store resolver throws', async () => {
        const { responseRejected } = await loadApiModule(undefined, {
            resolveAuthStore: () => {
                throw new Error('auth resolver failure')
            },
        })
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: {
                success: true,
                data: {
                    accessToken: 'new-access-auth-throws',
                },
            },
        })
        mocks.mockApiRequest.mockResolvedValue({ data: { ok: true } })

        const request = createApiRequestConfig()
        const error = createApiError({ config: request, response: { status: 401 } })

        const result = await responseRejected(error)

        expect(await getAccessToken()).toBe('new-access-auth-throws')
        expect(request.headers.Authorization).toBe('Bearer new-access-auth-throws')
        expect(result).toEqual({ data: { ok: true } })
    })

    it('handles queued requests while refresh is in progress', async () => {
        const { responseRejected } = await loadApiModule()
        mocks.mockApiRequest.mockResolvedValue({ data: { ok: true } })

        let resolveRefresh!: (value: unknown) => void
        const refreshPromise = new Promise((resolve) => {
            resolveRefresh = resolve
        })
        mocks.mockAxiosPost.mockReturnValueOnce(refreshPromise)

        const req1 = createApiRequestConfig()
        const req2 = createApiRequestConfig()
        const error1 = createApiError({ config: req1, response: { status: 401 } })
        const error2 = createApiError({ config: req2, response: { status: 401 } })

        const p1 = responseRejected(error1)
        const p2 = responseRejected(error2)

        resolveRefresh({
            data: {
                success: true,
                data: {
                    accessToken: 'queued-access',
                },
            },
        })

        const [r1, r2] = await Promise.all([p1, p2])

        expect(r1).toEqual({ data: { ok: true } })
        expect(r2).toEqual({ data: { ok: true } })
        expect(req1.headers.Authorization).toBe('Bearer queued-access')
        expect(req2.headers.Authorization).toBe('Bearer queued-access')
        expect(req1._retry).toBe(true)
        expect(req2._retry).toBe(true)
        expect(mocks.mockApiRequest).toHaveBeenCalledTimes(2)
    })

    it('rejects queued requests when refreshed access token is null', async () => {
        const { responseRejected } = await loadApiModule()
        mocks.mockApiRequest.mockResolvedValue({ data: { ok: true } })

        let resolveRefresh!: (value: unknown) => void
        const refreshPromise = new Promise((resolve) => {
            resolveRefresh = resolve
        })
        mocks.mockAxiosPost.mockReturnValueOnce(refreshPromise)

        const firstRequest = createApiRequestConfigWithoutHeaders()
        const queuedRequest = createApiRequestConfig()
        const firstError = createApiError({ config: firstRequest, response: { status: 401 } })
        const queuedError = createApiError({ config: queuedRequest, response: { status: 401 } })

        const p1 = responseRejected(firstError)
        const p2 = responseRejected(queuedError)

        resolveRefresh({
            data: {
                success: true,
                data: {
                    accessToken: null,
                },
            },
        })

        await expect(p1).rejects.toMatchObject({
            suppressGlobalErrorToast: true,
            isAuthRefreshFailure: true,
        })
        await expect(p2).rejects.toMatchObject({
            suppressGlobalErrorToast: true,
            isAuthRefreshFailure: true,
        })

        expect(firstRequest.headers).toBeUndefined()
        expect(queuedRequest.headers.Authorization).toBeUndefined()
        expect(mocks.mockApiRequest).not.toHaveBeenCalled()
    })

    it('rejects queued requests when refresh fails', async () => {
        const { responseRejected } = await loadApiModule()

        let rejectRefresh!: (reason?: unknown) => void
        const refreshPromise = new Promise((_resolve, reject) => {
            rejectRefresh = reject
        })
        mocks.mockAxiosPost.mockReturnValueOnce(refreshPromise)

        const req1 = createApiRequestConfig()
        const req2 = createApiRequestConfig()
        const error1 = createApiError({ config: req1, response: { status: 401 } })
        const error2 = createApiError({ config: req2, response: { status: 401 } })
        const refreshError = { response: { status: 401 } }

        const p1 = responseRejected(error1)
        const p2 = responseRejected(error2)
        await vi.waitFor(() => expect(mocks.mockAxiosPost).toHaveBeenCalledTimes(1))
        rejectRefresh(refreshError)

        await expect(p1).rejects.toBe(refreshError)
        await expect(p2).rejects.toBe(refreshError)
        expect(mocks.mockAddToast).toHaveBeenCalledTimes(1)
        expect(mocks.mockAddToast).toHaveBeenCalledWith(
            'common.messages.sessionExpired',
            'warning',
            3000,
            'top-center',
        )
    })

    it('attempts refresh without local refresh token state', async () => {
        const { responseRejected } = await loadApiModule({ accessToken: 'stale-access' })
        await setAccessToken('stale-access')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = createApiError({
            config: createApiRequestConfig({ _authAccessToken: 'stale-access' }),
            response: { status: 401 },
        })

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(await getAccessToken()).toBeNull()
        expect(mocks.mockAxiosPost).toHaveBeenCalledWith('/api/v1/auth/refresh', undefined, {
            withCredentials: true,
            timeout: 10000,
            signal: expect.any(AbortSignal),
        })
    })

    it('short-circuits refresh attempts during the failure cooldown', async () => {
        const { responseRejected } = await loadApiModule()
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        await expect(responseRejected(createApiError({
            config: createApiRequestConfig(),
            response: { status: 401 },
        }))).rejects.toMatchObject({
            suppressGlobalErrorToast: true,
            isAuthRefreshFailure: true,
        })

        mocks.mockAxiosPost.mockClear()

        await expect(responseRejected(createApiError({
            config: createApiRequestConfig(),
            response: { status: 401 },
        }))).rejects.toMatchObject({
            suppressGlobalErrorToast: true,
            isAuthRefreshFailure: true,
        })
        expect(mocks.mockAxiosPost).not.toHaveBeenCalled()
    })

    it('does not carry a refresh failure cooldown into a new session generation', async () => {
        const { authStore, responseRejected } = await loadApiModule({ accessToken: 'account-a' })
        await setAccessToken('account-a')
        mocks.mockAxiosPost.mockRejectedValueOnce({ response: { status: 500 } })
        await expect(responseRejected(createApiError({
            config: createApiRequestConfig({
                _authSessionGeneration: 0,
                _authAccessToken: 'account-a',
            }),
            response: { status: 401 },
        }))).rejects.toBeDefined()

        authStore.sessionGeneration = 1
        authStore.accessToken = 'account-b'
        await setAccessToken('account-b')
        mocks.mockAxiosPost.mockClear()
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: { success: true, data: { accessToken: 'account-b-refreshed' } },
        })
        mocks.mockApiRequest.mockResolvedValueOnce({ data: 'retried' })

        await expect(responseRejected(createApiError({
            config: createApiRequestConfig({
                _authSessionGeneration: 1,
                _authAccessToken: 'account-b',
            }),
            response: { status: 401 },
        }))).resolves.toEqual({ data: 'retried' })
        expect(mocks.mockAxiosPost).toHaveBeenCalledTimes(1)
    })

    it('rejects refresh when refresh endpoint reports failure', async () => {
        const { responseRejected } = await loadApiModule()
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: {
                success: false,
                data: {},
            },
        })

        const error = createApiError({
            config: createApiRequestConfig(),
            response: { status: 401 },
        })

        await expect(responseRejected(error)).rejects.toBeInstanceOf(Error)
        expect(mocks.mockApiRequest).not.toHaveBeenCalled()
    })

    it('does not clear tokens for refresh failures with non-auth server errors', async () => {
        const { responseRejected } = await loadApiModule({ accessToken: 'keep-access' })
        await setAccessToken('keep-access')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 500 },
        })

        const error = createApiError({
            config: createApiRequestConfig({ _authAccessToken: 'keep-access' }),
            response: { status: 401 },
        })

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(await getAccessToken()).toBe('keep-access')
        expect(localStorage.getItem('refreshToken')).toBeNull()
    })

    it('preserves the session when refresh fails without an HTTP response', async () => {
        const { responseRejected, authStore } = await loadApiModule({ accessToken: 'keep-access' })
        await setAccessToken('keep-access')
        mocks.mockAxiosPost.mockRejectedValueOnce(new Error('offline'))

        await expect(responseRejected(createApiError({
            config: createApiRequestConfig({ _authAccessToken: 'keep-access' }),
            response: { status: 401 },
        }))).rejects.toBeDefined()

        expect(authStore.clearSessionState).not.toHaveBeenCalled()
        expect(await getAccessToken()).toBe('keep-access')
        expect(mocks.mockRouterReplace).not.toHaveBeenCalled()
    })

    it('skips redirect when refresh failure happens on login page', async () => {
        const { responseRejected } = await loadApiModule({ accessToken: 'stale-access' })
        await setAccessToken('stale-access')
        history.pushState({}, '', '/login')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = createApiError({
            config: createApiRequestConfig({ _authAccessToken: 'stale-access' }),
            response: { status: 401 },
        })

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(mocks.mockRouterPush).not.toHaveBeenCalled()
    })

    it('does not redirect when current route does not require auth', async () => {
        const { responseRejected } = await loadApiModule({ accessToken: 'stale-access' })
        mocks.mockCurrentRoute.value.meta.requiresAuth = false
        await setAccessToken('stale-access')
        history.pushState({}, '', '/public')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = createApiError({
            config: createApiRequestConfig({ _authAccessToken: 'stale-access' }),
            response: { status: 401 },
        })

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(mocks.mockRouterPush).not.toHaveBeenCalled()
    })

    it('handles refresh failure with null auth store resolver', async () => {
        const { responseRejected } = await loadApiModule(undefined, {
            resolveAuthStore: () => null,
        })
        await setAccessToken('stale-access')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = createApiError({
            config: createApiRequestConfig({ _authAccessToken: 'stale-access' }),
            response: { status: 401 },
        })

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(await getAccessToken()).toBeNull()
        expect(localStorage.getItem('refreshToken')).toBeNull()
    })

    it('clears auth state and redirects to login when refresh fails with 401', async () => {
        const { responseRejected, authStore } = await loadApiModule({
            user: { id: 9 },
            accessToken: 'stale-access',
        })
        await setAccessToken('stale-access')
        history.pushState({}, '', '/boards')
        mocks.mockCurrentRoute.value.fullPath = '/boards'
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: {
                status: 401,
                data: {
                    error: {
                        code: 'A001',
                        message: '유효하지 않은 리프레시 토큰입니다.',
                    },
                },
            },
        })

        const error = createApiError({
            config: createApiRequestConfig({ _authAccessToken: 'stale-access' }),
            response: { status: 401 },
        })

        const rejected = await responseRejected(error).catch((err: unknown) => err)
        expect(rejected).toBeDefined()
        expect(rejected.suppressGlobalErrorToast).toBe(true)
        expect(rejected.isAuthRefreshFailure).toBe(true)

        expect(authStore.clearSessionState).toHaveBeenCalledTimes(1)
        expect(authStore.user).toBeNull()
        expect(authStore.accessToken).toBeNull()
        expect(await getAccessToken()).toBeNull()
        expect(localStorage.getItem('refreshToken')).toBeNull()
        expect(mocks.mockAddToast).toHaveBeenCalledTimes(1)
        expect(mocks.mockAddToast).toHaveBeenCalledWith(
            'common.messages.sessionExpired',
            'warning',
            3000,
            'top-center',
        )
        expect(mocks.mockAddToast).not.toHaveBeenCalledWith(
            '유효하지 않은 리프레시 토큰입니다.',
            'warning',
            3000,
            'top-center',
        )
        expect(mocks.mockRouterReplace).toHaveBeenCalledWith({
            path: '/login',
            query: { redirect: '/boards' },
        })
    })
})
