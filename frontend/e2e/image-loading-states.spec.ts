import { expect, test, type FrameLocator, type Page } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="200"><rect width="400" height="200" fill="#16a34a"/></svg>'

for (const shape of ['portrait', 'landscape']) {
  test(`the lightbox preserves its ${shape} bounds through loading`, async ({ page }) => {
    const width = shape === 'portrait' ? 300 : 600
    const height = shape === 'portrait' ? 600 : 300
    let release!: () => void
    const gate = new Promise<void>((resolve) => { release = resolve })
    await page.route('**/image-test/lightbox-*.svg*', async (route) => {
      await gate
      await route.fulfill({ contentType: 'image/svg+xml', body: `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="green"/></svg>` })
    })
    try {
      await page.setViewportSize({ width: 390, height: 740 })
      await page.goto(`/e2e/fixtures/image-states.html?lightboxStates=${shape}`, { waitUntil: 'domcontentloaded' })
      const image = page.getByRole('dialog').locator('img')
      await expect(image).toHaveAttribute('data-image-state', 'loading')
      const before = (await image.boundingBox())!
      expect(before.width / before.height).toBeCloseTo(width / height, 2)
      expect(before.width).toBeLessThanOrEqual(390)
      expect(before.height).toBeLessThanOrEqual(740 * 0.88)
      await expect(page.getByRole('status')).toContainText('전체 2개 중 1번째 이미지를 불러오는 중입니다.')
      release()
      await expect(image).toHaveAttribute('data-image-state', 'loaded')
      const after = (await image.boundingBox())!
      expect(after.width).toBeCloseTo(before.width, 1)
      expect(after.height).toBeCloseTo(before.height, 1)
      await expect(page.getByRole('status')).toContainText('전체 2개 중 1번째 이미지가 표시되었습니다.')
    } finally {
      release()
    }
  })
}

test('the lightbox retries a failed URL without cache busting and keeps keyboard focus', async ({ page }) => {
  const requests: string[] = []
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/image-test/lightbox-retry.svg*', async (route) => {
    requests.push(route.request().url())
    if (requests.length === 1) await route.fulfill({ status: 503, body: 'temporary failure' })
    else {
      await gate
      await route.fulfill({ contentType: 'image/svg+xml', body: svg })
    }
  })
  try {
    await page.goto('/e2e/fixtures/image-states.html?lightboxStates=retry', { waitUntil: 'domcontentloaded' })
    const image = page.getByRole('dialog').locator('img')
    await expect(image).toHaveAttribute('data-image-state', 'error')
    await expect(page.getByRole('status')).toContainText('전체 2개 중 1번째 이미지를 불러올 수 없습니다.')
    const retry = page.getByRole('button', { name: '이미지 다시 불러오기', exact: true })
    await retry.focus()
    await page.keyboard.press('Enter')
    const pendingRetry = page.getByRole('button', { name: '이미지를 다시 불러오는 중', exact: true })
    await expect(pendingRetry).toHaveAttribute('aria-disabled', 'true')
    await expect(pendingRetry).toBeFocused()
    await page.keyboard.press('Enter')
    await expect.poll(() => requests.length).toBe(2)
    expect(requests[0]).toBe(requests[1])
    expect(new URL(requests[1]!).search).toBe('?signature=preserved')
    await expect(image).toHaveAttribute('data-image-state', 'loading')
    release()
    await expect(image).toHaveAttribute('data-image-state', 'loaded')
    await expect(page.getByRole('status')).toContainText('전체 2개 중 1번째 이미지가 표시되었습니다.')
    await expect(page.getByRole('button', { name: '닫기', exact: true })).toBeFocused()
    expect(requests).toHaveLength(2)
  } finally {
    release()
  }
})

