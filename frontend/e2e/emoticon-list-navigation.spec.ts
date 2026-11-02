import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`restores emoticon filters, page and scroll after detail navigation at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 })
    await installMockApi(page)
    const requests: URL[] = []
    const packs = Array.from({ length: 40 }, (_, index) => ({
      emoticonId: index + 1,
      name: `Cat pack ${index + 1}`,
      tags: ['cat'],
      creatorName: 'Tester',
      purchaseCount: 1,
      isActive: true,
      createdAt: '2026-01-01',
      modifiedAt: '2026-01-01',
      images: [],
    }))
    await page.route('**/api/v1/emoticons/**', async route => {
      const url = new URL(route.request().url())
      if (url.pathname.endsWith('/popular')) {
        return route.fulfill({ json: { success: true, data: [] } })
      }
      if (url.pathname.endsWith('/search/all')) {
        requests.push(url)
        const pageIndex = Number(url.searchParams.get('page') ?? 0)
        return route.fulfill({ json: { success: true, data: {
          content: packs.slice(pageIndex * 20, (pageIndex + 1) * 20),
          number: pageIndex, size: 20, totalElements: 40, totalPages: 2,
          first: pageIndex === 0, last: pageIndex === 1,
        } } })
      }
      if (url.pathname.endsWith('/purchase-status')) {
        return route.fulfill({ json: { success: true, data: { purchased: false, available: true, price: 0 } } })
      }
      const id = Number(url.pathname.split('/').at(-1))
      if (Number.isInteger(id) && id > 0) {
        return route.fulfill({ json: { success: true, data: packs[id - 1] } })
      }
      return route.fallback()
    })

    await page.goto('/emoticons')
    const input = page.getByRole('textbox', { name: '노비콘 검색', exact: true })
    await input.fill('cat')
    await page.getByRole('combobox', { name: '노비콘 검색 범위' }).selectOption('TAG')
    await input.press('Enter')
    await expect(page).toHaveURL(/q=cat/)
    await page.getByRole('button', { name: '판매순', exact: true }).click()
    await expect(page).toHaveURL(/sort=popular/)
    await page.getByRole('button', { name: '2', exact: true }).click()
    await expect(page).toHaveURL(/page=2/)
    await expect.poll(() => requests.at(-1)?.searchParams.get('page')).toBe('1')
    expect(requests.at(-1)?.searchParams.get('keyword')).toBe('cat')
    expect(requests.at(-1)?.searchParams.get('searchType')).toBe('TAG')
    expect(requests.at(-1)?.searchParams.get('sortBy')).toBe('popular')

    const card = page.getByRole('button', { name: 'Cat pack 31', exact: true })
    await card.scrollIntoViewIfNeeded()
    const scrollBefore = await page.evaluate(() => window.scrollY)
    const listUrl = page.url()
    await card.click()
    await expect(page).toHaveURL(/\/emoticons\/31$/)
    await expect(page.getByRole('heading', { name: 'Cat pack 31', exact: true })).toBeVisible()
    await page.goBack()
    await expect(page).toHaveURL(listUrl)
    await expect(input).toHaveValue('cat')
    await expect(page.getByRole('combobox', { name: '노비콘 검색 범위' })).toHaveValue('TAG')
    await expect(page.getByRole('button', { name: '판매순', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('button', { name: '2', exact: true })).toHaveAttribute('aria-current', 'page')
    await expect(card).toBeVisible()
    await expect.poll(async () => Math.abs((await page.evaluate(() => window.scrollY)) - scrollBefore)).toBeLessThan(50)

    await page.reload()
    await expect(input).toHaveValue('cat')
    await expect(page.getByRole('button', { name: '2', exact: true })).toHaveAttribute('aria-current', 'page')
    await expect(page.getByRole('button', { name: 'Cat pack 21', exact: true })).toBeVisible()
  })
}
