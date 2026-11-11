import { expect, test, type Locator } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { installMockApi } from './fixtures/mockApi'

async function expectLabelAlignedWithInputText(control: Locator) {
  const offset = await control.evaluate(element => {
    const input = element as HTMLInputElement | HTMLSelectElement
    const label = input.labels?.[0]
    if (!label) throw new Error(`Missing label for ${input.name}`)
    const labelText = document.createRange()
    labelText.selectNodeContents(label)
    const style = getComputedStyle(input)
    const textLeft = input.getBoundingClientRect().left
      + parseFloat(style.borderLeftWidth) + parseFloat(style.paddingLeft)
    return Math.abs(labelText.getBoundingClientRect().left - textLeft)
  })
  expect(offset).toBeLessThanOrEqual(1.5)
}

for (const width of [390, 1440]) {
  test(`search results remain readable and fit the viewport at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 })
    await page.addInitScript(() => {
      // Keep native date inputs, but intercept the OS popup so clicks can be asserted headlessly.
      Object.defineProperty(HTMLInputElement.prototype, 'showPicker', {
        configurable: true,
        value(this: HTMLInputElement) {
          this.dataset.pickerCalls = String(Number(this.dataset.pickerCalls ?? 0) + 1)
        },
      })
    })
    await installMockApi(page, { authenticated: true })
    await page.route('**/api/v1/search/recent**', route => route.fulfill({ json: { success: true, data: {
      content: [{ logId: 1, keyword: 'Vue', searchedAt: '2026-11-10T09:00:00' },
        { logId: 2, keyword: '최근검색어'.repeat(20), searchedAt: '2026-11-10T08:00:00' },
        ...Array.from({ length: 10 }, (_, index) => ({
          logId: index + 3, keyword: `가로 스크롤 검색어 ${index + 1}`, searchedAt: '2026-11-10T07:00:00',
        }))],
      page: 0, size: 20, totalElements: 12, totalPages: 1, hasNext: false, hasPrevious: false,
    } } }))
    const group = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20, hasMore: false }
    await page.route('**/api/v1/search?**', route => route.fulfill({ json: { success: true, data: {
      keyword: 'Vue', boardResults: [], boardResultPage: group, userResults: group,
      postResults: { ...group, totalElements: 2, totalPages: 1, items: [1, 2].map(postId => ({
        postId, title: `Vue 검색 결과 ${postId}`, boardUrl: 'general', boardName: '개발 이야기',
        author: { userId: 7, displayName: '테스터' }, createdAt: '2026-11-10T09:00:00',
        viewCount: 20, likeCount: 3, commentCount: 2,
      })) },
      commentResults: { ...group, totalElements: 1, totalPages: 1, items: [{
        commentId: 1, postId: 1, boardUrl: 'general', postTitle: 'Vue 검색 결과',
        content: '검색 결과에서 댓글 본문을 편하게 읽을 수 있습니다.', author: { userId: 7, displayName: '테스터' },
      }] },
    } } }))
    await page.route('**/api/v1/search/popular', route => route.fulfill({ json: { success: true, data: {
      keywords: [{ keyword: 'TypeScript' }, { keyword: '긴검색어'.repeat(20) }],
    } } }))
    await page.route('**/api/v1/search/semantic?**', route => route.fulfill({ json: { success: true, data: {
      content: [], number: 0, size: 5, totalElements: 0, totalPages: 0,
    } } }))
    await page.goto('/search?q=Vue')
    const search = page.getByTestId('search-page')
    await expect(search.getByTestId('comment-results')).toBeVisible()
    await expect(search.getByTestId('recent-keywords').getByRole('button', { name: 'Vue 검색', exact: true })).toBeVisible()
    const scope = page.getByRole('combobox', { name: '게시글 검색 범위', exact: true })
    const period = page.getByRole('combobox', { name: '작성 기간', exact: true })
    await expect(scope).toBeVisible()
    await expect(period).toBeVisible()
    await expect(period).toHaveValue('')
    const scopeBox = await scope.boundingBox()
    const inputBox = await page.locator('#search-page-query').boundingBox()
    const periodBox = await period.boundingBox()
    const authorBox = await page.locator('#search-author-filter').boundingBox()
    const queryControlsBox = await page.getByTestId('search-query-controls').boundingBox()
    const periodControlsBox = await page.getByTestId('search-period-controls').boundingBox()
    const formBox = await page.getByRole('search').boundingBox()
    const recentBox = await search.getByTestId('recent-keywords').boundingBox()
    expect(scopeBox!.x + scopeBox!.width).toBeLessThanOrEqual(inputBox!.x)
    expect(scopeBox!.y).toBeCloseTo(inputBox!.y, 0)
    expect(queryControlsBox!.y + queryControlsBox!.height).toBeLessThan(periodControlsBox!.y)
    expect(periodBox!.y).toBeCloseTo(authorBox!.y, 0)
    expect(periodBox!.height).toBeCloseTo(authorBox!.height, 0)
    expect(periodBox!.width).toBeCloseTo(authorBox!.width, 0)
    expect(periodBox!.x + periodBox!.width).toBeLessThanOrEqual(authorBox!.x)
    expect(periodBox!.width).toBeLessThan(formBox!.width / 2)
    if (width >= 640) expect(periodBox!.width).toBeCloseTo(scopeBox!.width, 0)
    expect(recentBox!.y).toBeGreaterThan(formBox!.y + formBox!.height)
    await expectLabelAlignedWithInputText(period)
    await expectLabelAlignedWithInputText(page.locator('#search-author-filter'))

    const recent = search.getByTestId('recent-keywords')
    const recentList = recent.locator('.recent-boards-list')
    await expect(recent.locator('.recent-boards-inner')).toBeVisible()
    await expect(recentList.locator('.recent-board-chip')).toHaveCount(12)
    const chipTops = await recentList.locator('.recent-board-chip').evaluateAll(chips =>
      chips.map(chip => chip.getBoundingClientRect().top))
    expect(Math.max(...chipTops) - Math.min(...chipTops)).toBeLessThanOrEqual(1)
    expect(await recentList.evaluate(element => {
      const style = getComputedStyle(element)
      return ['auto', 'scroll'].includes(style.overflowX) && element.scrollWidth > element.clientWidth
    })).toBe(true)
    await recentList.evaluate(element => { element.scrollLeft = element.scrollWidth })
    await expect.poll(() => recentList.evaluate(element => element.scrollLeft)).toBeGreaterThan(0)
    await recentList.evaluate(element => { element.scrollLeft = 0 })
    await expect(recent.getByRole('button', { name: '최근 검색어 Vue 삭제', exact: true })).toHaveCount(1)
    await expect(recent.getByRole('button', { name: '모두 삭제', exact: true })).toBeVisible()

    await period.selectOption('CUSTOM')
    const filters = page.getByTestId('search-period-controls')
    expect(await filters.locator('input, select').evaluateAll(controls =>
      controls.map(control => control.getAttribute('name')))).toEqual(['period', 'from', 'to', 'author'])
    for (const id of ['search-from-filter', 'search-to-filter']) {
      const date = page.locator(`#${id}`)
      await expect(date).toHaveAttribute('type', 'date')
      await expect(date).toBeEditable()
      await expectLabelAlignedWithInputText(date)
      await date.fill('2026-11-10')
      await date.focus()
      await date.press('ArrowUp')
      await expect.poll(() => date.inputValue()).not.toBe('2026-11-10')
      await expect(date).not.toHaveAttribute('data-picker-calls')
      await date.click()
      await expect(date).toHaveAttribute('data-picker-calls', '1')
      await date.fill('2026-11-10')
    }
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await period.selectOption('')
    for (const theme of ['light', 'dark']) {
      await page.evaluate(theme => document.documentElement.classList.toggle('dark', theme === 'dark'), theme)
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      const pageBox = await search.boundingBox()
      const resultsBox = await page.getByTestId('search-results').boundingBox()
      expect(resultsBox!.width).toBeCloseTo(pageBox!.width, 0)
      const audit = await new AxeBuilder({ page }).include('[data-testid="search-page"]').withRules(['color-contrast']).analyze()
      expect(audit.violations).toEqual([])
      await page.screenshot({ path: testInfo.outputPath(`search-${theme}-${width}.png`), fullPage: true })
    }
    await page.getByTestId('popular-keywords').getByRole('button', { name: 'TypeScript 검색', exact: true }).click()
    await expect(page).toHaveURL(/q=TypeScript/)
  })
}


