import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`retains posts and retries the failed next page at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await installMockApi(page, { authenticated: true })
    let failNextPage = true
    const requestedPages: number[] = []
    await page.route('**/api/v1/users/me/feeds**', (route) => {
      const pageNumber = Number(new URL(route.request().url()).searchParams.get('page') ?? 0)
      requestedPages.push(pageNumber)
      if (pageNumber === 1 && failNextPage) {
        return route.fulfill({ status: 500, json: {
          success: false, error: { code: 'INTERNAL_ERROR', message: 'Temporary feed failure' },
        } })
      }
      return route.fulfill({ json: { success: true, data: {
        content: Array.from({ length: 20 }, (_, index) => ({
          feedId: pageNumber * 20 + index + 1,
          contentType: 'POST',
          post: {
            postId: pageNumber * 20 + index + 1,
            title: `Feed post ${pageNumber * 20 + index + 1}`,
            contentsExcerpt: 'A post that should remain readable after a network failure.',
            boardUrl: 'general', boardName: 'General',
            author: { userId: 7, displayName: 'Tester' },
            createdAt: '2026-10-01T09:00:00',
            likeCount: 2, commentCount: 0, viewCount: 5,
          },
        })),
        number: pageNumber, size: 20, totalElements: 40, totalPages: 2,
        first: pageNumber === 0, last: pageNumber === 1,
      } } })
    })

    await page.goto('/?view=feed')
    const cards = page.locator('.nv-home-card-personal')
    const panel = page.locator('#home-personal-feed-panel')
    await expect(cards).toHaveCount(20)
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight))
    await expect(panel.getByRole('status')).toContainText('추가 글을 불러오지 못했습니다', { timeout: 20_000 })
    await expect(cards).toHaveCount(20)
    await expect(cards.first()).toContainText('Feed post 1')

    failNextPage = false
    await panel.getByRole('button', { name: '다시 시도', exact: true }).click()
    await expect(cards).toHaveCount(40)
    await expect(panel.getByRole('status')).toHaveCount(0)
    expect(requestedPages.filter((value) => value === 0)).toHaveLength(1)
    expect(requestedPages.at(-1)).toBe(1)
    await expect(cards.last()).toContainText('Feed post 40')
  })
}
