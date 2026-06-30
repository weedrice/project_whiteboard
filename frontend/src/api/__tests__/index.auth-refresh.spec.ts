import { beforeEach, describe, expect, it } from 'vitest'

import { getApiIndexMocks, loadApiModule, resetApiIndexTestState } from './apiIndexTestHarness'

const mocks = getApiIndexMocks()

describe('API Interceptors', () => {
    beforeEach(() => {
        resetApiIndexTestState()
    })

    it('does not refresh token when skipAuthRefresh is enabled', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'unauthorized',
            config: { skipAuthRefresh: true, headers: {} },
            response: {
                status: 401,
                data: { message: 'unauthorized' },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAxiosPost).not.toHaveBeenCalled()
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(error.suppressGlobalErrorToast).toBeUndefined()
    })

    it('does not suppress global query toast for retried 401 failures without an axios toast', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'unauthorized after retry',
            config: { _retry: true, headers: {} },
            response: {
                status: 401,
                data: { message: 'unauthorized after retry' },
            },
        } as any

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

        const originalRequest = { headers: {} } as any
        const error = {
            config: originalRequest,
            response: { status: 401 },
        } as any

        const result = await responseRejected(error)

        expect(mocks.mockAxiosPost).toHaveBeenCalledWith('/api/v1/auth/refresh', undefined, { withCredentials: true })
        expect(localStorage.getItem('accessToken')).toBe('new-access')
        expect(localStorage.getItem('refreshToken')).toBeNull()
        expect(authStore.setTokens).toHaveBeenCalledWith('new-access')
        expect(mocks.mockFetchUser).toHaveBeenCalledWith({ skipAuthRefresh: true })
        expect(originalRequest.headers.Authorization).toBe('Bearer new-access')
        expect(mocks.mockApiRequest).toHaveBeenCalledWith(originalRequest)
        expect(result).toEqual({ data: { ok: true } })
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

        const originalRequest = { headers: {} } as any
        const error = {
            config: originalRequest,
            response: { status: 401 },
        } as any

        await expect(responseRejected(error)).rejects.toMatchObject({
            suppressGlobalErrorToast: true,
            isAuthRefreshFailure: true,
            isUserHydrationFailure: true,
        })
        expect(mocks.mockFetchUser).toHaveBeenCalledWith({ skipAuthRefresh: true })
        expect(localStorage.getItem('accessToken')).toBe('new-access')
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

        const request = { headers: {} } as any
        const error = {
            config: request,
            response: { status: 401 },
        } as any

        const result = await responseRejected(error)

        expect(localStorage.getItem('accessToken')).toBe('new-access-without-auth-resolver')
        expect(request.headers.Authorization).toBe('Bearer new-access-without-auth-resolver')
        expect(result).toEqual({ data: { ok: true } })
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

        const request = { headers: {} } as any
        const error = {
            config: request,
            response: { status: 401 },
        } as any

        const result = await responseRejected(error)

        expect(localStorage.getItem('accessToken')).toBe('new-access-auth-throws')
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

        const req1 = { headers: {} } as any
        const req2 = { headers: {} } as any
        const error1 = { config: req1, response: { status: 401 } } as any
        const error2 = { config: req2, response: { status: 401 } } as any

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
        expect(mocks.mockApiRequest).toHaveBeenCalledTimes(2)
    })

    it('handles queued request retries when refreshed access token is null', async () => {
        const { responseRejected } = await loadApiModule()
        mocks.mockApiRequest.mockResolvedValue({ data: { ok: true } })

        let resolveRefresh!: (value: unknown) => void
        const refreshPromise = new Promise((resolve) => {
            resolveRefresh = resolve
        })
        mocks.mockAxiosPost.mockReturnValueOnce(refreshPromise)

        const firstRequest = {} as any
        const queuedRequest = { headers: {} } as any
        const firstError = { config: firstRequest, response: { status: 401 } } as any
        const queuedError = { config: queuedRequest, response: { status: 401 } } as any

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

        await Promise.all([p1, p2])

        expect(firstRequest.headers).toBeUndefined()
        expect(queuedRequest.headers.Authorization).toBeUndefined()
    })

    it('rejects queued requests when refresh fails', async () => {
        const { responseRejected } = await loadApiModule()

        let rejectRefresh!: (reason?: unknown) => void
        const refreshPromise = new Promise((_resolve, reject) => {
            rejectRefresh = reject
        })
        mocks.mockAxiosPost.mockReturnValueOnce(refreshPromise)

        const req1 = { headers: {} } as any
        const req2 = { headers: {} } as any
        const error1 = { config: req1, response: { status: 401 } } as any
        const error2 = { config: req2, response: { status: 401 } } as any
        const refreshError = { response: { status: 401 } }

        const p1 = responseRejected(error1)
        const p2 = responseRejected(error2)
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
        const { responseRejected } = await loadApiModule()
        localStorage.setItem('accessToken', 'stale-access')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = {
            config: { headers: {} },
            response: { status: 401 },
        } as any

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(localStorage.getItem('accessToken')).toBeNull()
        expect(mocks.mockAxiosPost).toHaveBeenCalledWith('/api/v1/auth/refresh', undefined, { withCredentials: true })
    })

    it('rejects refresh when refresh endpoint reports failure', async () => {
        const { responseRejected } = await loadApiModule()
        mocks.mockAxiosPost.mockResolvedValueOnce({
            data: {
                success: false,
                data: {},
            },
        })

        const error = {
            config: { headers: {} },
            response: { status: 401 },
        } as any

        await expect(responseRejected(error)).rejects.toBeInstanceOf(Error)
        expect(mocks.mockApiRequest).not.toHaveBeenCalled()
    })

    it('does not clear tokens for refresh failures with non-auth server errors', async () => {
        const { responseRejected } = await loadApiModule()
        localStorage.setItem('accessToken', 'keep-access')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 500 },
        })

        const error = {
            config: { headers: {} },
            response: { status: 401 },
        } as any

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(localStorage.getItem('accessToken')).toBe('keep-access')
        expect(localStorage.getItem('refreshToken')).toBeNull()
    })

    it('skips redirect when refresh failure happens on login page', async () => {
        const { responseRejected } = await loadApiModule()
        localStorage.setItem('accessToken', 'stale-access')
        history.pushState({}, '', '/login')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = {
            config: { headers: {} },
            response: { status: 401 },
        } as any

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(mocks.mockRouterPush).not.toHaveBeenCalled()
    })

    it('does not redirect when current route does not require auth', async () => {
        const { responseRejected } = await loadApiModule()
        mocks.mockCurrentRoute.value.meta.requiresAuth = false
        localStorage.setItem('accessToken', 'stale-access')
        history.pushState({}, '', '/public')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = {
            config: { headers: {} },
            response: { status: 401 },
        } as any

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(mocks.mockRouterPush).not.toHaveBeenCalled()
    })

    it('handles refresh failure with null auth store resolver', async () => {
        const { responseRejected } = await loadApiModule(undefined, {
            resolveAuthStore: () => null,
        })
        localStorage.setItem('accessToken', 'stale-access')
        mocks.mockAxiosPost.mockRejectedValueOnce({
            response: { status: 401 },
        })

        const error = {
            config: { headers: {} },
            response: { status: 401 },
        } as any

        await expect(responseRejected(error)).rejects.toBeDefined()
        expect(localStorage.getItem('accessToken')).toBeNull()
        expect(localStorage.getItem('refreshToken')).toBeNull()
    })

    it('clears auth state and redirects to login when refresh fails with 401', async () => {
        const { responseRejected, authStore } = await loadApiModule({
            user: { id: 9 },
            accessToken: 'stale-access',
        })
        localStorage.setItem('accessToken', 'stale-access')
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

        const error = {
            config: { headers: {} },
            response: { status: 401 },
        } as any

        const rejected = await responseRejected(error).catch((err: unknown) => err)
        expect(rejected).toBeDefined()
        expect(rejected.suppressGlobalErrorToast).toBe(true)
        expect(rejected.isAuthRefreshFailure).toBe(true)

        expect(localStorage.getItem('accessToken')).toBeNull()
        expect(localStorage.getItem('refreshToken')).toBeNull()
        expect(authStore.clearSessionState).toHaveBeenCalledTimes(1)
        expect(authStore.user).toBeNull()
        expect(authStore.accessToken).toBeNull()
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
        expect(mocks.mockRouterPush).toHaveBeenCalledWith({
            path: '/login',
            query: { redirect: '/boards' },
        })
    })
})
