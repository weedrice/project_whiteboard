import { describe, expect, it } from 'vitest'
import { normalizePushNotificationPayload } from '@/utils/pushNotificationPayload'

describe('normalizePushNotificationPayload', () => {
  it.each([null, undefined, 'message', 1, [], true])('returns an empty payload for non-object JSON: %p', (value) => {
    expect(normalizePushNotificationPayload(value)).toEqual({})
  })

  it('keeps only supported string fields', () => {
    expect(normalizePushNotificationPayload({
      title: 'Title',
      body: 'Body',
      icon: 'https://evil.example/icon.png',
      badge: 123,
      url: '/notifications',
      tag: 'message',
      extra: 'ignored',
    })).toEqual({
      title: 'Title',
      body: 'Body',
      icon: 'https://evil.example/icon.png',
      url: '/notifications',
      tag: 'message',
    })
  })
})
