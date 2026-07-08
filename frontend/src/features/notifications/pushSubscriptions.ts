import { userApi, type PushSubscriptionPayload } from '@/api/user'

export function isPushSupported() {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
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
