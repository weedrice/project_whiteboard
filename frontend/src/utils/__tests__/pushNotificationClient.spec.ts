import { describe, expect, it, vi } from 'vitest'
import { focusOrOpenPushNotificationTarget } from '@/utils/pushNotificationClient'

function client(url: string) {
  const value = {
    url,
    focus: vi.fn(async () => undefined),
  }
  return value
}

describe('focusOrOpenPushNotificationTarget', () => {
  it('opens a new window instead of overwriting a different app route', async () => {
    const existing = client('https://noviis.kr/mypage')
    const openWindow = vi.fn(async () => undefined)

    await focusOrOpenPushNotificationTarget(
      [existing],
      'https://noviis.kr/board/free/1',
      openWindow,
    )

    expect(existing.focus).not.toHaveBeenCalled()
    expect(openWindow).toHaveBeenCalledWith('https://noviis.kr/board/free/1')
  })

  it('focuses an existing exact target', async () => {
    const existing = client('https://noviis.kr/notifications')
    const openWindow = vi.fn(async () => undefined)

    await focusOrOpenPushNotificationTarget(
      [existing],
      existing.url,
      openWindow,
    )

    expect(existing.focus).toHaveBeenCalledOnce()
    expect(openWindow).not.toHaveBeenCalled()
  })

  it('opens a new window when no same-origin client exists', async () => {
    const openWindow = vi.fn(async () => undefined)

    await focusOrOpenPushNotificationTarget(
      [client('https://example.com/')],
      'https://noviis.kr/notifications',
      openWindow,
    )

    expect(openWindow).toHaveBeenCalledWith('https://noviis.kr/notifications')
  })
})
