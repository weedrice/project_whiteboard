import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 639, 640, 1280]) {
  test(`board search and writing use responsive placement at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await installMockApi(page, { authenticated: true })
    const requests: URL[] = []
    await page.route('**/api/v1/**', async (route) => {
      const url = new URL(route.request().url())
      if (!['/api/v1/boards/general/posts', '/api/v1/search/posts'].includes(url.pathname)) return route.fallback()
      requests.push(url)
      const pageIndex = Number(url.searchParams.get('page') ?? 0)
      const filtered = url.searchParams.get('q') === 'needle'
      const content = Array.from({ length: filtered ? 1 : 20 }, (_, index) => ({
        postId: pageIndex * 20 + index + 1, title: filtered ? 'Needle result' : `Board post ${pageIndex * 20 + index + 1}`,
        boardUrl: 'general', boardName: 'General', likeCount: 0, viewCount: 0, commentCount: 0,
        author: { userId: 7, displayName: 'Tester' }, createdAt: '2026-10-01T09:00:00',
      }))
      await route.fulfill({ json: { success: true, data: {
        content, number: pageIndex, size: 20, totalElements: filtered ? 1 : 100,
        totalPages: filtered ? 1 : 5, first: pageIndex === 0, last: filtered || pageIndex === 4,
      } } })
    })
    await page.goto('/board/general/')
    const firstPost = page.locator(width < 640 ? '.nv-post-card' : 'tbody tr').first()
    await expect(firstPost).toContainText('Board post 1')
    const search = page.locator('#board-search-input')
    await expect(search).toHaveCount(1)
    await search.scrollIntoViewIfNeeded()
    await expect(search).toBeInViewport()
    const searchBox = await search.boundingBox()
    const postBox = await firstPost.boundingBox()
    const write = page.locator('.nv-board-search-write-btn')
    await expect(write).toHaveCount(1)
    await expect(write).toHaveAttribute('href', '/board/general/write')
    const writeBox = await write.boundingBox()
    if (width < 640) {
      expect(searchBox!.y + searchBox!.height).toBeLessThan(postBox!.y)
      expect(writeBox!.y + writeBox!.height).toBeLessThan(postBox!.y)
    } else {
      const lastPostBox = await page.locator('tbody tr').last().boundingBox()
      expect(searchBox!.y).toBeGreaterThan(lastPostBox!.y + lastPostBox!.height)
      expect(writeBox!.y).toBeGreaterThan(lastPostBox!.y + lastPostBox!.height)
    }
    expect(requests.every((url) => Number(url.searchParams.get('page') ?? 0) === 0)).toBe(true)
    await search.fill('needle')
    await search.press('Enter')
    await expect(firstPost).toContainText('Needle result')
    await expect.poll(() => requests.at(-1)?.searchParams.get('q')).toBe('needle')
    await expect(search).toHaveValue('needle')
    await search.fill('unsent query')
    const resizedWidth = width < 640 ? 1280 : 390
    await page.setViewportSize({ width: resizedWidth, height: 844 })
    await expect(search).toHaveCount(1)
    await expect(search).toHaveValue('unsent query')
    await expect(write).toHaveCount(1)
    const resizedPost = page.locator(resizedWidth < 640 ? '.nv-post-card' : 'tbody tr').first()
    await expect(resizedPost).toContainText('Needle result')
    await expect.poll(() => search.evaluate((input) => {
      const list = document.querySelector('#board-post-list .nv-post-card, #board-post-list tbody tr')
      return list ? Boolean(input.compareDocumentPosition(list) & Node.DOCUMENT_POSITION_FOLLOWING) : null
    })).toBe(resizedWidth < 640)
  })
}
