import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`preserves existing series when a creation completes before the initial list at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, { authenticated: true })
    let releaseList!: () => void
    const listGate = new Promise<void>(resolve => { releaseList = resolve })
    let listRequested = false
    let listCompleted = false
    let createdTitle: string | undefined
    await page.route('**/api/v1/users/me/post-series', async route => {
      if (route.request().method() === 'GET') {
        listRequested = true
        await listGate
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ success: true, data: [{ seriesId: 1, title: 'Existing series' }] }),
        })
        listCompleted = true
        return
      }
      if (route.request().method() === 'POST') {
        createdTitle = route.request().postDataJSON().title
        await route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ success: true, data: { seriesId: 2, title: createdTitle } }),
        })
        return
      }
      await route.fallback()
    })

    try {
      await page.goto('/board/general/write')
      await expect.poll(() => listRequested).toBe(true)
      const suffix = width < 1024 ? '-mobile' : ''
      const newSeriesInput = page.locator(`#new-series${suffix}`)
      const seriesSelect = page.locator(`#series${suffix}`)
      await expect(newSeriesInput).toBeVisible()
      await newSeriesInput.fill('Created while loading')
      await newSeriesInput.press('Enter')
      await expect(seriesSelect).toHaveValue('2')
      expect(createdTitle).toBe('Created while loading')
      expect(listCompleted).toBe(false)

      releaseList()
      await expect(seriesSelect.locator('option')).toHaveText([
        '시리즈 없음', 'Existing series', 'Created while loading',
      ])
      await expect(seriesSelect).toHaveValue('2')
      await seriesSelect.selectOption('1')
      await expect(seriesSelect).toHaveValue('1')
      await expect(seriesSelect.locator('option[value="2"]')).toHaveCount(1)
    } finally {
      releaseList()
    }
  })
}
