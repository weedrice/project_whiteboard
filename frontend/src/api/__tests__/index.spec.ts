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
})
