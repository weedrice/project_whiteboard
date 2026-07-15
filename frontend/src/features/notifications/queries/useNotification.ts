import { useMutation, useQueryClient } from '@tanstack/vue-query'
import {
    notificationApi,
    type NotificationParams
} from '@/api/notification'
import type { Notification } from '@/types'
import { type Ref, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
    notificationListQueryKey,
    notificationsQueryKey,
    notificationUnreadCountQueryKey,
} from '@/features/notifications/queries/notificationQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'

export function useNotification() {
    const queryClient = useQueryClient()

    const useNotifications = (params: Ref<NotificationParams>) => {
        return useApiPageQuery<Notification>({
            queryKey: computed(() => notificationListQueryKey(params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => notificationApi.getNotifications(params.value),
                (config) => notificationApi.getNotifications(params.value, config),
            ),
        })
    }

    const useUnreadCount = () => {
        const authStore = useAuthStore()
        return useApiQuery<number>({
            queryKey: notificationUnreadCountQueryKey,
            request: (context) => callWithOptionalQuerySignal(
                context,
                notificationApi.getUnreadCount,
                notificationApi.getUnreadCount,
            ),
            refetchInterval: 60000,
            staleTime: 0,
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
                queryClient.invalidateQueries({ queryKey: notificationsQueryKey })
                queryClient.invalidateQueries({ queryKey: notificationUnreadCountQueryKey })
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
                queryClient.invalidateQueries({ queryKey: notificationsQueryKey })
                queryClient.setQueryData(notificationUnreadCountQueryKey, 0)
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
