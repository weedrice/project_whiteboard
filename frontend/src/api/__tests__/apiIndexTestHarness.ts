import { vi } from 'vitest'
import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

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

export const getApiIndexMocks = () => mocks

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

export const loadApiModule = async (
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

type MutableHeaders = Record<string, string | undefined>

export type TestApiRequestConfig = InternalAxiosRequestConfig & {
    headers: MutableHeaders
}

export type TestApiError = AxiosError & {
    suppressGlobalErrorToast?: boolean
    isAuthRefreshFailure?: boolean
    isUserHydrationFailure?: boolean
}

export const createApiRequestConfig = (
    overrides: Partial<InternalAxiosRequestConfig> = {},
): TestApiRequestConfig => {
    const { headers, ...rest } = overrides

    return {
        ...rest,
        headers: {
            ...(headers as MutableHeaders | undefined),
        },
    } as TestApiRequestConfig
}

export const createApiRequestConfigWithoutHeaders = (): InternalAxiosRequestConfig => (
    {} as InternalAxiosRequestConfig
)

export const createApiResponse = <T>(data: T): AxiosResponse<T> => ({
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: createApiRequestConfig(),
})

export const createApiError = ({
    message,
    config,
    response,
    request,
    code,
    name,
}: {
    message?: string
    config?: Partial<InternalAxiosRequestConfig>
    response?: {
        status?: number
        data?: unknown
    }
    request?: unknown
    code?: string
    name?: string
} = {}): TestApiError => ({
    ...(message === undefined ? {} : { message }),
    name: name ?? 'AxiosError',
    code,
    config: config as InternalAxiosRequestConfig | undefined,
    request,
    response: response
        ? {
            data: response.data,
            status: response.status ?? 500,
            statusText: '',
            headers: {},
            config: createApiRequestConfig(),
        }
        : undefined,
    isAxiosError: true,
    toJSON: () => ({}),
} as TestApiError)

export const resetApiIndexTestState = () => {
    vi.resetModules()
    vi.clearAllMocks()
    mocks.mockFetchUser.mockResolvedValue(true)
    mocks.mockTranslate.mockImplementation((key: string) => key)
    localStorage.clear()
    history.pushState({}, '', '/')
    mocks.mockCurrentRoute.value.meta.requiresAuth = true
    mocks.mockCurrentRoute.value.fullPath = '/protected'
}
