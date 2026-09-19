import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import type { Notification, PageResponse } from '@/types'
import { sessionQueryKey } from '@/queryAuthScope'
import { notificationListQueryKey, notificationUnreadCountQueryKey } from '../notificationQueryKeys'
import { createRecentNotificationIdCache } from '../../stream/recentNotificationIdCache'
import { applyIncomingNotificationToCache } from '../notificationCacheUpdater'

const notification = (notificationId: number): Notification => ({
  notificationId,
  notificationType: 'COMMENT',
  sourceType: 'POST',
  sourceId: 1,
  isRead: true,
  createdAt: '2026-07-18T00:00:00Z',
  message: `notification ${notificationId}`,
  actor: { userId: 1, displayName: 'User' },
  actorDisplayName: 'User',
  actorInitial: 'U',
})

const page = (number: number, content: Notification[]): PageResponse<Notification> => ({
  content,
  number,
  size: 2,
  totalElements: 4,
  totalPages: 2,
  first: number === 0,
  last: number === 1,
  empty: content.length === 0,
})

describe('notificationCacheUpdater', () => {
  it('updates first-page metadata and invalidates cached later pages', () => {
    const queryClient = new QueryClient()
    const generation = 4
    const firstKey = sessionQueryKey(generation, notificationListQueryKey({ page: 0, size: 2 }))
    const secondKey = sessionQueryKey(generation, notificationListQueryKey({ page: 1, size: 2 }))
    queryClient.setQueryData(firstKey, page(0, [notification(4), notification(3)]))
    queryClient.setQueryData(secondKey, page(1, [notification(2), notification(1)]))
    const recentIds = { has: vi.fn(() => false), remember: vi.fn() }

    applyIncomingNotificationToCache(queryClient, notification(5), recentIds, generation)

    expect(queryClient.getQueryData<PageResponse<Notification>>(firstKey)).toMatchObject({
      content: [{ notificationId: 5 }, { notificationId: 4 }],
      totalElements: 5,
      totalPages: 3,
      last: false,
    })
    expect(queryClient.getQueryState(firstKey)?.isInvalidated).toBe(false)
    expect(queryClient.getQueryState(secondKey)?.isInvalidated).toBe(true)
  })

  it('invalidates a cached later page even when no first page is cached', () => {
    const queryClient = new QueryClient()
    const generation = 5
    const secondKey = sessionQueryKey(generation, notificationListQueryKey({ page: 1, size: 2 }))
    queryClient.setQueryData(secondKey, page(1, [notification(2), notification(1)]))

    applyIncomingNotificationToCache(
      queryClient,
      notification(5),
      { has: () => false, remember: vi.fn() },
      generation,
    )

    expect(queryClient.getQueryState(secondKey)?.isInvalidated).toBe(true)
  })

  it('updates grouped revisions without counting the same notification again', () => {
    const client = new QueryClient()
    const key = sessionQueryKey(4, notificationListQueryKey({ page: 0, size: 2 }))
    const unreadKey = sessionQueryKey(4, notificationUnreadCountQueryKey)
    client.setQueryData(key, page(0, []))
    client.setQueryData(unreadKey, 0)
    const recent = createRecentNotificationIdCache(200)
    const first = { ...notification(7), groupCount: 1, lastEventAt: '2026-09-13T00:00:00Z' }
    const latest = { ...first, groupCount: 2, lastEventAt: '2026-09-13T00:01:00Z', message: 'Second comment' }

    applyIncomingNotificationToCache(client, first, recent, 4)
    applyIncomingNotificationToCache(client, latest, recent, 4)
    applyIncomingNotificationToCache(client, first, recent, 4)
    applyIncomingNotificationToCache(client, latest, recent, 4)

    expect(client.getQueryData(key)).toMatchObject({
      content: [{ groupCount: 2, message: 'Second comment' }],
      totalElements: 5,
    })
    expect(client.getQueryData(unreadKey)).toBe(1)
  })

  it('does not regress a newer or read notification already loaded from the server', () => {
    const client = new QueryClient()
    const key = sessionQueryKey(4, notificationListQueryKey({ page: 0, size: 2 }))
    const latest = { ...notification(7), groupCount: 3, lastEventAt: '2026-09-13T00:02:00Z' }
    client.setQueryData(key, page(0, [latest]))
    const recent = createRecentNotificationIdCache(200)

    applyIncomingNotificationToCache(client, { ...latest, groupCount: 2, message: 'Stale', lastEventAt: '2026-09-13T00:01:00Z' }, recent, 4)
    applyIncomingNotificationToCache(client, latest, recent, 4)

    expect(client.getQueryData(key)).toMatchObject({ content: [latest] })
  })

  it('does not inflate unread or page totals when a group returns from a later page', () => {
    const client = new QueryClient()
    const key = sessionQueryKey(4, notificationListQueryKey({ page: 0, size: 2 }))
    const laterKey = sessionQueryKey(4, notificationListQueryKey({ page: 1, size: 2 }))
    const unreadKey = sessionQueryKey(4, notificationUnreadCountQueryKey)
    client.setQueryData(key, page(0, [notification(4), notification(3)]))
    client.setQueryData(laterKey, page(1, [notification(2), notification(1)]))
    client.setQueryData(unreadKey, 4)

    applyIncomingNotificationToCache(client, { ...notification(1), groupCount: 2 }, createRecentNotificationIdCache(200), 4)

    expect(client.getQueryData(key)).toMatchObject({ content: [{ notificationId: 1 }, { notificationId: 4 }], totalElements: 4 })
    expect(client.getQueryData(unreadKey)).toBe(4)
    expect(client.getQueryState(laterKey)?.isInvalidated).toBe(true)
  })

  it('remembers grouped revisions even without a notification list mounted', () => {
    const client = new QueryClient()
    const unreadKey = sessionQueryKey(4, notificationUnreadCountQueryKey)
    const recent = createRecentNotificationIdCache(200)
    applyIncomingNotificationToCache(client, { ...notification(7), groupCount: 1 }, recent, 4)
    applyIncomingNotificationToCache(client, { ...notification(7), groupCount: 2 }, recent, 4)
    applyIncomingNotificationToCache(client, { ...notification(7), groupCount: 1 }, recent, 4)
    expect(client.getQueryData(unreadKey)).toBe(1)
  })
})
