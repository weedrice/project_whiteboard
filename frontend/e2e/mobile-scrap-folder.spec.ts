import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`creates a scrap folder at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    const folders: Array<{ folderId: number; name: string; sortOrder: number }> = []
    await page.route('**/api/v1/users/me/scrap-folders', async route => {
      if (route.request().method() === 'POST') {
        expect(route.request().postDataJSON()).toEqual({ name: '읽을 글' })
        folders.push({ folderId: 1, name: '읽을 글', sortOrder: 0 })
      }
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: route.request().method() === 'POST' ? folders[0] : folders }),
      })
    })
    await page.route('**/api/v1/users/me/scraps?**', route => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ success: true, data: { content: [], page: 0, size: 15, totalPages: 0, totalElements: 0, last: true } }),
    }))

    await page.goto('/mypage/scraps')
    const name = page.getByRole('textbox', { name: '새 폴더', exact: true })
    await expect(name).toBeVisible({ timeout: 60_000 })
    await name.fill('읽을 글')
    await page.getByRole('button', { name: '추가', exact: true }).click()
    await expect(page.getByRole('button', { name: '읽을 글', exact: true })).toBeVisible()
    await expect(name).toHaveValue('')
    expect(folders).toHaveLength(1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
}
