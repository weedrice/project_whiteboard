import { afterEach, describe, expect, it, vi } from 'vitest'
import { getBrowserPushSubscription } from '@/features/notifications/pushSubscriptions'

describe('push service worker registration', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('fails with a retryable error instead of waiting forever when registration is unavailable', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('PushManager', class PushManager {})
    vi.stubGlobal('Notification', { permission: 'default' })
    vi.stubGlobal('navigator', {
      userAgent: 'test',
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue(undefined),
        ready: new Promise<ServiceWorkerRegistration>(() => {}),
      },
    })

    const pending = getBrowserPushSubscription().then(
      () => ({ error: null }),
      (error: unknown) => ({ error }),
    )
    await vi.advanceTimersByTimeAsync(5000)

    const { error } = await pending
    expect(error).toBeInstanceOf(Error)
    expect((error as Error).message).toContain('registration is unavailable')
  })
})
