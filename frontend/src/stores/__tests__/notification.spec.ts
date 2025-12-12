import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationStore } from '../notification'
import { notificationApi } from '@/api/notification'

// Mock dependencies
vi.mock('@/api/notification', () => ({
    notificationApi: {
        getNotifications: vi.fn(),
        getUnreadCount: vi.fn(),
        markAsRead: vi.fn(),
        markAllAsRead: vi.fn()
    }
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: vi.fn()
    }
}))

describe('Notification Store', () => {
    let store: ReturnType<typeof useNotificationStore>

    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        store = useNotificationStore()
    })

    describe('initial state', () => {
        it('starts with empty notifications', () => {
            expect(store.notifications).toEqual([])
            expect(store.unreadCount).toBe(0)
            expect(store.isLoading).toBe(false)
        })
    })

    describe('fetchNotifications', () => {
        it('fetches notifications successfully', async () => {
            const mockNotifications = [
                { notificationId: 1, message: 'Test 1', isRead: false },
                { notificationId: 2, message: 'Test 2', isRead: true }
            ]

            vi.mocked(notificationApi.getNotifications).mockResolvedValue({
                data: {
                    success: true,
                    data: {
                        content: mockNotifications,
                        totalPages: 1,
                        totalElements: 2
                    }
                }
            } as any)

            await store.fetchNotifications(0, 20)

            expect(notificationApi.getNotifications).toHaveBeenCalledWith({ page: 0, size: 20 })
            expect(store.notifications).toHaveLength(2)
            expect(store.totalElements).toBe(2)
            expect(store.isLoading).toBe(false)
        })

        it('sets isLoading during fetch', async () => {
            vi.mocked(notificationApi.getNotifications).mockImplementation(async () => {
                expect(store.isLoading).toBe(true)
                return {
                    data: { success: true, data: { content: [], totalPages: 0, totalElements: 0 } }
                } as any
            })

            await store.fetchNotifications()
            expect(store.isLoading).toBe(false)
        })

        it('handles error gracefully', async () => {
            vi.mocked(notificationApi.getNotifications).mockRejectedValue(new Error('Network error'))

            await store.fetchNotifications()

            expect(store.notifications).toEqual([])
            expect(store.isLoading).toBe(false)
        })
    })

    describe('fetchUnreadCount', () => {
        it('fetches unread count successfully', async () => {
            vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({
                data: { success: true, data: { count: 5 } }
            } as any)

            await store.fetchUnreadCount()

            expect(store.unreadCount).toBe(5)
        })

        it('handles error gracefully', async () => {
            vi.mocked(notificationApi.getUnreadCount).mockRejectedValue(new Error('Error'))

            await store.fetchUnreadCount()

            expect(store.unreadCount).toBe(0)
        })
    })

    describe('markAsRead', () => {
        beforeEach(() => {
            store.notifications = [
                { notificationId: 1, message: 'Test', isRead: false, type: 'COMMENT', createdAt: '' },
                { notificationId: 2, message: 'Test 2', isRead: false, type: 'LIKE', createdAt: '' }
            ] as any
            store.unreadCount = 2
        })

        it('performs optimistic update', async () => {
            vi.mocked(notificationApi.markAsRead).mockResolvedValue({
                data: { success: true }
            } as any)

            await store.markAsRead(1)

            expect(store.notifications[0].isRead).toBe(true)
            expect(store.unreadCount).toBe(1)
        })

        it('reverts on API failure', async () => {
            vi.mocked(notificationApi.markAsRead).mockResolvedValue({
                data: { success: false }
            } as any)

            await store.markAsRead(1)

            expect(store.notifications[0].isRead).toBe(false)
            expect(store.unreadCount).toBe(2)
        })

        it('does not decrement count for already read notification', async () => {
            // Clear beforeEach and set fresh state
            store.notifications = [
                { notificationId: 1, message: 'Test', isRead: true, type: 'COMMENT', createdAt: '' },
                { notificationId: 2, message: 'Test 2', isRead: false, type: 'LIKE', createdAt: '' }
            ] as any
            store.unreadCount = 1 // Only one unread

            // markAsRead should not call API or decrement count for already read
            vi.mocked(notificationApi.markAsRead).mockResolvedValue({
                data: { success: true }
            } as any)

            await store.markAsRead(1) // Try to mark already read

            // Should not have decremented
            expect(store.unreadCount).toBe(1)
        })
    })

    describe('markAllAsRead', () => {
        beforeEach(() => {
            store.notifications = [
                { notificationId: 1, message: 'Test', isRead: false, type: 'COMMENT', createdAt: '' },
                { notificationId: 2, message: 'Test 2', isRead: false, type: 'LIKE', createdAt: '' }
            ] as any
            store.unreadCount = 2
        })

        it('marks all as read on success', async () => {
            vi.mocked(notificationApi.markAllAsRead).mockResolvedValue({
                data: { success: true }
            } as any)
            vi.mocked(notificationApi.getNotifications).mockResolvedValue({
                data: {
                    success: true,
                    data: { content: [], totalPages: 0, totalElements: 0 }
                }
            } as any)

            await store.markAllAsRead()

            expect(store.unreadCount).toBe(0)
        })

        it('reverts on API failure', async () => {
            vi.mocked(notificationApi.markAllAsRead).mockResolvedValue({
                data: { success: false }
            } as any)

            await store.markAllAsRead()

            expect(store.unreadCount).toBe(2) // Reverted
        })

        it('reverts on network error', async () => {
            vi.mocked(notificationApi.markAllAsRead).mockRejectedValue(new Error('Network error'))

            await store.markAllAsRead()

            expect(store.unreadCount).toBe(2) // Reverted
        })
    })

    describe('markAsRead error handling', () => {
        beforeEach(() => {
            store.notifications = [
                { notificationId: 1, message: 'Test', isRead: false, type: 'COMMENT', createdAt: '' }
            ] as any
            store.unreadCount = 1
        })

        it('reverts on network error', async () => {
            vi.mocked(notificationApi.markAsRead).mockRejectedValue(new Error('Network error'))

            await store.markAsRead(1)

            expect(store.notifications[0].isRead).toBe(false)
            expect(store.unreadCount).toBe(1)
        })
    })
})

