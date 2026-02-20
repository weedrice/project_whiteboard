import { describe, it, expect, vi, beforeEach } from 'vitest'

const mocks = vi.hoisted(() => {
    const mockAddToast = vi.fn()
    const mockFetchUser = vi.fn()
    const mockAxiosPost = vi.fn()
    const mockRequestUse = vi.fn()
    const mockResponseUse = vi.fn()
    const mockAxiosInstance = {
        interceptors: {
            request: { use: mockRequestUse },
            response: { use: mockResponseUse }
        },
        defaults: { baseURL: '/api/v1' }
    }
    const mockAxiosCreate = vi.fn(() => mockAxiosInstance)

    return {
        mockAddToast,
        mockFetchUser,
        mockAxiosPost,
        mockRequestUse,
        mockResponseUse,
        mockAxiosInstance,
        mockAxiosCreate
    }
})

vi.mock('axios', async (importOriginal) => {
    const actual = await importOriginal<typeof import('axios')>()
    return {
        ...actual,
        default: {
            ...(actual as unknown as { default?: Record<string, unknown> }).default,
            create: mocks.mockAxiosCreate,
            post: mocks.mockAxiosPost
        },
        create: mocks.mockAxiosCreate,
        post: mocks.mockAxiosPost
    }
})

vi.mock('@/i18n', () => ({
    default: {
        global: {
            t: (key: string) => key
        }
    }
}))

vi.mock('@/router', () => ({
    default: {
        push: vi.fn()
    }
}))

const registerStoreResolvers = (configureApiStoreResolvers: (resolvers: {
    resolveToastStore?: () => { addToast: typeof mocks.mockAddToast }
    resolveAuthStore?: () => { user: null; accessToken: string; fetchUser: typeof mocks.mockFetchUser }
}) => void): void => {
    configureApiStoreResolvers({
        resolveToastStore: () => ({
            addToast: mocks.mockAddToast
        }),
        resolveAuthStore: () => ({
            user: null,
            accessToken: '',
            fetchUser: mocks.mockFetchUser
        })
    })
}

describe('API Interceptors', () => {
    beforeEach(() => {
        vi.resetModules()
        vi.clearAllMocks()
        localStorage.clear()
    })

    it('should be defined', async () => {
        const { default: api, configureApiStoreResolvers } = await import('../index')
        registerStoreResolvers(configureApiStoreResolvers)
        expect(api).toBeDefined()
        expect(mocks.mockAxiosCreate).toHaveBeenCalled()
    })

    it('handles errors without config safely', async () => {
        const { configureApiStoreResolvers } = await import('../index')
        registerStoreResolvers(configureApiStoreResolvers)

        const rejectedHandler = mocks.mockResponseUse.mock.calls[0]?.[1]
        expect(typeof rejectedHandler).toBe('function')

        const errorWithoutConfig = {
            message: 'Network Error',
            request: {}
        } as any

        await expect(rejectedHandler(errorWithoutConfig)).rejects.toBe(errorWithoutConfig)
        expect(mocks.mockAddToast).toHaveBeenCalled()
    })
})
