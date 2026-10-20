import { expect, test, type Page } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="200"><rect width="400" height="200" fill="#16a34a"/></svg>'
const imageSelector = '.ProseMirror img[alt^="Private "]'

async function setupImageDocument(page: Page) {
  const postContents = Array.from({ length: 80 }, (_, index) => `<p>Introductory paragraph ${index}</p>`).join('')
    + Array.from({ length: 12 }, (_, index) => `<img src="/api/v1/files/${7100 + index}" alt="Private ${index}" loading="lazy" width="400" height="200">`).join('')
  await installMockApi(page, { postContents })
}

async function openImageDocument(page: Page) {
  await login(page)
  await page.goto('/board/general/post/1/edit', { waitUntil: 'domcontentloaded' })
  await expect(page.locator(imageSelector)).toHaveCount(12)
}

async function frames(page: Page) {
  await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
}

test('defers twelve offscreen editor images and limits visible downloads to four', async ({ page }) => {
  await setupImageDocument(page)
  const calls: number[] = []
  const releases = new Map<number, () => void>()
  let released = false
  await page.route(/\/api\/v1\/files\/71\d\d(?:\?|$)/, async route => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1))
    calls.push(id)
    if (!released) await new Promise<void>(resolve => { releases.set(id, resolve) })
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  try {
    await openImageDocument(page)
    await frames(page)
    const images = page.locator(imageSelector)
    expect((await images.first().boundingBox())!.y).toBeGreaterThan(2000)
    expect(calls).toEqual([])
    await images.first().scrollIntoViewIfNeeded()
    await expect.poll(() => calls.length).toBe(4)
    await frames(page)
    expect(calls).toHaveLength(4)
    const completedId = calls[0]!
    releases.get(completedId)!()
    await expect(page.locator('.ProseMirror img[alt="Private ' + (completedId - 7100) + '"]')).toHaveAttribute('data-image-state', 'loaded')
    await expect.poll(() => calls.length).toBe(5)
    await frames(page)
    expect(calls).toHaveLength(5)
  } finally {
    released = true
    releases.forEach(release => release())
  }
})

test('deleting a downloading editor image cancels its request and admits a waiting image', async ({ page }) => {
  await setupImageDocument(page)
  const calls: number[] = []
  const failedRequests: number[] = []
  const releases: Array<() => void> = []
  let released = false
  page.on('requestfailed', request => {
    const match = new URL(request.url()).pathname.match(/\/files\/(71\d\d)$/)
    if (match) failedRequests.push(Number(match[1]))
  })
  await page.route(/\/api\/v1\/files\/71\d\d(?:\?|$)/, async route => {
    calls.push(Number(new URL(route.request().url()).pathname.split('/').at(-1)))
    if (!released) await new Promise<void>(resolve => { releases.push(resolve) })
    await route.fulfill({ contentType: 'image/svg+xml', body: svg })
  })
  try {
    await openImageDocument(page)
    const images = page.locator(imageSelector)
    await images.first().scrollIntoViewIfNeeded()
    await expect.poll(() => calls.length).toBe(4)
    const deletedId = calls[0]!
    const deletedImage = page.locator('.ProseMirror img[alt="Private ' + (deletedId - 7100) + '"]')
    await deletedImage.click()
    await page.keyboard.press('Delete')
    await expect(images).toHaveCount(11)
    await expect(deletedImage).toHaveCount(0)
    await expect.poll(() => failedRequests).toContain(deletedId)
    await expect.poll(() => calls.length).toBe(5)
    await frames(page)
    expect(calls).toHaveLength(5)
  } finally {
    released = true
    releases.forEach(release => release())
  }
})