test('post and comment pages navigate independently and inline filters reset both for the same query', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await installMockApi(page)
  const requests: URL[] = []
  await page.route('**/api/v1/search?**', async route => {
    const url = new URL(route.request().url())
    requests.push(url)
    const postPage = Number(url.searchParams.get('postPage') ?? url.searchParams.get('page') ?? 0)
    const commentPage = Number(url.searchParams.get('commentPage') ?? url.searchParams.get('page') ?? 0)
    const emptyGroup = { items: [], totalElements: 0, totalPages: 0, page: 0, size: 20, hasMore: false }
    await route.fulfill({ json: { success: true, data: {
      keyword: 'Vue', boardResults: [], boardResultPage: emptyGroup, userResults: emptyGroup,
      postResults: { ...emptyGroup, page: postPage, totalElements: 60, totalPages: 3, hasMore: postPage < 2, items: [{
        postId: postPage + 1, title: `Post page ${postPage + 1}`, boardUrl: 'general', boardName: 'General',
        author: { userId: 7, displayName: 'Tester' }, createdAt: '2026-11-10T09:00:00',
        viewCount: 20, likeCount: 3, commentCount: 2,
      }] },
      commentResults: { ...emptyGroup, page: commentPage, totalElements: 40, totalPages: 2, hasMore: commentPage < 1, items: [{
        commentId: commentPage + 1, postId: 1, boardUrl: 'general', postTitle: 'Vue post',
        content: `Comment page ${commentPage + 1}`, author: { userId: 7, displayName: 'Tester' },
      }] },
    } } })
  })
  await page.route('**/api/v1/search/popular', route => route.fulfill({ json: { success: true, data: { keywords: [] } } }))
  await page.route('**/api/v1/search/semantic?**', route => route.fulfill({ json: { success: true, data: {
    content: [], number: 0, size: 5, totalElements: 0, totalPages: 0,
  } } }))
  await page.goto('/search?q=Vue&postPage=1&commentPage=1')
  const posts = page.getByTestId('post-results')
  const comments = page.getByTestId('comment-results')
  await expect(posts).toContainText('Post page 2')
  await expect(comments).toContainText('Comment page 2')

  await posts.getByRole('link', { name: '3', exact: true }).click()
  await expect(posts).toContainText('Post page 3')
  await expect(comments).toContainText('Comment page 2')
  expect(requests.at(-1)?.searchParams.get('postPage')).toBe('2')
  expect(requests.at(-1)?.searchParams.get('commentPage')).toBe('1')
  await comments.getByRole('link', { name: '1', exact: true }).click()
  await expect(comments).toContainText('Comment page 1')
  await expect(posts).toContainText('Post page 3')
  expect(requests.at(-1)?.searchParams.get('postPage')).toBe('2')
  expect(requests.at(-1)?.searchParams.get('commentPage')).toBe('0')

  await page.goBack()
  await expect(posts).toContainText('Post page 3')
  await expect(comments).toContainText('Comment page 2')
  await page.reload()
  await expect(posts).toContainText('Post page 3')
  await expect(comments).toContainText('Comment page 2')

  const sortHeaders = posts.locator('th').filter({ has: page.getByRole('button') })
  await sortHeaders.nth(0).getByRole('button').click()
  await expect(posts).toContainText('Post page 1')
  await expect(comments).toContainText('Comment page 2')
  expect(requests.at(-1)?.searchParams.get('postPage')).toBe('0')
  expect(requests.at(-1)?.searchParams.get('commentPage')).toBe('1')
  expect(requests.at(-1)?.searchParams.get('sort')).toBe('likeCount,desc')

  const form = page.getByRole('search')
  await form.getByRole('combobox', { name: '게시글 검색 범위', exact: true }).selectOption('TITLE')
  await form.getByRole('combobox', { name: '작성 기간', exact: true }).selectOption('WEEK')
  await form.getByRole('button', { name: '검색', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.searchParams.get('period')).toBe('WEEK')
  expect(requests.at(-1)?.searchParams.get('searchType')).toBe('TITLE')
  expect(requests.at(-1)?.searchParams.get('q')).toBe('Vue')
  expect(requests.at(-1)?.searchParams.get('postPage')).toBe('0')
  expect(requests.at(-1)?.searchParams.get('commentPage')).toBe('0')
  const query = new URL(page.url()).searchParams
  expect(query.has('postPage')).toBe(false)
  expect(query.has('commentPage')).toBe(false)
  expect(query.has('page')).toBe(false)

  await form.getByRole('combobox', { name: '작성 기간', exact: true }).selectOption('CUSTOM')
  await page.locator('#search-from-filter').fill('2026-11-10')
  await page.locator('#search-to-filter').fill('2026-11-01')
  await expect(form.getByRole('button', { name: '검색', exact: true })).toBeDisabled()
  await page.locator('#search-to-filter').fill('2026-11-10')
  await expect(form.getByRole('button', { name: '검색', exact: true })).toBeEnabled()
  await form.getByRole('button', { name: '검색', exact: true }).click()
  await expect.poll(() => requests.at(-1)?.searchParams.get('period')).toBe('CUSTOM')
  expect(requests.at(-1)?.searchParams.get('from')).toBe('2026-11-10')
  expect(requests.at(-1)?.searchParams.get('to')).toBe('2026-11-10')
})
