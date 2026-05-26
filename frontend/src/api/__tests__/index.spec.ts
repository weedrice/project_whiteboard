import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
    const mockAddToast = vi.fn()
    const mockTranslate = vi.fn((key: string) => key)
    const mockFetchUser = vi.fn()
    const mockAxiosPost = vi.fn()
    const mockRequestUse = vi.fn()
    const mockResponseUse = vi.fn()
    const mockApiRequest = vi.fn()
    const mockRouterPush = vi.fn()
    const mockCurrentRoute = {
        value: {
            meta: { requiresAuth: true },
            fullPath: '/protected',
        },
    }
    const mockAxiosInstance = Object.assign(mockApiRequest, {
        interceptors: {
            request: { use: mockRequestUse },
            response: { use: mockResponseUse },
        },
        defaults: { baseURL: '/api/v1' },
    })
    const mockAxiosCreate = vi.fn(() => mockAxiosInstance)

    return {
        mockAddToast,
        mockTranslate,
        mockFetchUser,
        mockAxiosPost,
        mockRequestUse,
        mockResponseUse,
        mockApiRequest,
        mockRouterPush,
        mockCurrentRoute,
        mockAxiosInstance,
        mockAxiosCreate,
    }
})

vi.mock('axios', async (importOriginal) => {
    const actual = await importOriginal<typeof import('axios')>()
    return {
        ...actual,
        default: {
            ...(actual as unknown as { default?: Record<string, unknown> }).default,
            create: mocks.mockAxiosCreate,
            post: mocks.mockAxiosPost,
        },
        create: mocks.mockAxiosCreate,
        post: mocks.mockAxiosPost,
    }
})

vi.mock('@/i18n', () => ({
    default: {
        global: {
            t: mocks.mockTranslate,
        },
    },
}))

vi.mock('@/router', () => ({
    default: {
        push: mocks.mockRouterPush,
        currentRoute: mocks.mockCurrentRoute,
    },
}))

type ApiResolverOptions = {
    configureResolvers?: boolean
    resolveToastStore?: () => { addToast: typeof mocks.mockAddToast } | Promise<{ addToast: typeof mocks.mockAddToast }>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    resolveAuthStore?: () => any
}

const loadApiModule = async (
    authStoreOverrides?: { user?: unknown; accessToken?: string | null },
    resolverOptions: ApiResolverOptions = {},
) => {
    const module = await import('../index')
    const authStore = {
        user: authStoreOverrides?.user ?? { id: 1 },
        accessToken: authStoreOverrides?.accessToken ?? '',
        fetchUser: mocks.mockFetchUser,
    }

    if (resolverOptions.configureResolvers !== false) {
        module.configureApiStoreResolvers({
            resolveToastStore: resolverOptions.resolveToastStore ?? (() => ({
                addToast: mocks.mockAddToast,
            })),
            resolveAuthStore: resolverOptions.resolveAuthStore ?? (() => authStore),
        })
    }

    const requestFulfilled = mocks.mockRequestUse.mock.calls[0]?.[0]
    const requestRejected = mocks.mockRequestUse.mock.calls[0]?.[1]
    const responseFulfilled = mocks.mockResponseUse.mock.calls[0]?.[0]
    const responseRejected = mocks.mockResponseUse.mock.calls[0]?.[1]

    return {
        module,
        authStore,
        requestFulfilled,
        requestRejected,
        responseFulfilled,
        responseRejected,
    }
}

