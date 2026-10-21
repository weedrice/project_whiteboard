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

test('lightbox keyboard controls zoom, pan, reset, and preserve gallery navigation', async ({ page }) => {
  await page.route('**/image-test/**', (route) => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.setViewportSize({ width: 390, height: 740 })
  await page.goto('/e2e/fixtures/image-states.html?lightboxStates=landscape')
  const image = page.getByRole('dialog').locator('img')
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  const zoomIn = page.getByRole('button', { name: '이미지 확대', exact: true })
  const zoomOut = page.getByRole('button', { name: '이미지 축소', exact: true })
  const reset = page.getByRole('button', { name: '원래 크기', exact: true })
  await zoomIn.focus()
  await page.keyboard.press('Enter')
  await expect(zoomIn).toBeFocused()
  await expect(image).toHaveAttribute('style', /scale\(1\.5\)/)
  await page.keyboard.press('ArrowRight')
  await page.keyboard.press('ArrowDown')
  await expect(image).toHaveAttribute('style', /translate3d\(-48px, -48px, 0px\)/)
  await expect(image).toHaveAttribute('alt', 'Viewer image')
  await reset.focus()
  await page.keyboard.press('Enter')
  await expect(reset).toBeFocused()
  await expect(image).toHaveAttribute('style', /translate3d\(0px, 0px, 0px\) scale\(1\)/)
  await page.keyboard.press('ArrowRight')
  await expect(image).toHaveAttribute('alt', 'Other viewer image')
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  for (let index = 0; index < 6; index += 1) await zoomIn.click()
  await expect(zoomIn).toHaveAttribute('aria-disabled', 'true')
  await expect(image).toHaveAttribute('style', /scale\(4\)/)
  await zoomOut.focus()
  await page.keyboard.press('Enter')
  await expect(image).toHaveAttribute('style', /scale\(3\.5\)/)
  await page.getByRole('button', { name: '다음', exact: true }).click()
  await expect(image).toHaveAttribute('alt', 'Viewer image')
  await expect(image).toHaveAttribute('style', /scale\(1\)/)
})

test('a rotated zoomed gallery image stays in view at the same zoom', async ({ page }) => {
  const largeImage = '<svg xmlns="http://www.w3.org/2000/svg" width="2400" height="1200"><rect width="100%" height="100%" fill="green"/></svg>'
  await page.route('**/image-test/**', (route) => route.fulfill({ contentType: 'image/svg+xml', body: largeImage }))
  await page.setViewportSize({ width: 740, height: 390 })
  await page.goto('/e2e/fixtures/image-states.html?gallery')
  await expect(page.locator('#gallery-first')).toHaveAttribute('data-image-state', 'loaded')
  await page.locator('#gallery-first').click()
  const image = page.getByRole('dialog').locator('img')
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  for (let index = 0; index < 6; index += 1) await page.getByRole('button', { name: '이미지 확대', exact: true }).click()
  for (let index = 0; index < 30; index += 1) await page.keyboard.press('ArrowLeft')
  await page.setViewportSize({ width: 390, height: 740 })
  await expect.poll(async () => {
    const bounds = (await image.boundingBox())!
    return bounds.x < 390 && bounds.x + bounds.width > 0
  }).toBe(true)
  await expect(image).toHaveAttribute('style', /scale\(4\)/)
  // Image-only layout changes must also re-clamp, without a window resize event.
  await image.evaluate((element) => { element.style.width = '200px' })
  await expect.poll(async () => (await image.boundingBox())!.x).toBeLessThan(50)
  await expect(image).toHaveAttribute('style', /scale\(4\)/)
})

test('lightbox controls remain distinct over a light failure image without overlapping retry', async ({ page }) => {
  await page.route('**/image-test/**', (route) => route.fulfill({ status: 503, body: 'failed' }))
  await page.setViewportSize({ width: 390, height: 740 })
  await page.goto('/e2e/fixtures/image-states.html?lightboxStates=landscape')
  await expect(page.getByRole('dialog').locator('img')).toHaveAttribute('data-image-state', 'error')
  const contrast = await page.getByRole('button', { name: '이전', exact: true }).evaluate((button) => {
    const image = document.querySelector('.lightbox-image')!
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 1
    const context = canvas.getContext('2d')!
    context.fillStyle = getComputedStyle(image).backgroundColor
    context.fillRect(0, 0, 1, 1)
    context.fillStyle = getComputedStyle(button).backgroundColor
    context.fillRect(0, 0, 1, 1)
    const background = Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3)
    context.fillStyle = getComputedStyle(button).color
    context.fillRect(0, 0, 1, 1)
    const foreground = Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3)
    const luminance = (rgb: number[]) => rgb.map((value) => {
      const color = value / 255
      return color <= 0.04045 ? color / 12.92 : ((color + 0.055) / 1.055) ** 2.4
    }).reduce((total, value, index) => total + value * [0.2126, 0.7152, 0.0722][index]!, 0)
    return (luminance(foreground) + 0.05) / (luminance(background) + 0.05)
  })
  expect(contrast).toBeGreaterThanOrEqual(3)
  const retryBounds = (await page.getByRole('button', { name: '이미지 다시 불러오기', exact: true }).boundingBox())!
  const zoomIn = page.getByRole('button', { name: '이미지 확대', exact: true })
  const zoomBounds = (await zoomIn.boundingBox())!
  expect(retryBounds.y + retryBounds.height).toBeLessThan(zoomBounds.y)
  await expect(zoomIn).toHaveAttribute('aria-disabled', 'true')
})

