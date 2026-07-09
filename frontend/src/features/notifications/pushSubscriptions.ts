import { userApi, type PushSubscriptionPayload } from '@/api/user'

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
  const registration = await navigator.serviceWorker.ready
  return registration.pushManager.getSubscription()
}

export async function subscribeBrowserPush(publicKey: string) {
  if (!isPushSupported()) {
    throw new Error('Push notifications are not supported.')
  }
  const registration = await navigator.serviceWorker.ready
  return registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey),
  })
}

export async function saveBrowserPushSubscription(subscription: PushSubscription) {
  return userApi.createPushSubscription(toPushSubscriptionPayload(subscription))
}

export async function deleteBrowserPushSubscription(subscription: PushSubscription) {
  return userApi.deletePushSubscription(toPushSubscriptionPayload(subscription))
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
