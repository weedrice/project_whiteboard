import type { QueryClient } from '@tanstack/vue-query'
import type { Notification } from '@/types'
import {
  notificationsQueryKey,
  notificationUnreadCountQueryKey,
} from '@/features/notifications/queries/notificationQueryKeys'
import {
  getNotificationPageNumber,
  isNotificationPage,
} from '@/features/notifications/stream/notificationStreamStateModel'
import { sessionQueryKey } from '@/queryAuthScope'

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
  sessionGeneration: number,
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

  const notificationsSessionKey = sessionQueryKey(sessionGeneration, notificationsQueryKey)
  const unreadSessionKey = sessionQueryKey(sessionGeneration, notificationUnreadCountQueryKey)

  queryClient.setQueriesData({ queryKey: notificationsSessionKey }, (oldData: unknown) => {
    if (!isNotificationPage(oldData)) return oldData
    if (getNotificationPageNumber(oldData) !== 0) return oldData

    const existingIndex = oldData.content.findIndex((item) => item.notificationId === normalized.notificationId)
    if (existingIndex >= 0) {
      alreadyExistsInFirstPage = true
      const nextContent = [...oldData.content]
      nextContent.splice(existingIndex, 1)
      nextContent.unshift(normalized)
      return {
        ...oldData,
        content: nextContent,
      }
    }

    const nextContent = [normalized, ...oldData.content]
    const sizeLimit = oldData.size > 0 ? oldData.size : nextContent.length
    const totalElements = oldData.totalElements + 1
    const totalPages = oldData.size > 0 ? Math.ceil(totalElements / oldData.size) : oldData.totalPages

    return {
      ...oldData,
      content: nextContent.slice(0, sizeLimit),
      totalElements,
      totalPages,
      empty: false,
      last: totalPages <= 1,
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

  void queryClient.invalidateQueries({
    queryKey: notificationsSessionKey,
    predicate: (query) => isNotificationPage(query.state.data)
      && getNotificationPageNumber(query.state.data) > 0,
  })

  queryClient.setQueryData(unreadSessionKey, (old: number | undefined) => (old || 0) + 1)
}
