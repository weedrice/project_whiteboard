import { describe, expect, it } from 'vitest'
import {
  PUSH_NOTIFICATION_FALLBACK_PATH,
  PUSH_NOTIFICATION_IMAGE_FALLBACK_PATH,
  resolveInternalPushNotificationUrl,
  resolvePushImageUrl,
  resolvePushNavigationUrl,
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

  it('uses an internal asset fallback for an external notification image', () => {
    expect(resolveInternalPushNotificationUrl(
      'https://tracker.example/icon.png',
      origin,
      '/pwa-192x192.png',
    )).toBe('https://noviis.kr/pwa-192x192.png')
  })

  it.each([
    '/api/v1/users/me',
    '/oauth2/authorization/google',
    '/actuator/health',
    '/admin/users',
    '/%2561pi/v1/users/me',
    '/board%255c..%255capi/v1',
  ])('rejects a non-SPA or encoded bypass navigation: %s', (candidate) => {
    expect(resolvePushNavigationUrl(candidate, origin)).toBe(fallback)
  })

  it('uses a separate image allowlist', () => {
    expect(resolvePushImageUrl('/assets/push-icon.webp', origin))
      .toBe('https://noviis.kr/assets/push-icon.webp')
    expect(resolvePushImageUrl('/board/free', origin))
      .toBe(`${origin}${PUSH_NOTIFICATION_IMAGE_FALLBACK_PATH}`)
  })
})
