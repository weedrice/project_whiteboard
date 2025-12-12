import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi, type Notification } from '@/api/notification'
import logger from '@/utils/logger'

export const useNotificationStore = defineStore('notification', () => {
    const notifications = ref<Notification[]>([])
    const unreadCount = ref(0)
    const totalPages = ref(0)
    const totalElements = ref(0)
    const isLoading = ref(false)

    async function fetchNotifications(page = 0, size = 20) {
        isLoading.value = true
        try {
            const { data } = await notificationApi.getNotifications({ page, size })
            if (data.success) {
                const mappedContent = data.data.content.map((n: Notification) => ({
                    ...n,
                    isRead: n.isRead === true || (n as unknown as { is_read?: string | boolean }).is_read === 'Y' || (n as unknown as { is_read?: string | boolean }).is_read === true
                }))
                notifications.value = mappedContent
                totalPages.value = data.data.totalPages
                totalElements.value = data.data.totalElements
            }
        } catch (error) {
            logger.error('Failed to fetch notifications:', error)
        } finally {
            isLoading.value = false
        }
    }

    async function fetchUnreadCount() {
        try {
            const { data } = await notificationApi.getUnreadCount()
            if (data.success) {
                unreadCount.value = data.data.count
            }
        } catch (error) {
            logger.error('Failed to fetch unread count:', error)
        }
    }

    async function markAsRead(notificationId: number) {
        // Optimistic update
        const notification = notifications.value.find(n => n.notificationId === notificationId)
        if (notification && !notification.isRead) {
            notification.isRead = true
            unreadCount.value = Math.max(0, unreadCount.value - 1)
        }

        try {
            const { data } = await notificationApi.markAsRead(notificationId)
            if (!data.success) {
                // Revert if failed
                if (notification) {
                    notification.isRead = false
                    unreadCount.value++
                }
            }
        } catch (error) {
            logger.error('Failed to mark notification as read:', error)
            // Revert if failed
            if (notification) {
                notification.isRead = false
                unreadCount.value++
            }
        }
    }

    async function markAllAsRead() {
        // Optimistic update
        const unreadNotifications = notifications.value.filter(n => !n.isRead)
        notifications.value.forEach(n => n.isRead = true)
        const previousUnreadCount = unreadCount.value
        unreadCount.value = 0

        try {
            const { data } = await notificationApi.markAllAsRead()
            if (data.success) {
                // Refresh from server to ensure sync
                await fetchNotifications()
            } else {
                // Revert
                unreadNotifications.forEach(n => n.isRead = false)
                unreadCount.value = previousUnreadCount
            }
        } catch (error) {
            logger.error('Failed to mark all notifications as read:', error)
            // Revert
            unreadNotifications.forEach(n => n.isRead = false)
            unreadCount.value = previousUnreadCount
        }
    }

    let eventSource: EventSource | null = null

    function connectToSse() {
        if (eventSource) return

        // Use relative path if proxy is set up, otherwise full URL
        const url = '/api/v1/notifications/stream'
        eventSource = new EventSource(url)

        eventSource.addEventListener('connect', (event) => {
            // console.log('SSE Connected:', event.data)
        })

        eventSource.addEventListener('notification', (event) => {
            try {
                const newNotification = JSON.parse(event.data)
                // Add to list
                notifications.value.unshift({
                    ...newNotification,
                    isRead: false
                })
                // Increment unread count
                unreadCount.value++
                // Update total elements if tracking
                totalElements.value++
            } catch (error) {
                logger.error('Failed to parse SSE notification:', error)
            }
        })

        eventSource.onerror = (error) => {
            // console.error('SSE Error:', error)
            if (eventSource) {
                eventSource.close()
                eventSource = null
                // Retry connection after delay? Browser usually handles reconnection for EventSource
                // But if we manually closed it, we might need to reopen.
                // For now, let's leave it closed or try to reconnect after 5s
                setTimeout(() => connectToSse(), 5000)
            }
        }
    }

    return {
        notifications,
        unreadCount,
        totalPages,
        totalElements,
        isLoading,
        fetchNotifications,
        fetchUnreadCount,
        markAsRead,
        markAllAsRead,
        connectToSse
    }
})
