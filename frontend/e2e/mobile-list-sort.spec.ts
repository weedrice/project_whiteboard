import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const listing of ['board', 'search'] as const) {
  test(`mobile ${listing} sorting updates results and preserves route state`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await installMockApi(page)
    const requests: URL[] = []
    await page.route('**/api/v1/**', async (route) => {
      const url = new URL(route.request().url())
      if (!['/api/v1/boards/general/posts', '/api/v1/search'].includes(url.pathname)) return route.fallback()
      requests.push(url)
      const sort = url.searchParams.get('sort') ?? 'createdAt,desc'
      const content = Array.from({ length: 20 }, (_, index) => ({
        postId: index + 1, title: `${sort} post ${index + 1}`, boardUrl: 'general', boardName: 'General',
        likeCount: index, viewCount: index, commentCount: 0,
        author: { userId: 7, displayName: 'Tester' }, createdAt: '2026-10-01T09:00:00',
      }))
      const resultPage = { content, number: 0, size: 20, totalElements: 20, totalPages: 1, first: true, last: true }
      const emptyGroup = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20, hasMore: false }
      await route.fulfill({ json: { success: true, data: listing === 'board' ? resultPage : {
        keyword: 'vue', postResults: { ...emptyGroup, items: content, totalElements: 20, totalPages: 1 },
        commentResults: emptyGroup, userResults: emptyGroup, boardResults: [], boardResultPage: emptyGroup,
      } } })
    })
    await page.goto(listing === 'board' ? '/board/general/?concept=1' : '/search?q=vue&searchType=TITLE')
    const sortControl = page.getByRole('combobox', { name: '정렬', exact: true })
    const firstPost = page.locator('.nv-post-card').first()
    await expect(sortControl).toHaveValue('createdAt,desc')
    for (const sort of ['likeCount,desc', 'likeCount,asc', 'viewCount,desc', 'createdAt,asc']) {
      await sortControl.selectOption(sort)
      await expect.poll(() => requests.at(-1)?.searchParams.get('sort')).toBe(sort)
      expect(requests.at(-1)?.searchParams.get('page')).toBe('0')
      await expect(firstPost).toContainText(`${sort} post 1`)
      await expect(sortControl).toHaveValue(sort)
      expect(new URL(page.url()).searchParams.get(listing === 'board' ? 'concept' : 'searchType'))
        .toBe(listing === 'board' ? '1' : 'TITLE')
    }
    if (listing === 'search') {
      await page.goBack()
      await expect(sortControl).toHaveValue('viewCount,desc')
      await page.reload()
      await expect(sortControl).toHaveValue('viewCount,desc')
    } else {
      await sortControl.selectOption('viewCount,desc')
      await expect(firstPost).toContainText('viewCount,desc post 1')
    }
    await page.setViewportSize({ width: 1280, height: 844 })
    await expect(sortControl).toBeHidden()
    await expect(page.locator('th[aria-sort="descending"]')).toContainText('조회')
  })
}

for (const sort of ['postId,asc', 'postId,desc']) {
  test(`mobile search displays restored ${sort} without rewriting the URL or request`, async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await installMockApi(page)
    const requestedSorts: string[] = []
    await page.route('**/api/v1/search?**', async (route) => {
      const requestedSort = new URL(route.request().url()).searchParams.get('sort') ?? 'createdAt,desc'
      requestedSorts.push(requestedSort)
      const emptyGroup = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20, hasMore: false }
      await route.fulfill({ json: { success: true, data: {
        keyword: 'vue',
        postResults: { ...emptyGroup, totalElements: 1, totalPages: 1, items: [{
          postId: 1, title: `${requestedSort} restored post`, boardUrl: 'general', boardName: 'General',
          author: { userId: 7, displayName: 'Tester' }, createdAt: '2026-10-01T09:00:00',
          likeCount: 1, viewCount: 1, commentCount: 0,
        }] },
        commentResults: emptyGroup, userResults: emptyGroup, boardResults: [], boardResultPage: emptyGroup,
      } } })
    })
    await page.goto(`/search?q=vue&sort=${encodeURIComponent(sort)}`)
    const sortControl = page.getByRole('combobox', { name: '정렬', exact: true })
    const expectedLabel = sort === 'postId,asc' ? '번호 오름차순' : '번호 내림차순'
    await expect(sortControl).toHaveValue(sort)
    await expect(sortControl.locator('option:checked')).toHaveText(expectedLabel)
    await expect(page.locator('.nv-post-card').first()).toContainText(`${sort} restored post`)
    expect(new URL(page.url()).searchParams.get('sort')).toBe(sort)
    expect(requestedSorts).toEqual([sort])
    await page.reload()
    await expect(sortControl).toHaveValue(sort)
    await expect(sortControl.locator('option:checked')).toHaveText(expectedLabel)
    expect(new URL(page.url()).searchParams.get('sort')).toBe(sort)
    expect(requestedSorts).toEqual([sort, sort])

    await sortControl.selectOption('likeCount,desc')
    await expect(page.locator('.nv-post-card').first()).toContainText('likeCount,desc restored post')
    expect(new URL(page.url()).searchParams.get('sort')).toBe('likeCount,desc')
    expect(requestedSorts.at(-1)).toBe('likeCount,desc')
    await expect(sortControl.locator('option')).toHaveCount(6)
  })
}
