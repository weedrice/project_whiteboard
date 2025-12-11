import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { notificationApi, type NotificationParams, type Notification } from '@/api/notification'
import { type Ref } from 'vue'
import { useNotificationStore } from '@/stores/notification'

export function useNotification() {
    const queryClient = useQueryClient()
    const store = useNotificationStore()

    const useNotifications = (params: Ref<NotificationParams>) => {
        return useQuery({
            queryKey: ['notifications', params],
            queryFn: async () => {
                // Delegate to store for fetching, but return data for useQuery
                await store.fetchNotifications(params.value.page, params.value.size)
                return {
                    content: store.notifications,
                    totalPages: store.totalPages,
                    totalElements: store.totalElements
                }
            },
            placeholderData: (previousData: any) => previousData
        })
    }

    const useUnreadCount = () => {
        return useQuery({
            queryKey: ['notifications', 'unread-count'],
            queryFn: async () => {
                await store.fetchUnreadCount()
                return store.unreadCount
            },
            // Reduce polling since we have SSE, but keep it as backup or for initial load
            refetchInterval: 60000,
        })
    }

    const useMarkAsRead = () => {
        return useMutation({
            mutationFn: async (notificationId: number) => {
                return store.markAsRead(notificationId)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['notifications'] })
            }
        })
    }

    const useMarkAllAsRead = () => {
        return useMutation({
            mutationFn: async () => {
                return store.markAllAsRead()
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['notifications'] })
            }
        })
    }

    const connectToSse = () => {
        store.connectToSse()
    }

    return {
        useNotifications,
        useUnreadCount,
        useMarkAsRead,
        useMarkAllAsRead,
        connectToSse
    }
}
