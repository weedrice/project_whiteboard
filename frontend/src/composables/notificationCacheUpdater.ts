import type { QueryClient } from '@tanstack/vue-query'
import type { Notification } from '@/types'
import {
  notificationsQueryKey,
  notificationUnreadCountQueryKey,
} from '@/composables/notificationQueryKeys'
import {
  getNotificationPageNumber,
  isNotificationPage,
} from '@/composables/notificationStreamStateModel'

export interface RecentNotificationIdCache {
  has: (id: number) => boolean
  remember: (id: number) => void
}

export function getRawNotificationId(rawNotification: { notificationId?: unknown; notification_id?: unknown }) {
  const rawNotificationId = rawNotification.notificationId ?? rawNotification.notification_id
  return typeof rawNotificationId === 'number' && Number.isFinite(rawNotificationId)
    ? rawNotificationId
    : null
}

export function applyIncomingNotificationToCache(
  queryClient: QueryClient,
  incoming: Notification,
  recentNotificationIds: RecentNotificationIdCache,
) {
  const normalized: Notification = {
    ...incoming,
    isRead: false,
  }
  const notificationId = normalized.notificationId
  if (typeof notificationId === 'number' && recentNotificationIds.has(notificationId)) {
    return
  }

  let alreadyExistsInFirstPage = false

  queryClient.setQueriesData({ queryKey: notificationsQueryKey }, (oldData: unknown) => {
    if (!isNotificationPage(oldData)) return oldData
    if (getNotificationPageNumber(oldData) !== 0) return oldData

    const alreadyExists = oldData.content.some((item) => item.notificationId === normalized.notificationId)
    if (alreadyExists) {
      alreadyExistsInFirstPage = true
      return oldData
    }

    const nextContent = [normalized, ...oldData.content]
    const sizeLimit = oldData.size > 0 ? oldData.size : nextContent.length

    return {
      ...oldData,
      content: nextContent.slice(0, sizeLimit),
      totalElements: oldData.totalElements + 1,
      empty: false,
    }
  })

  if (alreadyExistsInFirstPage) {
    if (typeof notificationId === 'number') {
      recentNotificationIds.remember(notificationId)
    }
    return
  }

  if (typeof notificationId === 'number') {
    recentNotificationIds.remember(notificationId)
  }

  queryClient.setQueryData(notificationUnreadCountQueryKey, (old: number | undefined) => (old || 0) + 1)
}
