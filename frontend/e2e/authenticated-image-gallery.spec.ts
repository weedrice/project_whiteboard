import { expect, test, type Page } from '@playwright/test'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="200"><rect width="400" height="200" fill="#16a34a"/></svg>'
const fixture = '/e2e/fixtures/authenticated-image-gallery.html'

function gate() {
  let release!: () => void
  const promise = new Promise<void>((resolve) => { release = resolve })
  return { promise, release }
}

async function openGallery(page: Page) {
  const first = page.locator('article img').first()
  await expect(first).toHaveAttribute('data-image-state', 'loaded')
  await first.click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('status')).toContainText('전체 21개 중 1번째')
}

async function flushBrowserFrames(page: Page) {
  await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
}

function trackNativeFileRequests(page: Page) {
  const requests: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/files/') && request.resourceType() === 'image') requests.push(request.url())
  })
  return requests
}

test('loads only the visible protected image and fetches a selected offscreen gallery member without scrolling', async ({ page }) => {
  const selected = gate()
  const calls: number[] = []
  const nativeRequests = trackNativeFileRequests(page)
  await page.route('**/api/v1/files/*', async (route) => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1))
    calls.push(id)
    if (id === 9002) await selected.promise
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  try {
    await page.goto(fixture, { waitUntil: 'domcontentloaded' })
    await openGallery(page)
    expect(calls).toEqual([9001])
    await expect(page.locator('article img')).toHaveCount(21)
    expect((await page.locator('article img').nth(1).boundingBox())!.y).toBeGreaterThan(12000)
    await page.getByRole('button', { name: '다음', exact: true }).click()
    const current = page.getByRole('dialog').locator('img')
    await expect(current).toHaveAttribute('alt', 'Protected 2')
    await expect(current).toHaveAttribute('data-image-state', 'loading')
    await expect(page.getByRole('status')).toContainText('전체 21개 중 2번째')
    await expect.poll(() => calls).toEqual([9001, 9002])
    expect(await page.evaluate(() => window.scrollY)).toBe(0)
    selected.release()
    await expect(current).toHaveAttribute('data-image-state', 'loaded')
    await expect(current).toHaveAttribute('src', /^blob:/)
    expect(await current.getAttribute('src')).toBe(await page.locator('article img').nth(1).getAttribute('src'))
    expect(calls).toEqual([9001, 9002])
    expect(nativeRequests).toEqual([])
    expect(await page.evaluate(() => window.scrollY)).toBe(0)
  } finally {
    selected.release()
  }
})