test('cancelled drags and native pinches never trigger lightbox navigation', async ({ page, browserName }) => {
  await page.route('**/image-test/**', (route) => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.setViewportSize({ width: 390, height: 740 })
  await page.goto('/e2e/fixtures/image-states.html?lightboxStates=landscape')
  const image = page.getByRole('dialog').locator('img')
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  const bounds = (await image.boundingBox())!
  const y = bounds.y + bounds.height / 2
  await image.evaluate((element) => element.addEventListener('pointerdown', (event) => {
    element.setAttribute('data-test-pointer-id', String((event as PointerEvent).pointerId))
  }, { once: true }))
  await page.mouse.move(100, y)
  await page.mouse.down()
  await page.mouse.move(200, y)
  await image.dispatchEvent('pointercancel', { pointerId: Number(await image.getAttribute('data-test-pointer-id')), clientX: 200, clientY: y })
  await page.mouse.up()
  await expect(image).toHaveAttribute('alt', 'Viewer image')
  if (browserName === 'chromium') {
    const session = await page.context().newCDPSession(page)
    await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: 100, y, id: 1 }, { x: 280, y, id: 2 }] })
    await session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: 190, y, id: 1 }, { x: 200, y, id: 2 }] })
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    await expect(image).toHaveAttribute('alt', 'Viewer image')
    await session.detach()
  }
  await page.mouse.move(200, y)
  await page.mouse.down()
  await page.mouse.move(100, y)
  await page.mouse.up()
  await expect(image).toHaveAttribute('alt', 'Other viewer image')
})

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
  await expect.poll(async () => {
    // The iframe can finish resizing between load and the first native wheel input.
    await page.mouse.wheel(0, 800)
    return frame.locator('body').evaluate((body) =>
      Math.max(body.scrollTop, body.ownerDocument.documentElement.scrollTop),
    )
  }).toBeGreaterThan(0)
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

