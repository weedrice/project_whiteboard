import { expect, test } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

test('mobile search keeps recovered results open after retry and closes on outside click', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installMockApi(page)
  let failBoards = true
  let boardRequests = 0
  await page.route('**/api/v1/boards', async (route) => {
    boardRequests += 1
    if (failBoards) {
      await route.fulfill({ status: 500, json: { success: false, data: null, error: { message: 'Unavailable' } } })
    } else {
      await route.fallback()
    }
  })
  await page.goto('/board/general/post/1')
  await page.locator('.nv-global-search-toggle').first().click()
  const input = page.locator('#global-search-input')
  await input.fill('General')
  const results = page.locator('.nv-global-search-mobile-results')
  const retry = results.getByRole('button', { name: '다시 시도', exact: true })
  await expect(retry).toBeVisible()
  const requestsBeforeRetry = boardRequests
  failBoards = false
  await retry.click()

  await expect.poll(() => boardRequests).toBeGreaterThan(requestsBeforeRetry)
  await expect(results.getByRole('option')).toContainText('General')
  await expect(input).toHaveValue('General')
  await expect(input).toHaveAttribute('aria-expanded', 'true')
  await expect(retry).toHaveCount(0)

  await page.mouse.click(10, 600)
  await expect(input).toHaveCount(0)
  await expect(results).toHaveCount(0)
  await page.locator('.nv-global-search-toggle').first().click()
  await expect(input).toHaveValue('General')
  await input.focus()
  await results.getByRole('option').click()
  await expect(page).toHaveURL(/\/board\/general\/?$/)
})

test('sorting my posts from a later page loads the first page of the new order', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installMockApi(page, { authenticated: true })
  const requests: URL[] = []
  await page.route('**/api/v1/users/me/agents', (route) => route.fulfill({ json: { success: true, data: { agents: [] } } }))
  await page.route('**/api/v1/users/me/comments?*', (route) => route.fulfill({ json: {
    success: true, data: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true },
  } }))
  await page.route('**/api/v1/users/me/posts?*', async (route) => {
    const url = new URL(route.request().url())
    requests.push(url)
    const currentPage = Number(url.searchParams.get('page'))
    const sort = url.searchParams.get('sort')
    const content = Array.from({ length: 10 }, (_, index) => ({
      postId: currentPage * 10 + index + 1,
      title: `${sort} page ${currentPage + 1} post ${index + 1}`,
      boardUrl: 'general', boardName: 'General',
      author: mockUser, createdAt: '2026-10-01T09:00:00',
      viewCount: index, likeCount: index, commentCount: 0,
    }))
    await route.fulfill({ json: { success: true, data: {
      content, page: currentPage, size: 10, totalElements: 30, totalPages: 3,
      first: currentPage === 0, last: currentPage === 2,
    } } })
  })
  await page.goto('/mypage')
  const firstPost = page.locator('.nv-post-card').first()
  await expect(firstPost).toContainText('createdAt,desc page 1 post 1')
  await page.getByRole('button', { name: '2', exact: true }).click()
  await expect(firstPost).toContainText('createdAt,desc page 2 post 1')
  await page.getByRole('combobox', { name: '정렬', exact: true }).selectOption('likeCount,desc')

  await expect(firstPost).toContainText('likeCount,desc page 1 post 1')
  expect(requests.at(-1)?.searchParams.get('page')).toBe('0')
  expect(requests.at(-1)?.searchParams.get('sort')).toBe('likeCount,desc')
  await expect(page.getByRole('button', { name: '1', exact: true })).toHaveAttribute('aria-current', 'page')
  await page.getByRole('button', { name: '2', exact: true }).click()
  await expect(firstPost).toContainText('likeCount,desc page 2 post 1')
})
