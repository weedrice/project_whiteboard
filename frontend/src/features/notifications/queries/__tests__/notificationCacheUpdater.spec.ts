import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import type { Notification, PageResponse } from '@/types'
import { sessionQueryKey } from '@/queryAuthScope'
import { notificationListQueryKey } from '../notificationQueryKeys'
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
})
