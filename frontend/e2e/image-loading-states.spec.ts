import { expect, test } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="200"><rect width="400" height="200" fill="#16a34a"/></svg>'

test('delayed images preserve sizes, finish on native load, and handle replacement failure', async ({ page }, testInfo) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/image-test/slow.svg', async (route) => {
    await gate
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  await page.route('**/api/v1/files/123', async (route) => {
    await gate
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  await page.route('**/image-test/replacement.svg', (route) => route.fulfill({ status: 404, body: 'missing' }))
  await page.goto('/e2e/fixtures/image-states.html', { waitUntil: 'domcontentloaded' })
  const photo = page.locator('#photo')
  await expect(photo).toHaveAttribute('data-image-state', 'loading')
  await expect(page.locator('#protected img')).toHaveAttribute('data-image-state', 'loading')
  expect((await photo.boundingBox())?.width).toBe(400)
  expect((await photo.boundingBox())?.height).toBe(200)
  expect((await page.locator('#avatar').boundingBox())?.width).toBe(32)
  expect((await page.locator('#emoticon').boundingBox())?.width).toBe(24)
  expect((await page.locator('#logo').boundingBox())?.width).toBe(128)
  expect(await photo.evaluate((image) => getComputedStyle(image).content)).toContain('data:image/svg+xml')
  const emoticonSize = await page.locator('#rich .ql-emoticon').boundingBox()
  expect(emoticonSize?.width).toBeLessThanOrEqual(128)
  expect(emoticonSize?.height).toBeLessThanOrEqual(128)
  // WebKit screenshot capture can wait for the intentionally held image request.
  if (testInfo.project.name === 'chromium') {
    await page.screenshot({ path: testInfo.outputPath('loading.png'), animations: 'disabled' })
  }

  release()
  await expect(photo).toHaveAttribute('data-image-state', 'loaded')
  await expect(page.locator('#protected img')).toHaveAttribute('data-image-state', 'loaded')
  expect(await photo.evaluate((image) => getComputedStyle(image).content)).toBe('normal')
  await page.locator('#lazy').scrollIntoViewIfNeeded()
  await expect(page.locator('#lazy')).toHaveAttribute('data-image-state', 'loaded')
  await page.locator('#replace').click()
  await expect(photo).toHaveAttribute('data-image-state', 'error')
  await expect(photo).not.toHaveAttribute('aria-busy')
  await expect(photo).toHaveAttribute('title', '이미지를 불러올 수 없습니다.')
  expect(await photo.evaluate((image) => getComputedStyle(image).content)).toContain('data:image/svg+xml')
  if (testInfo.project.name === 'chromium') {
    await page.screenshot({ path: testInfo.outputPath('failure.png'), animations: 'disabled' })
  }
})

test('mobile reduced motion and authenticated failure terminate without broken-image rendering', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 })
  await page.emulateMedia({ reducedMotion: 'reduce', colorScheme: 'dark' })
  await page.route('**/image-test/slow.svg', (route) => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.route('**/api/v1/files/123', (route) => route.fulfill({ status: 403, body: '{}' }))
  await page.goto('/e2e/fixtures/image-states.html')
  await expect(page.locator('#photo')).toHaveAttribute('data-image-state', 'loaded')
  await expect(page.locator('#protected img')).toHaveAttribute('data-image-state', 'error')
  expect((await page.locator('#photo').boundingBox())?.width).toBeLessThanOrEqual(360)
  await page.locator('#photo').evaluate((image) => { image.setAttribute('data-image-state', 'loading') })
  await expect(page.locator('#photo')).toHaveCSS('animation-name', 'none')
})
