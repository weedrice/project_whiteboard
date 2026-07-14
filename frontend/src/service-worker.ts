/// <reference lib="webworker" />

import { clientsClaim } from 'workbox-core'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { NetworkOnly } from 'workbox-strategies'
import { cleanupOutdatedCaches, createHandlerBoundToURL, precacheAndRoute } from 'workbox-precaching'

declare const self: ServiceWorkerGlobalScope & {
  __WB_MANIFEST: Array<{ url: string, revision: string | null }>
}

clientsClaim()

cleanupOutdatedCaches()
precacheAndRoute(self.__WB_MANIFEST)

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') {
    void self.skipWaiting()
  }
})

registerRoute(({ url }) => url.pathname.startsWith('/api/'), new NetworkOnly())
registerRoute(({ url }) => url.pathname.startsWith('/oauth2/'), new NetworkOnly())
registerRoute(({ url }) => url.pathname.startsWith('/login/oauth2/'), new NetworkOnly())

const navigationRoute = new NavigationRoute(createHandlerBoundToURL('/index.html'), {
  denylist: [/^\/api\//, /^\/oauth2\//, /^\/login\/oauth2\//, /^\/robots\.txt$/, /^\/sitemap.*\.xml$/],
})
registerRoute(navigationRoute)

type PushPayload = {
  title?: string
  body?: string
  icon?: string
  badge?: string
  url?: string
  tag?: string
}

self.addEventListener('push', (event) => {
  const payload = readPushPayload(event)
  const title = payload.title || 'NoviIs'
  const options: NotificationOptions = {
    body: payload.body,
    icon: payload.icon || '/pwa-192x192.png',
    badge: payload.badge || '/pwa-192x192.png',
    tag: payload.tag,
    data: {
      url: payload.url || '/',
    },
  }

  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const targetUrl = new URL(String(event.notification.data?.url || '/'), self.location.origin).href

  event.waitUntil((async () => {
    const windowClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const matchingClient = windowClients.find((client) => client.url === targetUrl)
    if (matchingClient) {
      await matchingClient.focus()
      return
    }
    await self.clients.openWindow(targetUrl)
  })())
})

function readPushPayload(event: PushEvent): PushPayload {
  if (!event.data) {
    return {}
  }

  try {
    return event.data.json() as PushPayload
  } catch {
    return {
      body: event.data.text(),
    }
  }
}
