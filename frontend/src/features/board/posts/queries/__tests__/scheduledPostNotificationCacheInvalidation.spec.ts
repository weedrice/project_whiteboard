import { describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
import type { Notification } from '@/types'
import { invalidateScheduledPostNotificationCaches } from '@/features/board/posts/queries/scheduledPostNotificationCacheInvalidation'

function notification(overrides: Partial<Notification>): Notification {
  return {
    notificationId: 1,
    notificationType: 'SYSTEM',
    message: '',
    sourceType: 'SYSTEM',
    sourceId: 44,
    isRead: false,
    createdAt: '2026-07-20T12:00:00',
    actor: { userId: 1, displayName: '' },
    actorDisplayName: '',
    actorInitial: '',
    ...overrides,
  }
}

function queryClient() {
  return {
    invalidateQueries: vi.fn(),
  } as unknown as QueryClient
}

describe('invalidateScheduledPostNotificationCaches', () => {
  it('invalidates the failed schedule list and detail without exposing unrelated post caches', () => {
    const client = queryClient()

    invalidateScheduledPostNotificationCaches(client, notification({
      messageKey: 'notification.scheduled.failed',
      sourceId: 44,
    }), 7)

    expect(client.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 7, 'user', 'scheduled-posts'],
    })
    expect(client.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 7, 'user', 'scheduled-posts', '44'],
    })
    expect(client.invalidateQueries).toHaveBeenCalledTimes(2)
  })

  it('invalidates publication-derived post, draft, point, and scheduled caches', () => {
    const client = queryClient()

    invalidateScheduledPostNotificationCaches(client, notification({
      messageKey: 'notification.scheduled.published',
      sourceType: 'POST',
      sourceId: 91,
    }), 8)

    expect(client.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 8, 'user', 'scheduled-posts'],
    })
    expect(client.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 8, 'user', 'drafts'],
    })
    expect(client.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 8, 'user', 'points'],
    })
    expect(client.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 8, 'post', 91],
    })
    expect(client.invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['session', 8, 'tags'],
    })
  })

  it('ignores unrelated system notifications', () => {
    const client = queryClient()

    invalidateScheduledPostNotificationCaches(client, notification({
      messageKey: 'notification.badge.awarded',
    }), 9)

    expect(client.invalidateQueries).not.toHaveBeenCalled()
  })
})
