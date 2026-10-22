import { expect, test } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="60"><rect width="100" height="60" fill="#16a34a"/></svg>'

function gate() {
  let release!: () => void
  const promise = new Promise<void>(resolve => { release = resolve })
  return { promise, release }
}

test('defers offscreen inquiry images and shares four download slots across messages', async ({ page }) => {
  const first = gate()
  const rest = gate()
  const calls: number[] = []
  const nativeRequests: string[] = []
  page.on('request', request => {
    if (request.url().includes('/api/v1/files/') && request.resourceType() === 'image') nativeRequests.push(request.url())
  })
  await page.route('**/api/v1/files/*', async route => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1))
    calls.push(id)
    await (calls.length === 1 ? first.promise : rest.promise)
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  try {
    await page.goto('/e2e/fixtures/inquiry-image-loading.html')
    await expect(page.locator('section img')).toHaveCount(12)
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    expect(calls).toEqual([])
    await page.locator('#reveal').click()
    await expect.poll(() => calls.length).toBe(4)
    expect(new Set(calls).size).toBe(4)
    first.release()
    await expect.poll(() => calls.length).toBe(5)
    expect(new Set(calls).size).toBe(5)
    rest.release()
    await expect(page.locator('section img[data-image-state="loaded"]')).toHaveCount(12)
    expect(calls).toHaveLength(12)
    expect(nativeRequests).toEqual([])
  } finally {
    first.release()
    rest.release()
  }
})

test('cancels waiting inquiry images when the session changes', async ({ page }) => {
  const delayed = gate()
  const calls: string[] = []
  await page.route('**/api/v1/files/*', async route => {
    calls.push(route.request().url())
    await delayed.promise
    try {
      await route.fulfill({ contentType: 'image/svg+xml', body: svg })
    } catch {
      // Requests are cancelled by the component's session scope.
    }
  })
  try {
    await page.goto('/e2e/fixtures/inquiry-image-loading.html')
    await page.locator('#reveal').click()
    await expect.poll(() => calls.length).toBe(4)
    await page.locator('#session').click()
    await expect(page.locator('section img[data-image-state="error"]')).toHaveCount(12)
    delayed.release()
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    expect(calls).toHaveLength(4)
    await expect(page.locator('section img[src]')).toHaveCount(0)
  } finally {
    delayed.release()
  }
})
