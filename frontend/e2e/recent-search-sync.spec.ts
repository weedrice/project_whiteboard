import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test('consecutive searches reconcile delayed history and delete confirmed records', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  let records: Array<{ logId: number; keyword: string; searchedAt: string }> = []
  let historyReads = 0
  const deletions: string[] = []
  await page.route('**/api/v1/search/recent**', async route => {
    if (route.request().method() === 'DELETE') {
      deletions.push(new URL(route.request().url()).pathname)
      const id = Number(new URL(route.request().url()).pathname.split('/').at(-1))
      records = records.filter(record => record.logId !== id)
      await route.fulfill({ json: { success: true, data: null } })
      return
    }
    historyReads += 1
    const content = records
    await route.fulfill({ json: { success: true, data: {
      content, page: 0, size: 8, totalElements: content.length,
      totalPages: content.length ? 1 : 0, hasNext: false, hasPrevious: false,
    } } })
  })
  await page.route('**/api/v1/search?**', async route => {
    const keyword = new URL(route.request().url()).searchParams.get('q')
    const empty = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20, hasMore: false }
    await route.fulfill({ json: { success: true, data: {
      keyword, postResults: empty, commentResults: empty, userResults: empty,
      boardResults: [], boardResultPage: empty,
    } } })
  })
  await page.route('**/api/v1/search/semantic?**', route => route.fulfill({ json: {
    success: true, data: { content: [], number: 0, size: 5, totalElements: 0, totalPages: 0 },
  } }))
  await page.route('**/api/v1/search/popular', route => route.fulfill({ json: {
    success: true, data: { keywords: [] },
  } }))
  await page.route('**/api/v1/tags/popular**', route => route.fulfill({ json: {
    success: true, data: { tags: [] },
  } }))

  await page.goto('/search')
  await expect.poll(() => historyReads).toBeGreaterThan(0)
  const recent = page.locator('aside section').filter({ has: page.getByRole('heading', { name: '최근 검색어', exact: true }) })
  const input = page.locator('#search-page-query')
  const readsBeforeSearch = historyReads
  await input.fill('second')
  await input.press('Enter')
  await expect.poll(() => historyReads).toBeGreaterThan(readsBeforeSearch)
  await expect(recent.getByRole('button', { name: 'second 검색', exact: true })).toHaveCount(0)
  records = [{ logId: 42, keyword: 'second', searchedAt: '2026-10-27T12:01:00' }]
  await expect(recent.getByRole('button', { name: 'second 검색', exact: true })).toBeVisible()
  await recent.getByRole('button', { name: '최근 검색어 second 삭제', exact: true }).click()
  await expect(recent.getByRole('button', { name: 'second 검색', exact: true })).toHaveCount(0)
  expect(deletions).toEqual(['/api/v1/search/recent/42'])

  const readsBeforeNextSearch = historyReads
  await input.fill('third')
  await input.press('Enter')
  await expect.poll(() => historyReads).toBeGreaterThan(readsBeforeNextSearch)
  await expect(recent.getByRole('button', { name: 'third 검색', exact: true })).toHaveCount(0)
  records = [{ logId: 43, keyword: 'third', searchedAt: '2026-10-27T12:02:00' }]
  await expect(recent.getByRole('button', { name: 'third 검색', exact: true })).toBeVisible()
  await expect(recent.getByRole('button', { name: 'second 검색', exact: true })).toHaveCount(0)
})
