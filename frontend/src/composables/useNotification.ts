import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { notificationApi, type NotificationParams, type Notification } from '@/api/notification'
import { type Ref } from 'vue'

export function useNotification() {
    const queryClient = useQueryClient()

    const useNotifications = (params: Ref<NotificationParams>) => {
        return useQuery({
            queryKey: ['notifications', params],
            queryFn: async () => {
                const { data } = await notificationApi.getNotifications(params.value)
                if (data.success && data.data.content) {
                    data.data.content = data.data.content.map((n: any) => ({
                        ...n,
                        isRead: n.isRead === true || n.isRead === 'Y' || n.is_read === 'Y' || n.is_read === true || n.read === true
                    }))
                }
                return data.data
            },
            placeholderData: (previousData: any) => previousData
        })
    }

    const useUnreadCount = () => {
        return useQuery({
            queryKey: ['notifications', 'unread-count'],
            queryFn: async () => {
                const { data } = await notificationApi.getUnreadCount()
                return data.data
            },
            refetchInterval: 30000, // Poll every 30 seconds
        })
    }

    const useMarkAsRead = () => {
        return useMutation({
            mutationFn: async (notificationId: string | number) => {
                const { data } = await notificationApi.markAsRead(notificationId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['notifications'] })
            }
        })
    }

    const useMarkAllAsRead = () => {
        return useMutation({
            mutationFn: async () => {
                const { data } = await notificationApi.markAllAsRead()
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['notifications'] })
            }
        })
    }

    return {
        useNotifications,
        useUnreadCount,
        useMarkAsRead,
        useMarkAllAsRead
    }
}
