import type { NotificationParams } from '@/api/notification'

export const notificationsQueryKey = ['notifications'] as const

export const notificationListQueryKey = (params: Readonly<NotificationParams>) =>
    [...notificationsQueryKey, { ...params }] as const

export const notificationUnreadCountQueryKey = [...notificationsQueryKey, 'unread-count'] as const