test('a hidden failed image updates its error label when shown and resized', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/image-test/**', async (route) => {
    await gate
    await route.fulfill({ status: 503, body: 'failed' })
  })
  await page.route('**/api/v1/files/**', (route) => route.abort())
  try {
    await page.goto('/e2e/fixtures/image-states.html', { waitUntil: 'domcontentloaded' })
    const image = page.locator('#photo')
    await expect(image).toHaveAttribute('data-image-state', 'loading')
    await image.evaluate((element) => { element.style.display = 'none' })
    release()
    await expect(image).toHaveAttribute('data-image-state', 'error')
    await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
    const errorSymbol = () => image.evaluate((element) => element.style.getPropertyValue('--nv-image-error-symbol'))
    expect(await errorSymbol()).toBe('')
    await image.evaluate((element) => { element.style.cssText = 'display:block;width:400px;height:200px' })
    await expect.poll(errorSymbol).not.toBe('')
    await image.evaluate((element) => { element.style.width = '100px'; element.style.height = '50px' })
    await expect.poll(errorSymbol).toBe('')
    expect(await image.evaluate((element) => element.style.getPropertyValue('--nv-image-error-symbol-dark'))).toBe('')
    await image.evaluate((element) => { element.style.width = '400px'; element.style.height = '200px' })
    await expect.poll(errorSymbol).not.toBe('')
  } finally {
    release()
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

test('failed images preserve decorative roles and author-provided accessible names through recovery', async ({ page }) => {
  await page.route('**/image-test/accessibility-missing.svg', (route) => route.fulfill({ status: 404, body: '' }))
  await page.route('**/image-test/accessibility-recovered.svg', (route) => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await page.goto('/e2e/fixtures/image-states.html?imageAccessibility')
  const error = '이미지를 불러올 수 없습니다.'
  const decorative = page.locator('#decorative-link')
  await expect(decorative.locator('img')).toHaveAttribute('data-image-state', 'error')
  await expect(decorative).toHaveAccessibleName('Board name')
  expect(await decorative.ariaSnapshot()).not.toContain('img')
  await expect(decorative.locator('img')).not.toHaveAttribute('title')
  await expect(page.locator('#alt-link')).toHaveAccessibleName('Read the guide — ' + error)
  await expect(page.locator('#label-link')).toHaveAccessibleName('Download report — ' + error)
  await expect(page.locator('#reference-link')).toHaveAccessibleName('Reference label')
  await expect(page.locator('#reference-link img')).toHaveAttribute('aria-labelledby', 'image-reference')
  await expect(page.locator('#reference-link img')).toHaveAccessibleDescription(error)
  await page.getByRole('button', { name: 'Recover images', exact: true }).click()
  await expect(decorative.locator('img')).toHaveAttribute('data-image-state', 'loaded')
  await expect(decorative).toHaveAccessibleName('Board name')
  expect(await decorative.ariaSnapshot()).not.toContain('img')
  await expect(page.locator('#alt-link')).toHaveAccessibleName('Read the guide')
  await expect(page.locator('#label-link')).toHaveAccessibleName('Download report')
  await expect(page.locator('#reference-link')).toHaveAccessibleName('Reference label')
  await expect(page.locator('#reference-link img')).not.toHaveAttribute('title')
})

