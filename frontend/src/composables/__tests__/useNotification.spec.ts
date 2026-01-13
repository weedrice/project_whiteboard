import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useNotification } from '../useNotification'

// Mock notification API
vi.mock('@/api/notification', () => ({
    notificationApi: {
        getNotifications: vi.fn(),
        getUnreadCount: vi.fn(),
        markAsRead: vi.fn(),
        markAllAsRead: vi.fn()
    }
}))

// Mock auth store
vi.mock('@/stores/auth', () => ({
    useAuthStore: vi.fn(() => ({
        isAuthenticated: true,
        accessToken: 'test-token'
    }))
}))

// Mock vue-query
const mockInvalidateQueries = vi.fn()
const mockSetQueryData = vi.fn()
const mockCancelQueries = vi.fn()
const mockGetQueryData = vi.fn()

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options) => {
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn()
        }
    }),
    useMutation: vi.fn((options) => {
        return {
            mutate: async (variables: unknown) => {
                try {
                    const result = await options.mutationFn(variables)
                    if (options.onSuccess) options.onSuccess(result, variables, undefined)
                    return result
                } catch (error) {
                    if (options.onError) options.onError(error, variables, undefined)
                    throw error
                } finally {
                    if (options.onSettled) options.onSettled(undefined, undefined, variables, undefined)
                }
            },
            mutateAsync: async (variables: unknown) => {
                try {
                    const result = await options.mutationFn(variables)
                    if (options.onSuccess) options.onSuccess(result, variables, undefined)
                    return result
                } catch (error) {
                    if (options.onError) options.onError(error, variables, undefined)
                    throw error
                } finally {
                    if (options.onSettled) options.onSettled(undefined, undefined, variables, undefined)
                }
            },
            isLoading: ref(false),
            error: ref(null)
        }
    }),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries,
        setQueryData: mockSetQueryData,
        cancelQueries: mockCancelQueries,
        getQueryData: mockGetQueryData
    }))
}))

describe('useNotification', () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
    })

    describe('useNotifications', () => {
        it('returns query hooks', () => {
            const { useNotifications } = useNotification()
            const params = ref({ page: 0, size: 20 })

            const result = useNotifications(params)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
            expect(result).toHaveProperty('error')
        })
    })

    describe('useUnreadCount', () => {
        it('returns query hooks', () => {
            const { useUnreadCount } = useNotification()

            const result = useUnreadCount()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })
    })

    describe('useMarkAsRead', () => {
        it('calls API and invalidates queries', async () => {
            const { notificationApi } = await import('@/api/notification')
            vi.mocked(notificationApi.markAsRead).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useMarkAsRead } = useNotification()
            const mutation = useMarkAsRead()

            await mutation.mutateAsync(1)

            expect(notificationApi.markAsRead).toHaveBeenCalledWith(1)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
        })
    })

    describe('useMarkAllAsRead', () => {
        it('calls API and invalidates queries', async () => {
            const { notificationApi } = await import('@/api/notification')
            vi.mocked(notificationApi.markAllAsRead).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useMarkAllAsRead } = useNotification()
            const mutation = useMarkAllAsRead()

            await mutation.mutateAsync(undefined)

            expect(notificationApi.markAllAsRead).toHaveBeenCalled()
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
        })
    })

    describe('connectToSse', () => {
        it('creates EventSource connection', () => {
            // Mock EventSource constructor
            const mockEventSource = {
                addEventListener: vi.fn(),
                close: vi.fn(),
                onerror: null
            }
            global.EventSource = class EventSource {
                constructor(url: string) {
                    return mockEventSource as any
                }
            } as any

            const { connectToSse } = useNotification()

            connectToSse()

            expect(mockEventSource.addEventListener).toBeDefined()
        })
    })
})
