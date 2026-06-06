import type { Ref } from 'vue'
import type { NotificationParams } from '@/api/notification'

export const notificationsQueryKey = ['notifications'] as const

export const notificationListQueryKey = (params: Ref<NotificationParams>) =>
    [...notificationsQueryKey, params] as const

export const notificationUnreadCountQueryKey = [...notificationsQueryKey, 'unread-count'] as const
