import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const filter of [
  { query: 'q=needle&type=TITLE', apiKey: 'q', apiValue: 'needle' },
  { query: 'categoryId=7', apiKey: 'categoryId', apiValue: '7' },
  { query: 'concept=1', apiKey: 'minLikes', apiValue: '5' },
] as const) {
  test(`restores board history with ${filter.query}`, async ({ page }) => {
    await installMockApi(page)
    const boards = ['alpha', 'beta'].map((boardUrl, index) => ({
      boardId: index + 1, boardUrl, boardName: boardUrl, description: '',
      sortOrder: index + 1, subscriberCount: 0, postCount: 100,
      isSubscribed: false, isActive: true, isPublic: true,
      subscriptionAccessible: true, allowNsfw: true, isAdmin: false,
      categories: [
        { categoryId: 1, name: 'General', sortOrder: 1, isActive: true, minWriteRole: 'USER' },
        { categoryId: 7, name: 'Category Seven', sortOrder: 2, isActive: true, minWriteRole: 'USER' },
      ],
      latestPosts: [], agentUseYn: false,
    }))
    const requests: URL[] = []
    await page.route('**/api/v1/**', async (route) => {
      const url = new URL(route.request().url())
      const path = url.pathname.replace('/api/v1', '')
      const dataBoard = boards.find((board) => path === `/boards/${board.boardUrl}`)
      if (path === '/boards' || dataBoard) {
        await route.fulfill({ json: { success: true, data: dataBoard ?? boards } })
        return
      }
      const boardPosts = path.match(/^\/boards\/(alpha|beta)\/posts$/)
      if (path !== '/search/posts' && !boardPosts) return route.fallback()

      requests.push(url)
      const boardUrl = boardPosts?.[1] ?? url.searchParams.get('boardUrl')
      const pageIndex = Number(url.searchParams.get('page') ?? 0)
      const marker = url.searchParams.get(filter.apiKey) === filter.apiValue ? 'filtered' : 'all'
      await route.fulfill({ json: { success: true, data: {
        content: [{
          postId: 100 + pageIndex, title: `${boardUrl} ${marker} page ${pageIndex + 1}`,
          boardUrl, boardName: boardUrl, likeCount: 5, viewCount: 10, commentCount: 0,
          author: { userId: 7, displayName: 'Tester' }, createdAt: '2026-10-01T09:00:00',
        }],
        number: pageIndex, size: 20, totalElements: 100, totalPages: 5,
        first: pageIndex === 0, last: pageIndex === 4,
      } } })
    })

    await page.goto(`/board/alpha/?page=3&${filter.query}`)
    const table = page.locator('table')
    await expect(table).toContainText('alpha filtered page 3')
    await expect(page.locator('span[aria-current="page"]')).toHaveText('3')
    await page.locator('#all-board-dropdown-trigger').click()
    await page.getByRole('menuitem', { name: /beta/ }).click()
    await expect(table).toContainText('beta all page 1')

    await page.goBack()
    await expect(table).toContainText('alpha filtered page 3')
    await expect(page.locator('span[aria-current="page"]')).toHaveText('3')
    if (filter.apiKey === 'q') await expect(page.locator('#board-search-input')).toHaveValue('needle')

    await page.goForward()
    await expect(table).toContainText('beta all page 1')
    await page.goBack()
    await expect(table).toContainText('alpha filtered page 3')
    await page.locator('a[href*="page=4"]').first().click()
    await expect(table).toContainText('alpha filtered page 4')
    await expect.poll(() => requests.at(-1)?.searchParams.get('page')).toBe('3')
    expect(requests.at(-1)?.searchParams.get(filter.apiKey)).toBe(filter.apiValue)
  })
}
