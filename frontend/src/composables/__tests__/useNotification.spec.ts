import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { useNotification } from '../useNotification'

// Mock notification store
const mockFetchNotifications = vi.fn()
const mockFetchUnreadCount = vi.fn()
const mockMarkAsRead = vi.fn()
const mockMarkAllAsRead = vi.fn()
const mockConnectToSse = vi.fn()

vi.mock('@/stores/notification', () => ({
    useNotificationStore: vi.fn(() => ({
        notifications: [],
        totalPages: 0,
        totalElements: 0,
        unreadCount: 0,
        fetchNotifications: mockFetchNotifications,
        fetchUnreadCount: mockFetchUnreadCount,
        markAsRead: mockMarkAsRead,
        markAllAsRead: mockMarkAllAsRead,
        connectToSse: mockConnectToSse
    }))
}))

// Mock vue-query
const mockInvalidateQueries = vi.fn()
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
                const result = await options.mutationFn(variables)
                options.onSuccess?.(result, variables)
                return result
            },
            mutateAsync: async (variables: unknown) => {
                const result = await options.mutationFn(variables)
                options.onSuccess?.(result, variables)
                return result
            },
            isLoading: ref(false),
            error: ref(null)
        }
    }),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries
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
        it('calls store.markAsRead and invalidates queries', async () => {
            const { useMarkAsRead } = useNotification()
            const mutation = useMarkAsRead()

            mockMarkAsRead.mockResolvedValue(undefined)

            await mutation.mutateAsync(1)

            expect(mockMarkAsRead).toHaveBeenCalledWith(1)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
        })
    })

    describe('useMarkAllAsRead', () => {
        it('calls store.markAllAsRead and invalidates queries', async () => {
            const { useMarkAllAsRead } = useNotification()
            const mutation = useMarkAllAsRead()

            mockMarkAllAsRead.mockResolvedValue(undefined)

            await mutation.mutateAsync(undefined)

            expect(mockMarkAllAsRead).toHaveBeenCalled()
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['notifications'] })
        })
    })

    describe('connectToSse', () => {
        it('calls store.connectToSse', () => {
            const { connectToSse } = useNotification()

            connectToSse()

            expect(mockConnectToSse).toHaveBeenCalled()
        })
    })
})
