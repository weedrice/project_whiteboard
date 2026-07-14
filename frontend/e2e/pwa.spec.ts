import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test.use({ serviceWorkers: 'allow' })

test('PWA provides the app shell at root and an offline page for uncached direct paths', async ({ page, context }) => {
  await installMockApi(page)
  await page.goto('/')
  await page.evaluate(async () => navigator.serviceWorker?.ready)
  await page.reload()

  await context.setOffline(true)
  await page.goto('/')
  await expect(page.locator('#app')).toBeAttached()

  await page.goto('/never-visited-offline')
  await expect(page).toHaveTitle(/Offline/)
})
