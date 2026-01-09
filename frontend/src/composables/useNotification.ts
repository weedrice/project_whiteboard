import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { notificationApi, type NotificationParams } from '@/api/notification'
import type { Notification, PageResponse } from '@/types'
import { type Ref, computed } from 'vue'
import logger from '@/utils/logger'
import { useAuthStore } from '@/stores/auth'

export function useNotification() {
    const queryClient = useQueryClient()

    const useNotifications = (params: Ref<NotificationParams>) => {
        return useQuery({
            queryKey: ['notifications', params],
            queryFn: async () => {
                const { data } = await notificationApi.getNotifications(params.value)
                return data.data
            },
            placeholderData: (previousData) => previousData
        })
    }

    const useUnreadCount = () => {
        const authStore = useAuthStore()
        return useQuery({
            queryKey: ['notifications', 'unread-count'],
            queryFn: async () => {
                const { data } = await notificationApi.getUnreadCount()
                return data.data
            },
            refetchInterval: 60000,
            enabled: computed(() => authStore.isAuthenticated),
        })
    }

    const useMarkAsRead = () => {
        return useMutation({
            mutationFn: async (notificationId: number) => {
                const { data } = await notificationApi.markAsRead(notificationId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['notifications'] })
                queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] })
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
                queryClient.setQueryData(['notifications', 'unread-count'], 0)
            }
        })
    }

    let eventSource: EventSource | null = null
    let reconnectTimer: ReturnType<typeof setTimeout> | null = null

    const connectToSse = () => {
        if (eventSource) return

        // 기존 재연결 타이머 정리
        if (reconnectTimer) {
            clearTimeout(reconnectTimer)
            reconnectTimer = null
        }

        const authStore = useAuthStore()
        const token = authStore.accessToken
        if (!token) return

        const url = `/api/v1/notifications/stream?token=${token}`
        eventSource = new EventSource(url)

        eventSource.addEventListener('notification', (event) => {
            try {
                const newNotification = JSON.parse(event.data) as Notification
                // Transform if needed (though we rely on API for transform, SSE might send raw data)
                // Assuming SSE sends compatible JSON or we need to transform it here too.
                // For now, let's assume it matches or is close enough.
                // Actually, we should probably ensure camelCase here if backend sends snake_case via SSE.
                const transformedNotification: Notification = {
                    ...newNotification,
                    isRead: false,
                    // Add other transforms if SSE data is raw
                }

                // Update unread count
                queryClient.setQueryData(['notifications', 'unread-count'], (old: number | undefined) => (old || 0) + 1)

                // Update notifications list (for page 0)
                // We need to find the active query for page 0.
                // This is a bit complex with dynamic params.
                // A simple approach is to invalidate, but that triggers a refetch.
                // To update cache directly:
                queryClient.setQueriesData({ queryKey: ['notifications'] }, (oldData: any) => {
                    if (!oldData) return oldData
                    // Check if this is a PageResponse
                    if ('content' in oldData) {
                        const pageData = oldData as PageResponse<Notification>
                        // Only prepend if it's the first page (usually we can't easily know from here without params)
                        // But commonly we want to see it.
                        // For simplicity, let's just invalidate to be safe and consistent.
                        return oldData
                    }
                    return oldData
                })
                queryClient.invalidateQueries({ queryKey: ['notifications'] })

            } catch (error) {
                logger.error('Failed to parse SSE notification:', error)
            }
        })

        eventSource.onerror = (error) => {
            if (eventSource) {
                eventSource.close()
                eventSource = null
                // 재연결 타이머 설정
                reconnectTimer = setTimeout(() => {
                    reconnectTimer = null
                    connectToSse()
                }, 5000)
            }
        }
    }

    // We might want to close SSE when not needed, but usually it's global.
    // If this composable is used in a component, we shouldn't close it on unmount if it's meant to be global.
    // But if we call connectToSse in a top-level component, it persists.

    const closeSse = () => {
        // 재연결 타이머 정리
        if (reconnectTimer) {
            clearTimeout(reconnectTimer)
            reconnectTimer = null
        }
        // SSE 연결 종료
        if (eventSource) {
            eventSource.close()
            eventSource = null
        }
    }

    return {
        useNotifications,
        useUnreadCount,
        useMarkAsRead,
        useMarkAllAsRead,
        connectToSse,
        closeSse
    }
}
