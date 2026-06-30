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
    resolveAuthStore?: () => any
}

const loadApiModule = async (
    authStoreOverrides?: { user?: unknown; accessToken?: string | null },
    resolverOptions: ApiResolverOptions = {},
) => {
    const module = await import('../index')
    const authStore: {
        user: unknown
        accessToken: string | null
        fetchUser: typeof mocks.mockFetchUser
        setTokens: ReturnType<typeof vi.fn>
        clearSessionState: ReturnType<typeof vi.fn>
    } = {
        user: authStoreOverrides?.user ?? { id: 1 },
        accessToken: authStoreOverrides?.accessToken ?? '',
        fetchUser: mocks.mockFetchUser,
        setTokens: vi.fn((token: string) => {
            authStore.accessToken = token
            localStorage.setItem('accessToken', token)
            localStorage.removeItem('refreshToken')
        }),
        clearSessionState: vi.fn(() => {
            authStore.user = null
            authStore.accessToken = null
            localStorage.removeItem('accessToken')
            localStorage.removeItem('refreshToken')
        }),
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
})
