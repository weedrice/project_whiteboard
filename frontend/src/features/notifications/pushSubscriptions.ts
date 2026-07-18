import { userApi, type PushSubscriptionPayload } from '@/api/user'
import type { AxiosRequestConfig } from 'axios'

const SERVICE_WORKER_READY_TIMEOUT_MS = 5000

export function isPushSupported() {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

export function getNotificationPermission(): NotificationPermission | 'unsupported' {
  return isPushSupported() ? Notification.permission : 'unsupported'
}

export async function requestPushPermission() {
  if (!isPushSupported()) {
    return 'unsupported' as const
  }
  return Notification.requestPermission()
}

export async function getBrowserPushSubscription() {
  if (!isPushSupported()) {
    return null
  }
  const registration = await getPushServiceWorkerRegistration()
  return registration.pushManager.getSubscription()
}

export async function subscribeBrowserPush(publicKey: string) {
  if (!isPushSupported()) {
    throw new Error('Push notifications are not supported.')
  }
  const registration = await getPushServiceWorkerRegistration()
  return registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey),
  })
}

export async function saveBrowserPushSubscription(subscription: PushSubscription, config?: AxiosRequestConfig) {
  return userApi.createPushSubscription(toPushSubscriptionPayload(subscription), config)
}

export function isPushSubscriptionForApplicationServerKey(
  subscription: PushSubscription,
  publicKey: string,
): boolean {
  const applicationServerKey = subscription.options.applicationServerKey
  if (!applicationServerKey) return false
  const current = new Uint8Array(applicationServerKey)
  const expected = urlBase64ToUint8Array(publicKey)
  if (current.length !== expected.length) return false
  let difference = 0
  for (let index = 0; index < current.length; index += 1) {
    difference |= current[index] ^ expected[index]
  }
  return difference === 0
}

async function getPushServiceWorkerRegistration(): Promise<ServiceWorkerRegistration> {
  const existing = await navigator.serviceWorker.getRegistration?.()
  if (existing?.active) return existing

  let timeoutId: ReturnType<typeof setTimeout> | undefined
  try {
    return await Promise.race([
      navigator.serviceWorker.ready,
      new Promise<never>((_resolve, reject) => {
        timeoutId = setTimeout(
          () => reject(new Error('Push service worker registration is unavailable.')),
          SERVICE_WORKER_READY_TIMEOUT_MS,
        )
      }),
    ])
  } finally {
    if (timeoutId !== undefined) clearTimeout(timeoutId)
  }
}

export async function deleteBrowserPushSubscription(subscription: PushSubscription, config?: AxiosRequestConfig) {
  return userApi.deletePushSubscription(toPushSubscriptionPayload(subscription), config)
}

/**
 * Detaches the browser endpoint at an authentication boundary.
 *
 * The browser subscription is removed before the best-effort server request so
 * an offline logout cannot leave the previous account receiving notifications
 * in this browser. The previous access token is passed explicitly because the
 * reactive auth state may already belong to the next session by the time the
 * server request runs.
 */
export async function detachBrowserPushSubscriptionForSession(accessToken: string | null) {
  const subscription = await getBrowserPushSubscription().catch(() => null)
  if (!subscription) return

  const payload = (() => {
    try {
      return toPushSubscriptionPayload(subscription)
    } catch {
      return null
    }
  })()
  await subscription.unsubscribe().catch(() => false)

  if (!accessToken || !payload) return
  await userApi.deletePushSubscription(payload, {
    headers: { Authorization: `Bearer ${accessToken}` },
    preserveAuthHeader: true,
    skipAuthRefresh: true,
    skipGlobalErrorHandler: true,
  }).catch(() => undefined)
}

export function toPushSubscriptionPayload(subscription: PushSubscription): PushSubscriptionPayload {
  const json = subscription.toJSON()
  return {
    endpoint: subscription.endpoint,
    keys: {
      p256dh: json.keys?.p256dh ?? '',
      auth: json.keys?.auth ?? '',
    },
    userAgent: navigator.userAgent,
  }
}

function urlBase64ToUint8Array(value: string) {
  const padding = '='.repeat((4 - value.length % 4) % 4)
  const base64 = (value + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = window.atob(base64)
  const output = new Uint8Array(rawData.length)
  for (let index = 0; index < rawData.length; index += 1) {
    output[index] = rawData.charCodeAt(index)
  }
  return output
}
