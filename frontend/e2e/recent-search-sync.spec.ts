import { expect, test, type Page } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

const guestStorageKey = 'whiteboard:recent-searches:v1:guest'
const accountStorageKey = 'whiteboard:recent-searches:v1:user:7'

async function installEmptySearchResults(page: Page) {
  await page.route('**/api/v1/search?**', route => {
    const keyword = new URL(route.request().url()).searchParams.get('q')
    const empty = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20, hasMore: false }
    return route.fulfill({ json: { success: true, data: {
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
}

test('guest history persists across reloads and supports local deletion without history API calls', async ({ page }) => {
  await installMockApi(page)
  await installEmptySearchResults(page)
  const historyRequests: string[] = []
  page.on('request', request => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/search/recent')) {
      historyRequests.push(`${request.method()} ${request.url()}`)
    }
  })

  await page.goto('/search')
  const recent = page.getByTestId('recent-keywords')
  const input = page.locator('#search-page-query')
  await expect(input).toBeVisible()
  await expect(recent).toHaveCount(0)
  for (const keyword of ['first guest search', 'second guest search']) {
    await input.fill(keyword)
    await input.press('Enter')
    await expect(recent.getByRole('button', { name: `${keyword} 검색`, exact: true })).toBeVisible()
  }

  // Returning to a fresh cached result must still promote or recreate its history.
  await input.fill('first guest search')
  await input.press('Enter')
  await expect(recent.locator('.recent-board-link').first()).toHaveText('first guest search')
  await recent.getByRole('button', { name: '최근 검색어 first guest search 삭제', exact: true }).click()
  for (const keyword of ['second guest search', 'first guest search']) {
    await input.fill(keyword)
    await input.press('Enter')
    await expect(recent.locator('.recent-board-link').first()).toHaveText(keyword)
  }

  // Reopening an empty search page checks persistence without submitting the URL query again.
  await page.goto('/search')
  await page.reload()
  await expect(recent.getByRole('button', { name: 'first guest search 검색', exact: true })).toBeVisible()
  await expect(recent.getByRole('button', { name: 'second guest search 검색', exact: true })).toBeVisible()
  await recent.getByRole('button', { name: '최근 검색어 first guest search 삭제', exact: true }).click()
  await page.reload()
  await expect(recent.getByRole('button', { name: 'first guest search 검색', exact: true })).toHaveCount(0)
  await expect(recent.getByRole('button', { name: 'second guest search 검색', exact: true })).toBeVisible()
  await recent.getByRole('button', { name: '모두 삭제', exact: true }).click()
  await expect(recent).toHaveCount(0)
  await page.reload()
  await expect(input).toBeVisible()
  await expect(recent).toHaveCount(0)
  expect(historyRequests).toEqual([])
})

test('authenticated history hydrates its own cache while loading then replaces it with DB records without merging guest history', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  await installEmptySearchResults(page)
  const guest = [{ keyword: 'private guest search', searchedAt: '2026-11-10T09:00:00Z' }]
  const cached = [{ logId: 11, keyword: 'cached account search', searchedAt: '2026-11-10T08:00:00Z' }]
  const server = [{ logId: 12, keyword: 'server account search', searchedAt: '2026-11-10T10:00:00Z' }]
  await page.addInitScript(({ guestKey, accountKey, guest, cached }) => {
    localStorage.setItem(guestKey, JSON.stringify(guest))
    localStorage.setItem(accountKey, JSON.stringify(cached))
  }, { guestKey: guestStorageKey, accountKey: accountStorageKey, guest, cached })
  const historyMethods: string[] = []
  let releaseHistory!: () => void
  const historyReady = new Promise<void>(resolve => { releaseHistory = resolve })
  await page.route('**/api/v1/search/recent**', async route => {
    historyMethods.push(route.request().method())
    await historyReady
    await route.fulfill({ json: { success: true, data: {
      content: server, page: 0, size: 20, totalElements: 1,
      totalPages: 1, hasNext: false, hasPrevious: false,
    } } })
  })

  try {
    await page.goto('/search')
    const recent = page.getByTestId('recent-keywords')
    await expect.poll(() => historyMethods.length).toBeGreaterThan(0)
    await expect(recent.getByRole('button', { name: 'cached account search 검색', exact: true })).toBeVisible()
    await expect(recent.getByRole('button', { name: 'private guest search 검색', exact: true })).toHaveCount(0)
    releaseHistory()
    await expect(recent.getByRole('button', { name: 'server account search 검색', exact: true })).toBeVisible()
    await expect(recent.getByRole('button', { name: 'cached account search 검색', exact: true })).toHaveCount(0)
    await expect(recent.getByRole('button', { name: 'private guest search 검색', exact: true })).toHaveCount(0)
    await expect.poll(() => page.evaluate(key => JSON.parse(localStorage.getItem(key) ?? '[]'), accountStorageKey)).toEqual(server)
    expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key) ?? '[]'), guestStorageKey)).toEqual(guest)
    expect(historyMethods.every(method => method === 'GET')).toBe(true)
  } finally {
    releaseHistory()
  }
})

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
      content, page: 0, size: 20, totalElements: content.length,
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
  const recent = page.getByTestId('search-query-panel').getByTestId('recent-keywords')
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