describe('API Interceptors', () => {
    beforeEach(() => {
        vi.resetModules()
        vi.clearAllMocks()
        mocks.mockFetchUser.mockResolvedValue(true)
        mocks.mockTranslate.mockImplementation((key: string) => key)
        localStorage.clear()
        history.pushState({}, '', '/')
        mocks.mockCurrentRoute.value.meta.requiresAuth = true
        mocks.mockCurrentRoute.value.fullPath = '/protected'
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
                user: null,
                accessToken: null,
                fetchUser: async () => false,
            }),
        })

        expect(typeof module.configureApiStoreResolvers).toBe('function')
    })

    it('passes through successful response interceptor result', async () => {
        const { responseFulfilled } = await loadApiModule()
        const response = { data: { ok: true } } as any

        expect(responseFulfilled(response)).toBe(response)
    })

    it('adds Authorization header in request interceptor for non-auth endpoint', async () => {
        const { requestFulfilled } = await loadApiModule()
        localStorage.setItem('accessToken', 'token-1')
        const config = { url: '/posts', headers: {} } as any

        const result = requestFulfilled(config)

        expect(result.headers.Authorization).toBe('Bearer token-1')
    })

    it('does not attach token on auth endpoint except email verification', async () => {
        const { requestFulfilled } = await loadApiModule()
        localStorage.setItem('accessToken', 'token-2')

        const authConfig = { url: '/auth/login', headers: {} } as any
        const verifyConfig = { url: '/auth/email/verify', headers: {} } as any

        requestFulfilled(authConfig)
        requestFulfilled(verifyConfig)

        expect(authConfig.headers.Authorization).toBeUndefined()
        expect(verifyConfig.headers.Authorization).toBe('Bearer token-2')
    })

    it('propagates request interceptor rejection', async () => {
        const { requestRejected } = await loadApiModule()
        const requestError = new Error('request failed') as any

        await expect(requestRejected(requestError)).rejects.toBe(requestError)
    })

    it('falls back to noop toast store when resolver is not configured', async () => {
        const { responseRejected } = await loadApiModule(undefined, { configureResolvers: false })
        const error = {
            message: 'forbidden',
            config: { headers: {} },
            response: {
                status: 403,
                data: { message: 'forbidden' },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(error.suppressGlobalErrorToast).toBeUndefined()
    })

    it('falls back to noop toast store when resolver throws', async () => {
        const { responseRejected } = await loadApiModule(undefined, {
            resolveToastStore: () => {
                throw new Error('toast resolver failure')
            },
        })
        const error = {
            message: 'not found',
            config: { headers: {} },
            response: {
                status: 404,
                data: { message: 'missing' },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(error.suppressGlobalErrorToast).toBeUndefined()
    })

    it('handles response errors without config safely', async () => {
        const { responseRejected } = await loadApiModule()
        const errorWithoutConfig = {
            message: 'Network Error',
            request: {},
        } as any

        await expect(responseRejected(errorWithoutConfig)).rejects.toBe(errorWithoutConfig)
        expect(errorWithoutConfig.suppressGlobalErrorToast).toBe(true)
        expect(mocks.mockAddToast).toHaveBeenCalledWith(
            'common.messages.networkRetry',
            'error',
            5000,
            'top-center',
        )
    })

    it('handles redirectOnError requests', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'fallback message',
            config: { redirectOnError: true, headers: {} },
            response: {
                status: 404,
                data: { message: 'not found' },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockRouterPush).toHaveBeenCalledWith({
            name: 'error',
            query: { status: '404', message: 'not found' },
        })
    })

    it('handles redirectOnError fallback when response payload is missing', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'redirect fallback',
            config: { redirectOnError: true, headers: {} },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockRouterPush).toHaveBeenCalledWith({
            name: 'error',
            query: { status: '500', message: 'redirect fallback' },
        })
    })

    it('skips global error handling when configured', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'bad request',
            config: { skipGlobalErrorHandler: true, headers: {} },
            response: {
                status: 400,
                data: { message: 'bad request' },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(error.suppressGlobalErrorToast).toBeUndefined()
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
    })

    it('ignores canceled requests before global handling and auth refresh', async () => {
        const { responseRejected } = await loadApiModule()
        const canceledError = {
            name: 'CanceledError',
            code: 'ERR_CANCELED',
            message: 'canceled',
            config: { headers: {} },
            request: {},
        } as any

        await expect(responseRejected(canceledError)).rejects.toBe(canceledError)
        expect(canceledError.suppressGlobalErrorToast).toBeUndefined()
        expect(mocks.mockAddToast).not.toHaveBeenCalled()
        expect(mocks.mockAxiosPost).not.toHaveBeenCalled()
        expect(mocks.mockRouterPush).not.toHaveBeenCalled()
    })

    it('shows validation message for 400 errors with details', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'validation failed',
            config: { headers: {} },
            response: {
                status: 400,
                data: {
                    error: {
                        message: 'validation failed',
                        details: {
                            title: ['title is required'],
                        },
                    },
                },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(error.suppressGlobalErrorToast).toBe(true)
        expect(mocks.mockAddToast).toHaveBeenCalledWith(
            'title is required',
            'error',
            3000,
            'top-center',
        )
    })

    it.each([
        { status: 400, message: 'bad request', expected: 'bad request' },
        { status: 403, message: 'forbidden', expected: 'forbidden' },
        { status: 404, message: 'not found', expected: 'not found' },
        { status: 500, message: 'server', expected: 'common.messages.serverError' },
        { status: 418, message: 'teapot', expected: 'teapot' },
    ])('handles status %s in global error handler', async ({ status, message, expected }) => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message,
            config: { headers: {} },
            response: {
                status,
                data: { message },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith(expected, 'error', 3000, 'top-center')
    })

    it('uses translation fallback branches when translated strings are empty', async () => {
        mocks.mockTranslate.mockImplementation(() => '')
        const { responseRejected } = await loadApiModule()

        const validationError = {
            config: { headers: {} },
            response: {
                status: 400,
                data: {
                    error: {
                        details: {},
                    },
                },
            },
        } as any
        await expect(responseRejected(validationError)).rejects.toBe(validationError)

        const forbiddenError = {
            config: { headers: {} },
            response: {
                status: 403,
                data: {},
            },
        } as any
        await expect(responseRejected(forbiddenError)).rejects.toBe(forbiddenError)

        const notFoundError = {
            config: { headers: {} },
            response: {
                status: 404,
                data: {},
            },
        } as any
        await expect(responseRejected(notFoundError)).rejects.toBe(notFoundError)

        const unknownStatusError = {
            config: { headers: {} },
            response: {
                status: 418,
                data: {},
            },
        } as any
        await expect(responseRejected(unknownStatusError)).rejects.toBe(unknownStatusError)

        const retryableNetworkError = {
            code: 'ERR_NETWORK',
            config: { headers: {} },
            request: {},
        } as any
        await expect(responseRejected(retryableNetworkError)).rejects.toBe(retryableNetworkError)

        const nonRetryableNetworkError = {
            code: 'ERR_BAD_RESPONSE',
            config: { headers: {} },
            request: {},
        } as any
        await expect(responseRejected(nonRetryableNetworkError)).rejects.toBe(nonRetryableNetworkError)

        const setupError = {
            config: { headers: {} },
        } as any
        await expect(responseRejected(setupError)).rejects.toBe(setupError)

        expect(mocks.mockAddToast).toHaveBeenCalledWith(
            'Network error. Please check your connection and try again.',
            'error',
            5000,
            'top-center',
        )
    })

    it('uses badRequest translation fallback in 400 branch without details', async () => {
        mocks.mockTranslate.mockImplementation((key: string) => (key === 'common.messages.serverError' ? '' : key))
        const { responseRejected } = await loadApiModule()

        const error = {
            config: { headers: {} },
            response: {
                status: 400,
                data: {},
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith('common.messages.badRequest', 'error', 3000, 'top-center')
    })

    it('falls back to top-level message when nested error message is missing', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'request failed',
            config: { headers: {} },
            response: {
                status: 403,
                data: {
                    message: 'top-level-forbidden',
                    error: { code: 'AUTH' },
                },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith('top-level-forbidden', 'error', 3000, 'top-center')
    })

    it('falls back to axios error.message when response has no message fields', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'from-axios-error',
            config: { headers: {} },
            response: {
                status: 418,
                data: {},
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith('from-axios-error', 'error', 3000, 'top-center')
    })

    it('handles 400 validation details object without field entries', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'request failed',
            config: { headers: {} },
            response: {
                status: 400,
                data: {
                    error: {
                        message: 'validation-empty',
                        details: {},
                    },
                },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith('validation-empty', 'error', 3000, 'top-center')
    })

    it('falls back to the response message when 400 details are not validation arrays', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'request failed',
            config: { headers: {} },
            response: {
                status: 400,
                data: {
                    error: {
                        message: 'validation-malformed',
                        details: {
                            title: 'required',
                        },
                    },
                },
            },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith('validation-malformed', 'error', 3000, 'top-center')
    })

    it('shows normalized network message when request error is not retryable', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'socket closed',
            code: 'ERR_BAD_RESPONSE',
            config: { headers: {} },
            request: {},
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith('socket closed', 'error', 3000, 'top-center')
    })

    it('shows request setup message for pre-request failures', async () => {
        const { responseRejected } = await loadApiModule()
        const error = {
            message: 'setup failed',
            config: { headers: {} },
        } as any

        await expect(responseRejected(error)).rejects.toBe(error)
        expect(mocks.mockAddToast).toHaveBeenCalledWith('setup failed', 'error', 3000, 'top-center')
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
        const { responseRejected } = await loadApiModule({ user: { id: 10 }, accessToken: 'old-access' })
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
        expect(authStore.user).toBeNull()
        expect(authStore.accessToken).toBe('')
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
