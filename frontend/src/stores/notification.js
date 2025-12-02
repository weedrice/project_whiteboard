import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '@/api/notification'

export const useNotificationStore = defineStore('notification', () => {
    const notifications = ref([])
    const unreadCount = ref(0)
    const isLoading = ref(false)

    async function fetchNotifications(page = 0, size = 20) {
        isLoading.value = true
        try {
            const { data } = await notificationApi.getNotifications({ page, size })
            if (data.success) {
                const mappedContent = data.data.content.map(n => ({
                    ...n,
                    isRead: n.isRead === true || n.isRead === 'Y' || n.is_read === 'Y' || n.is_read === true || n.read === true
                }))
                if (page === 0) {
                    notifications.value = mappedContent
                } else {
                    notifications.value.push(...mappedContent)
                }
            }
        } catch (error) {
            console.error('Failed to fetch notifications:', error)
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
            console.error('Failed to fetch unread count:', error)
        }
    }

    async function markAsRead(notificationId) {
        try {
            const { data } = await notificationApi.markAsRead(notificationId)
            if (data.success) {
                const notification = notifications.value.find(n => n.notificationId === notificationId)
                if (notification && !notification.isRead) {
                    notification.isRead = true
                    unreadCount.value = Math.max(0, unreadCount.value - 1)
                }
            }
        } catch (error) {
            console.error('Failed to mark notification as read:', error)
        }
    }

    async function markAllAsRead() {
        try {
            const { data } = await notificationApi.markAllAsRead()
            if (data.success) {
                notifications.value.forEach(n => n.isRead = true)
                unreadCount.value = 0
                // Refresh from server to ensure sync
                await fetchNotifications()
            }
        } catch (error) {
            console.error('Failed to mark all notifications as read:', error)
        }
    }

    return {
        notifications,
        unreadCount,
        isLoading,
        fetchNotifications,
        fetchUnreadCount,
        markAsRead,
        markAllAsRead
    }
})
