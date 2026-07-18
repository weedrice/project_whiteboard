import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { userApi } from '@/api/user'
import {
  detachBrowserPushSubscriptionForSession,
  getBrowserPushSubscription,
  isPushSubscriptionForApplicationServerKey,
} from '@/features/notifications/pushSubscriptions'

vi.mock('@/api/user', () => ({
  userApi: {
    deletePushSubscription: vi.fn(),
  },
}))

describe('push service worker registration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('compares the browser subscription application server key by value', () => {
    const matching = {
      options: { applicationServerKey: new Uint8Array([1, 2, 3]).buffer },
    } as unknown as PushSubscription
    const stale = {
      options: { applicationServerKey: new Uint8Array([1, 2, 4]).buffer },
    } as unknown as PushSubscription

    expect(isPushSubscriptionForApplicationServerKey(matching, 'AQID')).toBe(true)
    expect(isPushSubscriptionForApplicationServerKey(stale, 'AQID')).toBe(false)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('unsubscribes locally before best-effort server cleanup with the previous token', async () => {
    const order: string[] = []
    const subscription = {
      endpoint: 'https://push.example/subscription',
      toJSON: () => ({ keys: { p256dh: 'key', auth: 'auth' } }),
      unsubscribe: vi.fn(async () => {
        order.push('local')
        return true
      }),
    } as unknown as PushSubscription
    vi.mocked(userApi.deletePushSubscription).mockImplementationOnce(async () => {
      order.push('server')
      return {} as never
    })
    vi.stubGlobal('PushManager', class PushManager {})
    vi.stubGlobal('Notification', { permission: 'granted' })
    vi.stubGlobal('navigator', {
      userAgent: 'test-agent',
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue({
          active: {},
          pushManager: { getSubscription: vi.fn().mockResolvedValue(subscription) },
        }),
      },
    })

    await detachBrowserPushSubscriptionForSession('old-token')

    expect(order).toEqual(['local', 'server'])
    expect(userApi.deletePushSubscription).toHaveBeenCalledWith(
      {
        endpoint: 'https://push.example/subscription',
        keys: { p256dh: 'key', auth: 'auth' },
        userAgent: 'test-agent',
      },
      {
        headers: { Authorization: 'Bearer old-token' },
        preserveAuthHeader: true,
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
      },
    )
  })

  it('keeps local unsubscribe successful when server cleanup is offline', async () => {
    const unsubscribe = vi.fn().mockResolvedValue(true)
    const subscription = {
      endpoint: 'https://push.example/subscription',
      toJSON: () => ({ keys: { p256dh: 'key', auth: 'auth' } }),
      unsubscribe,
    } as unknown as PushSubscription
    vi.mocked(userApi.deletePushSubscription).mockRejectedValueOnce(new Error('offline'))
    vi.stubGlobal('PushManager', class PushManager {})
    vi.stubGlobal('Notification', { permission: 'granted' })
    vi.stubGlobal('navigator', {
      userAgent: 'test-agent',
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue({
          active: {},
          pushManager: { getSubscription: vi.fn().mockResolvedValue(subscription) },
        }),
      },
    })

    await expect(detachBrowserPushSubscriptionForSession('old-token')).resolves.toBeUndefined()
    expect(unsubscribe).toHaveBeenCalledOnce()
  })

  it('still unsubscribes locally when the server payload cannot be serialized', async () => {
    const unsubscribe = vi.fn().mockResolvedValue(true)
    const subscription = {
      endpoint: 'https://push.example/subscription',
      toJSON: () => { throw new Error('serialization failed') },
      unsubscribe,
    } as unknown as PushSubscription
    vi.stubGlobal('PushManager', class PushManager {})
    vi.stubGlobal('Notification', { permission: 'granted' })
    vi.stubGlobal('navigator', {
      userAgent: 'test-agent',
      serviceWorker: {
        getRegistration: vi.fn().mockResolvedValue({
          active: {},
          pushManager: { getSubscription: vi.fn().mockResolvedValue(subscription) },
        }),
      },
    })

    await detachBrowserPushSubscriptionForSession('old-token')

    expect(unsubscribe).toHaveBeenCalledOnce()
    expect(userApi.deletePushSubscription).not.toHaveBeenCalled()
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