test('an inquiry image retries its authenticated download and exposes the decoded blob link', async ({ page }) => {
  let requests = 0
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/files/765', async (route) => {
    requests += 1
    if (requests === 1) await route.fulfill({ status: 503, body: 'temporary failure' })
    else {
      await gate
      await route.fulfill({ contentType: 'image/svg+xml', body: svg })
    }
  })
  try {
    await page.goto('/e2e/fixtures/image-states.html?inquiryRetry', { waitUntil: 'domcontentloaded' })
    const image = page.getByAltText('Retry inquiry image')
    await expect(image).toHaveAttribute('data-image-state', 'error')
    await page.getByRole('button', { name: '이미지 다시 불러오기', exact: true }).focus()
    await page.keyboard.press('Enter')
    const pendingRetry = page.getByRole('button', { name: '이미지를 다시 불러오는 중', exact: true })
    await expect(pendingRetry).toHaveAttribute('aria-disabled', 'true')
    await expect(pendingRetry).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(image).toHaveAttribute('data-image-state', 'loading')
    await expect.poll(() => requests).toBe(2)
    release()
    await expect(image).toHaveAttribute('data-image-state', 'loaded')
    await expect(page.locator('a')).toHaveAttribute('href', /^blob:/)
    await expect(page.locator('a')).toBeFocused()
    expect(await image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBe(400)
    expect(requests).toBe(2)
  } finally {
    release()
  }
})

async function expectSandboxContentScrollable(page: Page, frame: FrameLocator) {
  await expect(frame.locator('html')).toHaveCSS('overflow-y', 'auto')
  await expect(frame.locator('body')).toHaveCSS('overflow-y', 'auto')
  const overflow = await frame.locator('body').evaluate((body) => {
    const root = body.ownerDocument.documentElement
    return Math.max(root.scrollHeight - root.clientHeight, body.scrollHeight - body.clientHeight)
  })
  expect(overflow).toBeGreaterThan(0)
  const bounds = (await page.locator('#sandbox-bridge-frame').boundingBox())!
  await page.mouse.move(bounds.x + 80, bounds.y + 80)
  await page.mouse.wheel(0, 800)
  await expect.poll(() => frame.locator('body').evaluate((body) =>
    Math.max(body.scrollTop, body.ownerDocument.documentElement.scrollTop),
  )).toBeGreaterThan(0)
}

test('a blocked sandbox bridge restores decoded images with a single fallback navigation', async ({ page }) => {
  await page.route('**/sandbox-height-bridge.js*', (route) => route.abort())
  await page.goto('/e2e/fixtures/image-states.html?sandboxBridge', { waitUntil: 'domcontentloaded' })
  const frame = page.frameLocator('#sandbox-bridge-frame')
  const image = frame.getByAltText('Sandbox bridge recovery image')
  await expect(page.locator('#sandbox-load-count')).toHaveText('2')
  await expect(frame.getByText('Sandbox bridge recovery content')).toBeVisible()
  await expect(image).not.toHaveAttribute('data-image-state')
  await expect(image).not.toHaveAttribute('aria-busy')
  await expect(image).toHaveCSS('content', 'normal')
  await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.complete && element.naturalWidth)).toBe(320)
  await expectSandboxContentScrollable(page, frame)
  const fallbackSource = await page.locator('#sandbox-bridge-frame').getAttribute('srcdoc')
  // Observe another full watchdog interval to reject repeated fallback reloads.
  await page.waitForTimeout(1250)
  await expect(page.locator('#sandbox-load-count')).toHaveText('2')
  await expect(page.locator('#sandbox-bridge-frame')).toHaveAttribute('srcdoc', fallbackSource!)
})

