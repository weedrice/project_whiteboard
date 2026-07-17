import { describe, expect, it } from 'vitest'
import {
  PUSH_NOTIFICATION_FALLBACK_PATH,
  resolveInternalPushNotificationUrl,
} from '@/utils/pushNotificationUrl'

describe('resolveInternalPushNotificationUrl', () => {
  const origin = 'https://noviis.kr'
  const fallback = `${origin}${PUSH_NOTIFICATION_FALLBACK_PATH}`

  it('accepts only same-origin absolute paths', () => {
    expect(resolveInternalPushNotificationUrl('/board/free/post/1?from=push', origin))
      .toBe('https://noviis.kr/board/free/post/1?from=push')
  })

  it.each([
    'https://evil.example/phish',
    '//evil.example/phish',
    '/\\evil.example/phish',
    'javascript:alert(1)',
    'data:text/html,hello',
    'notifications',
    null,
  ])('falls back for an unsafe target: %s', (candidate) => {
    expect(resolveInternalPushNotificationUrl(candidate, origin)).toBe(fallback)
  })
})
