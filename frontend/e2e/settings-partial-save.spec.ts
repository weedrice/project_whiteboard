import { expect, test } from '@playwright/test'
import { installMockApi, mockSettings } from './fixtures/mockApi'

test('saving a theme preserves a different setting changed by another device', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  let current = { ...mockSettings }
  const writes: Record<string, unknown>[] = []
  await page.route('**/api/v1/users/me/settings', route => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON()
      writes.push(payload)
      current = { ...current, ...payload }
    }
    return route.fulfill({ json: { success: true, data: current } })
  })
  await page.goto('/mypage/settings')
  const general = page.locator('#general')
  await general.getByLabel('테마', { exact: true }).selectOption('DARK')
  // A second device updates the server after this form has loaded its baseline.
  current = { ...current, timezone: 'UTC' }
  await general.getByRole('button', { name: '설정 저장', exact: true }).click()
  await expect.poll(() => writes).toEqual([{ theme: 'DARK' }])
  await expect(general.getByRole('button', { name: '설정 저장', exact: true })).toBeDisabled()
  expect(current.timezone).toBe('UTC')
  await expect(general.getByLabel('표시 시간대', { exact: true })).toHaveValue('UTC')
})
