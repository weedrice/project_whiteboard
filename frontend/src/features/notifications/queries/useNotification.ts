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
import {
    AUTH_SCOPED_QUERY_META,
    currentSessionQueryKey,
    isSessionGenerationCurrent,
} from '@/queryAuthScope'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'

export function useNotification() {
    const queryClient = useQueryClient()
    const authStore = useAuthStore()
    const authKey = (queryKey: readonly unknown[]) => currentSessionQueryKey(authStore, queryKey)

    const useNotifications = (params: Ref<NotificationParams>) => {
        return useApiPageQuery<Notification>({
            queryKey: computed(() => notificationListQueryKey(params.value)),
            meta: AUTH_SCOPED_QUERY_META,
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => notificationApi.getNotifications(params.value),
                (config) => notificationApi.getNotifications(params.value, config),
            ),
        })
    }

    const useUnreadCount = () => {
        return useApiQuery<number>({
            queryKey: notificationUnreadCountQueryKey,
            meta: AUTH_SCOPED_QUERY_META,
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
            onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
            mutationFn: async (notificationId: number) => {
                const { data } = await notificationApi.markAsRead(notificationId)
                return data
            },
            onSuccess: (_data, _variables, context) => {
                if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
                queryClient.invalidateQueries({ queryKey: authKey(notificationsQueryKey) })
                queryClient.invalidateQueries({ queryKey: authKey(notificationUnreadCountQueryKey) })
            }
        })
    }

    const useMarkAllAsRead = () => {
        return useMutation({
            onMutate: () => ({ sessionGeneration: authStore.sessionGeneration }),
            mutationFn: async () => {
                const { data } = await notificationApi.markAllAsRead()
                return data
            },
            onSuccess: (_data, _variables, context) => {
                if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
                queryClient.invalidateQueries({ queryKey: authKey(notificationsQueryKey) })
                queryClient.invalidateQueries({ queryKey: authKey(notificationUnreadCountQueryKey) })
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
