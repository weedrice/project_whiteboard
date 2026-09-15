import { describe, expect, it, vi } from 'vitest'
import {
  getNotificationMessage,
  getNotificationPresentation,
  getNotificationTypeLabel,
} from '../notificationPresentation'

describe('getNotificationMessage', () => {
  it('localizes a masked actor label before formatting the notification message', () => {
    const t = vi.fn((key: string, ...args: unknown[]) => {
      const params = args[0] as unknown[] | undefined
      if (key === 'notification.actors.unknown') return 'Unknown'
      if (key === 'notification.post.liked') return `${params?.[0]} liked your post.`
      return key
    })

    const message = getNotificationMessage({
      message: '님이 게시글을 좋아합니다.',
      messageKey: 'notification.post.liked',
      messageParams: [''],
      actorLabelKey: 'notification.actors.unknown',
    }, t)

    expect(message).toBe('Unknown liked your post.')
  })
})

describe('notification presentation', () => {
  it.each([
    ['KEYWORD', 'accent', 'notification.types.keyword'],
    ['BADGE', 'accent', 'notification.types.badge'],
    ['SANCTION', 'warning', 'notification.types.sanction'],
  ] as const)('assigns %s an explicit semantic tone and label', (notificationType, tone, labelKey) => {
    const notification = { notificationType }

    expect(getNotificationPresentation(notification).tone).toBe(tone)
    expect(getNotificationTypeLabel(notification, (key) => key)).toBe(labelKey)
  })

  it('uses the shared fallback presentation when the server sends an unknown type', () => {
    const notification = { notificationType: 'UNKNOWN' as never }

    expect(getNotificationPresentation(notification).tone).toBe('neutral')
    expect(getNotificationTypeLabel(notification, (key) => key)).toBe('notification.types.default')
  })
})