for (const delayed of [false, true]) {
  test(`a ${delayed ? 'delayed' : 'successful'} sandbox bridge retains its original document`, async ({ page }) => {
    let release!: () => void
    const gate = new Promise<void>((resolve) => { release = resolve })
    let bridgeRequested!: () => void
    const requested = new Promise<void>((resolve) => { bridgeRequested = resolve })
    if (delayed) {
      await page.route('**/sandbox-height-bridge.js*', async (route) => {
        bridgeRequested()
        await gate
        await route.continue()
      })
    }
    try {
      await page.goto('/e2e/fixtures/image-states.html?sandboxBridge', { waitUntil: 'domcontentloaded' })
      const iframe = page.locator('#sandbox-bridge-frame')
      const frame = page.frameLocator('#sandbox-bridge-frame')
      const image = frame.getByAltText('Sandbox bridge recovery image')
      await expect(iframe).toHaveCount(1)
      const originalSource = await iframe.getAttribute('srcdoc')
      if (delayed) {
        await requested
        await expect(image).toHaveAttribute('data-image-state', 'loading')
        // Network delay before iframe load must not consume the watchdog grace period.
        await page.waitForTimeout(1250)
        await expect(page.locator('#sandbox-load-count')).toHaveText('0')
        await expect(iframe).toHaveAttribute('srcdoc', originalSource!)
        release()
      }
      await expect(image).toHaveAttribute('data-image-state', 'loaded')
      await expect(image).not.toHaveAttribute('aria-busy')
      await expect(image).toHaveCSS('content', 'normal')
      await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.complete && element.naturalWidth)).toBe(320)
      await expect(frame.getByText('Sandbox bridge recovery content')).toBeVisible()
      await expect(page.locator('#sandbox-load-count')).toHaveText('1')
      await expectSandboxContentScrollable(page, frame)
      await page.waitForTimeout(1250)
      await expect(page.locator('#sandbox-load-count')).toHaveText('1')
      await expect(iframe).toHaveAttribute('srcdoc', originalSource!)
    } finally {
      release()
    }
  })
}

test('the lightbox next button loads an offscreen lazy image without scrolling the article', async ({ page, browserName }) => {
  let requestedLazyImage = false
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/image-test/gallery-first.svg', (route) => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.route('**/image-test/gallery-lazy.svg', async (route) => {
    requestedLazyImage = true
    await gate
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  try {
    await page.goto('/e2e/fixtures/image-states.html?gallery', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('#gallery-first')).toHaveAttribute('data-image-state', 'loaded')
    await expect(page.locator('#gallery-lazy')).toHaveAttribute('data-image-state', 'loading')
    expect((await page.locator('#gallery-lazy').boundingBox())!.y).toBeGreaterThan(page.viewportSize()!.height)
    // WebKit may prefetch an offscreen lazy image; Chromium waits for the viewer.
    if (browserName === 'chromium') expect(requestedLazyImage).toBe(false)

    await page.locator('#gallery-first').click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('1 / 2')
    await dialog.getByRole('button', { name: '다음', exact: true }).click()
    await expect(dialog.locator('img')).toHaveAttribute('alt', 'gallery-lazy')
    await expect(dialog.locator('img')).toHaveAttribute('data-image-state', 'loading')
    await expect.poll(() => requestedLazyImage).toBe(true)
    expect(await page.evaluate(() => window.scrollY)).toBe(0)

    release()
    await expect(dialog.locator('img')).toHaveAttribute('data-image-state', 'loaded')
    expect(await dialog.locator('img').evaluate((image: HTMLImageElement) => image.naturalWidth)).toBe(400)
    await expect(dialog).toContainText('2 / 2')
  } finally {
    release()
  }
})

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
  await expect(page.locator('#lazy')).toHaveAttribute('data-image-offscreen', '')
  await expect(page.locator('#lazy')).toHaveCSS('animation-play-state', 'paused')
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
  await expect(page.locator('#lazy')).not.toHaveAttribute('data-image-offscreen')
  await page.locator('#replace').click()
  await expect(photo).toHaveAttribute('data-image-state', 'error')
  await expect(photo).not.toHaveAttribute('aria-busy')
  await expect(photo).toHaveAttribute('title', '이미지를 불러올 수 없습니다.')
  expect(await photo.evaluate((image) => getComputedStyle(image).content)).toContain('data:image/svg+xml')
  const errorBackground = () => photo.evaluate((image) => decodeURIComponent(getComputedStyle(image).backgroundImage))
  await expect.poll(errorBackground).toContain('#3a3a3a')
  await page.evaluate(() => document.documentElement.classList.add('dark'))
  await expect.poll(errorBackground).toContain('#c8c7c2')
  await page.emulateMedia({ colorScheme: 'dark' })
  await page.evaluate(() => document.documentElement.classList.remove('dark'))
  await expect.poll(errorBackground).toContain('#3a3a3a')
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
