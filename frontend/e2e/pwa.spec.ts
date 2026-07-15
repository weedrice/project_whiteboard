import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test.use({ serviceWorkers: 'allow' })

test('PWA provides a cold offline app shell and an offline page for uncached direct paths', async ({ page, context }) => {
  await installMockApi(page)
  await page.goto('/')
  await page.evaluate(async () => navigator.serviceWorker?.ready)

  await page.evaluate(async () => {
    const cacheNames = await caches.keys()
    await Promise.all(
      cacheNames
        .filter((cacheName) => !cacheName.includes('precache'))
        .map((cacheName) => caches.delete(cacheName)),
    )
  })
  const cdpSession = await context.newCDPSession(page)
  await cdpSession.send('Network.clearBrowserCache')
  await page.close()

  await context.setOffline(true)
  const offlinePage = await context.newPage()
  await offlinePage.goto('/')
  await expect(offlinePage.locator('#app')).toBeAttached()
  await expect(offlinePage.locator('#app > *').first()).toBeAttached()

  await offlinePage.goto('/never-visited-offline')
  await expect(offlinePage).toHaveTitle(/Offline/)
})
