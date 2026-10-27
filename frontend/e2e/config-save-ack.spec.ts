import { expect, test } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

test('shows normalized saved settings and preserves another row edited while saving', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  await page.route('**/api/v1/users/me', route => route.fulfill({
    json: { success: true, data: { ...mockUser, role: 'SUPER_ADMIN' } },
  }))
  const configs = [
    { key: 'NOBICON_PRICE', value: '100', description: 'Price' },
    { key: 'SITE_NAME', value: 'Original name', description: 'Name' },
  ]
  const writes: Array<{ value: string, description: string }> = []
  const pendingSave = Promise.withResolvers<void>()
  let listReads = 0
  await page.route('**/api/v1/admin/configs**', async route => {
    if (route.request().method() === 'PUT') {
      const payload = route.request().postDataJSON() as { value: string, description: string }
      writes.push(payload)
      await pendingSave.promise
      const key = decodeURIComponent(new URL(route.request().url()).pathname.split('/').at(-1)!)
      const config = configs.find(item => item.key === key)!
      config.value = payload.value.trim()
      config.description = payload.description.trim()
      await route.fulfill({ json: { success: true, data: config } })
      return
    }
    listReads += 1
    await route.fulfill({ json: { success: true, data: configs } })
  })

  try {
    await page.goto('/admin/settings')
    const value = page.getByRole('textbox', { name: 'NOBICON_PRICE 값', exact: true })
    const description = page.getByRole('textbox', { name: 'NOBICON_PRICE 설명', exact: true })
    const otherValue = page.getByRole('textbox', { name: 'SITE_NAME 값', exact: true })
    const row = page.getByRole('row').filter({ has: value })
    await expect(value).toHaveValue('100')
    await value.fill('  200  ')
    await description.fill('  Saved description  ')
    await row.getByRole('button', { name: '저장', exact: true }).click()
    await expect.poll(() => writes).toEqual([{ value: '200', description: 'Saved description' }])
    await expect(value).toBeDisabled()
    await otherValue.fill('New unsaved name')
    pendingSave.resolve()

    await expect(value).toBeEnabled()
    await expect(value).toHaveValue('200')
    await expect(description).toHaveValue('Saved description')
    await expect.poll(() => listReads).toBeGreaterThanOrEqual(2)
    await expect(otherValue).toHaveValue('New unsaved name')
    expect(writes).toHaveLength(1)
  } finally {
    pendingSave.resolve()
  }
})