for (const colorScheme of ['light', 'dark'] as const) {
  test(`forced colors ${colorScheme} keeps error text, small icons and keyboard focus visible against the opposite app theme`, async ({ page, browserName }, testInfo) => {
    test.skip(browserName !== 'chromium', 'Windows forced colors are verified in Chromium.')
    let release!: () => void
    const gate = new Promise<void>((resolve) => { release = resolve })
    await page.emulateMedia({ forcedColors: 'active', colorScheme })
    await page.route('**/image-test/contrast-missing.svg', (route) => route.fulfill({ status: 404, body: '' }))
    await page.route('**/image-test/contrast-recovered.svg', async (route) => {
      await gate
      await route.fulfill({ contentType: 'image/svg+xml', body: svg })
    })
    try {
      await page.goto('/e2e/fixtures/image-states.html?errorContrast', { waitUntil: 'domcontentloaded' })
      await page.evaluate((dark) => document.documentElement.classList.toggle('dark', dark), colorScheme === 'light')
      const large = page.locator('#contrast-large')
      const small = page.locator('#contrast-small')
      await expect(large).toHaveAttribute('data-image-state', 'error')
      await expect(small).toHaveAttribute('data-image-state', 'error')
      await expect.poll(() => large.evaluate((image) => image.style.getPropertyValue('--nv-image-error-symbol'))).not.toBe('')
      const foreground = colorScheme === 'light' ? 0 : 255
      const background = 255 - foreground
      // Decode browser screenshots with the browser canvas, without image-processing dependencies.
      const pixels = async (selector: string, filename: string) => {
        const png = await page.locator(selector).screenshot({ path: testInfo.outputPath(filename) })
        return page.evaluate(async ({ data, foreground }) => {
          const bitmap = new Image()
          bitmap.src = data
          await bitmap.decode()
          const canvas = document.createElement('canvas')
          canvas.width = bitmap.width
          canvas.height = bitmap.height
          const context = canvas.getContext('2d')!
          context.drawImage(bitmap, 0, 0)
          const pixel = (x: number, y: number) => Array.from(context.getImageData(x, y, 1, 1).data).slice(0, 3)
          const count = (top: number, bottom: number) => {
            const rgba = context.getImageData(0, top, bitmap.width, bottom - top).data
            let result = 0
            for (let i = 0; i < rgba.length; i += 4) {
              // Allow antialiased glyph edges, still well above 7:1 against the canvas.
              if (Math.abs(rgba[i]! - foreground) <= 64 && Math.abs(rgba[i + 1]! - foreground) <= 64 && Math.abs(rgba[i + 2]! - foreground) <= 64) result++
            }
            return result
          }
          return {
            icon: bitmap.width > 100 ? count(68, 102) : count(0, bitmap.height),
            text: bitmap.width > 100 ? count(102, 140) : 0,
            edge: pixel(Math.floor(bitmap.width / 2), 1),
            empty: pixel(10, 10),
          }
        }, { data: 'data:image/png;base64,' + png.toString('base64'), foreground })
      }
      const normal = await pixels('#contrast-large', 'error-large.png')
      expect(normal.icon).toBeGreaterThan(20)
      expect(normal.text).toBeGreaterThan(20)
      expect(normal.empty).toEqual([background, background, background])
      expect((await pixels('#contrast-small', 'error-small.png')).icon).toBeGreaterThan(5)
      await page.keyboard.press('Tab')
      await expect(large).toBeFocused()
      const focused = await pixels('#contrast-large', 'error-focused.png')
      expect(focused.edge).toEqual([foreground, foreground, foreground])
      expect(focused.empty).toEqual([background, background, background])
      await expect(large).toHaveCSS('border-radius', '8px')
      await expect(large).toHaveCSS('object-fit', 'cover')
      // Switching the app theme while forced colors are active cannot select a low-contrast SVG.
      await page.evaluate(() => document.documentElement.classList.toggle('dark'))
      expect((await pixels('#contrast-small', 'error-small-theme-change.png')).icon).toBeGreaterThan(5)
      await page.getByRole('button', { name: 'Recover contrast images' }).click()
      await expect(large).toHaveAttribute('data-image-state', 'loading')
      await expect(large).toHaveCSS('mask-image', 'none')
      release()
      await expect(large).toHaveAttribute('data-image-state', 'loaded')
      await expect(small).toHaveAttribute('data-image-state', 'loaded')
      await expect(large).toHaveCSS('mask-image', 'none')
      await expect(large).toHaveCSS('forced-color-adjust', 'auto')
      await expect(large).toHaveCSS('border-radius', '8px')
      await expect(large).toHaveCSS('object-fit', 'cover')
    } finally {
      release()
    }
  })
}

test('hover previews recover from undecodable cached blobs on the next hover', async ({ page }) => {
  let requests = 0
  await page.route('**/api/v1/files/876/variants/thumbnail', async route => {
    requests += 1
    await route.fulfill({ contentType: 'image/svg+xml', body: requests === 1 ? 'invalid image bytes' : svg })
  })
  await page.goto('/e2e/fixtures/image-states.html?hoverRecovery')
  const title = page.locator('.nv-post-title-cell')
  const image = page.locator('.nv-post-hover-preview img')
  await title.hover()
  await expect(image).toHaveAttribute('data-image-state', 'error')
  expect(requests).toBe(1)
  await page.mouse.move(0, 500)
  await expect(image).toHaveCount(0)
  await title.hover()
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  expect(requests).toBe(2)
  await page.mouse.move(0, 500)
  await expect(image).toHaveCount(0)
  await title.hover()
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  expect(requests).toBe(2)
})
