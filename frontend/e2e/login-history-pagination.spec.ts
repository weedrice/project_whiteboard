import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`opens older login history and navigates back at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    const requestedPages: number[] = []
    await page.route('**/api/v1/users/me/login-history?**', async route => {
      const number = Number(new URL(route.request().url()).searchParams.get('page'))
      requestedPages.push(number)
      const content = Array.from({ length: number === 0 ? 10 : 1 }, (_, index) => ({
        historyId: number * 10 + index + 1, loginId: 'tester', ipAddress: '127.0.0.1',
        deviceSummary: `Login device ${number * 10 + index + 1}`, success: true,
        createdAt: '2026-10-27T09:00:00+09:00',
      }))
      await route.fulfill({ json: { success: true, data: {
        content, number, size: 10, totalElements: 11, totalPages: 2,
        first: number === 0, last: number === 1, empty: false,
      } } })
    })
    await page.goto('/mypage/settings#security')
    await page.locator('[aria-controls="settings-login-history"]').click()
    const history = page.locator('#settings-login-history')
    await expect(history.getByText('Login device 1', { exact: true })).toBeVisible()
    await history.getByRole('button', { name: '다음', exact: true }).click()
    await expect(history.getByText('Login device 11', { exact: true })).toBeVisible()
    await expect(history.getByText('Login device 1', { exact: true })).toBeHidden()
    expect(requestedPages).toContain(1)
    await history.getByRole('button', { name: '이전', exact: true }).click()
    await expect(history.getByText('Login device 1', { exact: true })).toBeVisible()
    await expect(history.getByRole('button', { name: '이전', exact: true })).toBeDisabled()
  })
}
