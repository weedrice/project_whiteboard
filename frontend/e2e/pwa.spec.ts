import { readdirSync } from 'node:fs'
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
  await expect(offlinePage.getByRole('heading', { level: 1 })).toHaveCSS('font-size', '24px')
  await expect(offlinePage.locator('main')).toHaveCSS('max-width', '420px')
})


test('PWA repairs legacy HTML cache entries and preserves healthy offline assets', async ({ page, context }) => {
  const chunkFile = readdirSync(new URL('../dist/js/', import.meta.url))
    .find((name) => /^AdminManagement-[A-Za-z0-9_-]+\.js$/.test(name))
  expect(chunkFile).toBeDefined()
  const chunkPath = `/js/${chunkFile}`
  const offlinePath = '/js/legacy-offline-AbCdEfGh.js'
  const missingPath = '/js/missing-runtime-AbCdEfGh.js'

  // A static page avoids registering the app worker before the legacy cache is seeded.
  await page.goto('/robots.txt')
  await page.evaluate(async ({ chunkPath, offlinePath }) => {
    const cache = await caches.open('noviis-static-v1')
    await cache.put(chunkPath, new Response('<html>legacy fallback</html>', {
      headers: { 'Content-Type': 'text/html' },
    }))
    await cache.put(offlinePath, new Response('healthy offline asset', {
      headers: { 'Content-Type': 'application/javascript' },
    }))
    await (await caches.open('unrelated-cache')).put('/unrelated-entry', new Response('keep'))
    await navigator.serviceWorker.register('/service-worker.js')
    await navigator.serviceWorker.ready
  }, { chunkPath, offlinePath })
  await page.waitForFunction(() => navigator.serviceWorker.controller != null)

  const migrated = await page.evaluate(async ({ chunkPath, offlinePath }) => {
    const cache = await caches.open('noviis-static-v1')
    return {
      invalid: Boolean(await cache.match(chunkPath)),
      valid: await (await cache.match(offlinePath))?.text(),
      unrelated: await (await (await caches.open('unrelated-cache')).match('/unrelated-entry'))?.text(),
    }
  }, { chunkPath, offlinePath })
  expect(migrated).toEqual({ invalid: false, valid: 'healthy offline asset', unrelated: 'keep' })

  // Old tabs can still write the legacy cache while a new worker takes control.
  await page.evaluate(async (chunkPath) => {
    await (await caches.open('noviis-static-v1')).put(chunkPath, new Response('<html>late fallback</html>', {
      headers: { 'Content-Type': 'text/html; charset=utf-8' },
    }))
  }, chunkPath)
  const restoredType = await page.evaluate(async (chunkPath) => (await fetch(chunkPath)).headers.get('Content-Type'), chunkPath)
  expect(restoredType).toMatch(/javascript/)
  await expect.poll(() => page.evaluate(async (chunkPath) =>
    (await (await caches.open('noviis-static-v1')).match(chunkPath))?.headers.get('Content-Type'), chunkPath),
  ).toMatch(/javascript/)

  // Vite preview reproduces the old Nginx SPA fallback for an absent JS path.
  const fallbackType = await page.evaluate(async (missingPath) => (await fetch(missingPath)).headers.get('Content-Type'), missingPath)
  expect(fallbackType).toContain('text/html')
  expect(await page.evaluate(async (missingPath) => Boolean(
    await (await caches.open('noviis-static-v1')).match(missingPath),
  ), missingPath)).toBe(false)

  await context.setOffline(true)
  expect(await page.evaluate(async (offlinePath) => (await fetch(offlinePath)).text(), offlinePath))
    .toBe('healthy offline asset')
  expect(await page.evaluate(async (chunkPath) => (await fetch(chunkPath)).headers.get('Content-Type'), chunkPath))
    .toMatch(/javascript/)
})
