import { expect, test } from '@playwright/test'
import { installMockApi, mockSettings } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`retains and saves a theme selected during an earlier save at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    let current = { ...mockSettings }
    const writes: string[] = []
    let releaseFirst!: () => void
    const firstResponse = new Promise<void>(resolve => { releaseFirst = resolve })
    await page.route('**/api/v1/users/me/settings', async route => {
      if (route.request().method() === 'PUT') {
        const payload = route.request().postDataJSON()
        writes.push(payload.theme)
        if (writes.length === 1) await firstResponse
        current = { ...current, ...payload }
      }
      await route.fulfill({ json: { success: true, data: current } })
    })
    try {
      await page.goto('/mypage/settings')
      const general = page.locator('#general')
      const theme = general.getByLabel('테마', { exact: true })
      await expect(theme).toHaveValue('LIGHT')
      await theme.selectOption('DARK')
      await general.getByRole('button', { name: '설정 저장', exact: true }).click()
      await expect.poll(() => writes).toEqual(['DARK'])
      await theme.selectOption('LIGHT')
      releaseFirst()
      const save = general.getByRole('button', { name: '설정 저장', exact: true })
      await expect(save).toBeEnabled()
      await expect(theme).toHaveValue('LIGHT')
      await expect(page.locator('html')).toHaveClass(/dark/)
      await save.click()
      await expect.poll(() => writes).toEqual(['DARK', 'LIGHT'])
      await expect(save).toBeDisabled()
      await expect(theme).toHaveValue('LIGHT')
      await expect(page.locator('html')).not.toHaveClass(/dark/)
    } finally {
      releaseFirst()
    }
  })
}