test('retries a failed selected protected image once and keeps the gallery position', async ({ page }) => {
  let attempts = 0
  const retry = gate()
  const nativeRequests = trackNativeFileRequests(page)
  await page.route('**/api/v1/files/*', async (route) => {
    if (route.request().url().endsWith('/9002')) {
      attempts += 1
      if (attempts === 1) {
        await route.fulfill({ status: 503, body: 'temporarily unavailable' })
        return
      }
      await retry.promise
    }
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  try {
    await page.goto(fixture, { waitUntil: 'domcontentloaded' })
    await openGallery(page)
    await page.getByRole('button', { name: '다음', exact: true }).click()
    const current = page.getByRole('dialog').locator('img')
    await expect(current).toHaveAttribute('data-image-state', 'error')
    await expect(page.getByRole('status')).toContainText('전체 21개 중 2번째')
    await page.getByRole('button', { name: '이미지 다시 불러오기', exact: true }).click()
    await expect.poll(() => attempts).toBe(2)
    await expect(current).toHaveAttribute('data-image-state', 'loading')
    await expect(page.getByRole('button', { name: '이미지를 다시 불러오는 중', exact: true })).toHaveAttribute('aria-disabled', 'true')
    retry.release()
    await expect(current).toHaveAttribute('data-image-state', 'loaded')
    await expect(current).toHaveAttribute('alt', 'Protected 2')
    await expect(page.getByRole('status')).toContainText('전체 21개 중 2번째')
    expect(attempts).toBe(2)
    expect(nativeRequests).toEqual([])
  } finally {
    retry.release()
  }
})

for (const kind of ['authenticated', 'native'] as const) {
  for (const activation of ['click', 'keyboard'] as const) {
    test(`opens a sole failed ${kind} image by ${activation} and retries successfully`, async ({ page }) => {
      let attempts = 0
      const retry = gate()
      const protectedRequests = trackNativeFileRequests(page)
      const failedAttempts = kind === 'native' ? 2 : 1
      await page.route(kind === 'native' ? '**/image-test/single-native.svg' : '**/api/v1/files/9001', async (route) => {
        attempts += 1
        if (attempts <= failedAttempts) await route.fulfill({ status: 503, body: 'temporarily unavailable' })
        else {
          await retry.promise
          await route.fulfill({ contentType: 'image/svg+xml', body: svg })
        }
      })
      try {
        await page.goto(fixture + '?single' + (kind === 'native' ? '&native' : ''), { waitUntil: 'domcontentloaded' })
        const original = page.locator('article img')
        await expect(original).toHaveAttribute('data-image-state', 'error')
        await expect(original).toHaveAttribute('tabindex', '0')
        await expect(original).toHaveAttribute('role', 'button')
        if (activation === 'click') await original.click()
        else {
          await page.keyboard.press('Tab')
          await expect(original).toBeFocused()
          await page.keyboard.press('Enter')
        }
        const current = page.getByRole('dialog').locator('img')
        await expect(current).toHaveAttribute('data-image-state', 'error')
        await expect(page.getByRole('status')).toContainText('전체 1개 중 1번째')
        await page.getByRole('button', { name: '이미지 다시 불러오기', exact: true }).focus()
        await page.keyboard.press('Enter')
        const pending = page.getByRole('button', { name: '이미지를 다시 불러오는 중', exact: true })
        await expect(pending).toBeFocused()
        await expect(pending).toHaveAttribute('aria-disabled', 'true')
        await page.keyboard.press('Enter')
        await expect.poll(() => attempts).toBe(failedAttempts + 1)
        retry.release()
        await expect(current).toHaveAttribute('data-image-state', 'loaded')
        await expect(page.getByRole('button', { name: '닫기', exact: true })).toBeFocused()
        if (kind === 'authenticated') expect(protectedRequests).toEqual([])
        await page.keyboard.press('Escape')
        await expect(page.getByRole('dialog')).toHaveCount(0)
        await expect(original).toBeFocused()
        expect(attempts).toBe(failedAttempts + 1)
      } finally {
        retry.release()
      }
    })
  }
}

test('defers protected images inside collapsed details until the user expands them', async ({ page }) => {
  const requests: string[] = []
  await page.route('**/api/v1/files/*', async (route) => {
    requests.push(route.request().url())
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  await page.goto(fixture + '?collapsed', { waitUntil: 'domcontentloaded' })
  const first = page.locator('article img').first()
  await expect(page.locator('article img')).toHaveCount(21)
  await expect(first).toHaveAttribute('data-image-state', 'loading')
  await expect(first).toBeHidden()
  await flushBrowserFrames(page)
  expect(requests).toEqual([])
  await page.getByText('Collapsed photos', { exact: true }).click()
  await expect(first).toHaveAttribute('data-image-state', 'loaded')
  expect(requests).toHaveLength(1)
  expect(requests[0]).toMatch(/9001$/)
})

test('linked protected images preserve new-tab click and keyboard navigation without opening the gallery', async ({ page, context }) => {
  await page.route('**/api/v1/files/*', (route) => route.fulfill({ contentType: 'image/svg+xml', body: svg }))
  await context.route('**/linked-destination', (route) => route.fulfill({ contentType: 'text/html', body: '<p>Linked destination</p>' }))
  await page.goto(fixture + '?linked', { waitUntil: 'domcontentloaded' })
  const image = page.locator('article img')
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  for (const action of ['click', 'keyboard']) {
    const popupPromise = page.waitForEvent('popup')
    if (action === 'click') await image.click()
    else await page.locator('article a').press('Enter')
    const popup = await popupPromise
    await expect(popup).toHaveURL(/linked-destination$/)
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await popup.close()
  }
})

for (const boundary of ['session', 'content'] as const) {
  test('closes the protected gallery and discards an old download after a ' + boundary + ' replacement', async ({ page }) => {
    const delayed = gate()
    const finished = gate()
    let delayedRequests = 0
    await page.route('**/api/v1/files/*', async (route) => {
      if (route.request().url().endsWith('/9002')) {
        delayedRequests += 1
        await delayed.promise
        try {
          await route.fulfill({ contentType: 'image/svg+xml', body: svg })
        } catch {
          // The production loader cancels the request when its owner is replaced.
        } finally {
          finished.release()
        }
        return
      }
      await route.fulfill({ contentType: 'image/svg+xml', body: svg })
    })
    try {
      await page.goto(fixture, { waitUntil: 'domcontentloaded' })
      await openGallery(page)
      await page.getByRole('button', { name: '다음', exact: true }).click()
      await expect.poll(() => delayedRequests).toBe(1)
      await expect(page.getByRole('dialog').locator('img')).toHaveAttribute('data-image-state', 'loading')
      const original = await page.locator('article img').first().getAttribute('src')
      await page.evaluate((kind) => {
        const fixture = (window as unknown as { authGalleryFixture: { switchSession: () => void; replaceContent: () => void } }).authGalleryFixture
        if (kind === 'session') fixture.switchSession()
        else fixture.replaceContent()
      }, boundary)
      await expect(page.getByRole('dialog')).toHaveCount(0)
      if (boundary === 'session') await expect(page.locator('article img').first()).toHaveAttribute('data-image-state', 'loaded')
      else await expect(page.locator('article')).toContainText('Replacement content')
      const before = await page.evaluate(() => (window as unknown as { authGalleryFixture: { created: string[]; revoked: string[] } }).authGalleryFixture.created.length)
      delayed.release()
      await finished.promise
      await flushBrowserFrames(page)
      const after = await page.evaluate(() => {
        const fixture = (window as unknown as { authGalleryFixture: { created: string[]; revoked: string[] } }).authGalleryFixture
        return { created: fixture.created.length, revoked: fixture.revoked }
      })
      expect(after.created).toBe(before)
      expect(after.revoked).toContain(original)
      await expect(page.getByRole('dialog')).toHaveCount(0)
      if (boundary === 'session') await expect(page.locator('article img').nth(1)).not.toHaveAttribute('src')
    } finally {
      delayed.release()
    }
  })
}
