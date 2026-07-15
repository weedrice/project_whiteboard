/// <reference lib="webworker" />

import { clientsClaim } from 'workbox-core'
import { NavigationRoute, registerRoute } from 'workbox-routing'
import { CacheFirst, NetworkOnly } from 'workbox-strategies'
import { ExpirationPlugin } from 'workbox-expiration'
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

registerRoute(
  ({ url }) => url.origin === self.location.origin
    && /^\/(?:js|assets|img|fonts)\/.*-[A-Za-z0-9_-]{8,}\.[A-Za-z0-9]+$/.test(url.pathname),
  new CacheFirst({
    cacheName: 'noviis-static-v1',
    plugins: [new ExpirationPlugin({ maxEntries: 150, maxAgeSeconds: 30 * 24 * 60 * 60 })],
  }),
)

const appShellHandler = createHandlerBoundToURL('/index.html')
const offlineHandler = createHandlerBoundToURL('/offline.html')
const navigationRoute = new NavigationRoute(async (options) => {
  try {
    return await fetch(options.request)
  } catch {
    const pathname = new URL(options.request.url).pathname
    return pathname === '/' || pathname === '/index.html'
      ? appShellHandler(options)
      : offlineHandler(options)
  }
}, {
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
