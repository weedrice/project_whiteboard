import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const listing of ['search', 'tag'] as const) {
  test(`${listing} results sort through the API and preserve navigation state`, async ({ page }) => {
    await installMockApi(page)
    const requests: URL[] = []
    const posts = [
      { postId: 1, title: 'Newest post', likeCount: 2, viewCount: 50 },
      { postId: 2, title: 'Popular post', likeCount: 10, viewCount: 100 },
    ].map((post) => ({
      ...post,
      boardUrl: 'general', boardName: 'General', commentCount: 0,
      author: { userId: 7, displayName: 'Tester' },
      createdAt: '2026-10-01T09:00:00',
    }))
    await page.route(listing === 'search' ? '**/api/v1/search?**' : '**/api/v1/tags/vue/posts?**', async (route) => {
      const url = new URL(route.request().url())
      requests.push(url)
      const pageIndex = Number(url.searchParams.get('page') ?? 0)
      const sort = url.searchParams.get('sort')
      const content = sort?.endsWith(',desc') && sort !== 'createdAt,desc' ? [...posts].reverse() : posts
      const resultPage = { content, number: pageIndex, size: 20, totalElements: 60, totalPages: 3, first: pageIndex === 0, last: pageIndex === 2 }
      const emptyGroup = { items: [], totalElements: 0, totalPages: 0, page: pageIndex, size: 20, hasMore: false }
      await route.fulfill({ json: { success: true, data: listing === 'tag' ? resultPage : {
        keyword: 'vue',
        postResults: { ...emptyGroup, items: content, totalElements: 60, totalPages: 3, hasMore: pageIndex < 2 },
        commentResults: emptyGroup, userResults: emptyGroup, boardResults: [], boardResultPage: emptyGroup,
      } } })
    })

    await page.goto(listing === 'search' ? '/search?q=vue&searchType=TITLE&page=2' : '/tag/vue?page=2')
    const table = page.locator('table')
    const sortHeaders = table.locator('th').filter({ has: page.getByRole('button') })
    await expect(table.locator('tbody tr').first()).toContainText('Newest post')

    await sortHeaders.nth(0).getByRole('button').click()
    await expect.poll(() => requests.at(-1)?.searchParams.get('sort')).toBe('likeCount,desc')
    expect(requests.at(-1)?.searchParams.get('page')).toBe('0')
    await expect(table.locator('tbody tr').first()).toContainText('Popular post')
    await expect(sortHeaders.nth(0)).toHaveAttribute('aria-sort', 'descending')
    expect(new URL(page.url()).searchParams.has('page')).toBe(false)
    if (listing === 'search') expect(new URL(page.url()).searchParams.get('searchType')).toBe('TITLE')

    await sortHeaders.nth(0).getByRole('button').click()
    await expect.poll(() => requests.at(-1)?.searchParams.get('sort')).toBe('likeCount,asc')
    await expect(table.locator('tbody tr').first()).toContainText('Newest post')
    await expect(sortHeaders.nth(0)).toHaveAttribute('aria-sort', 'ascending')
    const nextPage = listing === 'search' ? '1' : '2'
    const paginationLink = page.locator(`a[href*="page=${nextPage}"][href*="sort=likeCount"]`).first()
    await expect(paginationLink).toBeVisible()
    await paginationLink.click()
    await expect.poll(() => requests.at(-1)?.searchParams.get('page')).toBe('1')
    expect(requests.at(-1)?.searchParams.get('sort')).toBe('likeCount,asc')

    await page.reload()
    await expect(sortHeaders.nth(0)).toHaveAttribute('aria-sort', 'ascending')
    await sortHeaders.nth(1).getByRole('button').click()
    await expect.poll(() => requests.at(-1)?.searchParams.get('sort')).toBe('viewCount,desc')
    expect(requests.at(-1)?.searchParams.get('page')).toBe('0')
    await expect(sortHeaders.nth(1)).toHaveAttribute('aria-sort', 'descending')
    await page.goBack()
    await expect(sortHeaders.nth(0)).toHaveAttribute('aria-sort', 'ascending')
  })
}
