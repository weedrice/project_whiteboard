import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { notificationApi } from '@/api/notification'
import { computed } from 'vue'

export function useNotification() {
    const queryClient = useQueryClient()

    const useNotifications = (params) => {
        return useQuery({
            queryKey: ['notifications', params],
            queryFn: async () => {
                const { data } = await notificationApi.getNotifications(params.value)
                if (data.success && data.data.content) {
                    data.data.content = data.data.content.map(n => ({
                        ...n,
                        isRead: n.isRead === true || n.isRead === 'Y' || n.is_read === 'Y' || n.is_read === true || n.read === true
                    }))
                }
                return data.data
            },
            keepPreviousData: true
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
            mutationFn: async (notificationId) => {
                const { data } = await notificationApi.markAsRead(notificationId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries(['notifications'])
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
                queryClient.invalidateQueries(['notifications'])
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
