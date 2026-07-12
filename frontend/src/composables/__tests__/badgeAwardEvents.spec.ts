import { describe, expect, it, vi } from 'vitest'
import { emitBadgeAwardEvent, subscribeBadgeAwardEvents } from '@/composables/badgeAwardEvents'
import type { Notification } from '@/types'

const notification = (notificationType: Notification['notificationType']): Notification => ({
  notificationId: 1,
  notificationType,
  message: 'message',
  sourceType: 'SYSTEM',
  sourceId: 1,
  isRead: false,
  createdAt: '2026-07-12T00:00:00',
  actor: { userId: 0, authorType: 'SYSTEM', displayName: '' },
  actorDisplayName: '',
  actorInitial: '',
})

describe('badgeAwardEvents', () => {
  it('publishes only badge notifications and removes unsubscribed listeners', () => {
    const listener = vi.fn()
    const unsubscribe = subscribeBadgeAwardEvents(listener)

    emitBadgeAwardEvent(notification('LIKE'))
    emitBadgeAwardEvent(notification('BADGE'))
    unsubscribe()
    emitBadgeAwardEvent(notification('BADGE'))

    expect(listener).toHaveBeenCalledTimes(1)
    expect(listener).toHaveBeenCalledWith(expect.objectContaining({ notificationType: 'BADGE' }))
  })
})
