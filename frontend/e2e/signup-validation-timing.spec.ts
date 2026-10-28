import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

const viewports = [
  { name: 'desktop', size: { width: 1280, height: 800 } },
  { name: 'mobile', size: { width: 390, height: 844 } },
] as const

for (const viewport of viewports) {
  test(`signup waits for blur before errors and validates corrections and password dependencies (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page)
    await page.route('**/api/v1/auth/oauth/signup-ticket', (route) => route.fulfill({
      status: 404,
      json: { success: false, data: null, error: { code: 'NOT_FOUND', message: 'No OAuth signup ticket' } },
    }))
    const fields = [
      { id: 'login-id', invalid: 'a', valid: 'login_1' },
      { id: 'password', invalid: 'a', valid: 'Password1!' },
      { id: 'password-confirm', invalid: 'a', valid: 'Password1!' },
      { id: 'signup-email', invalid: 'user', valid: 'user@example.com' },
      { id: 'display-name', invalid: 'a', valid: 'Display' },
    ]
    for (const field of fields) {
      // A fresh form prevents Tab in a prior scenario from touching this field.
      await page.goto('/signup')
      if (field.id === 'password-confirm') await page.locator('#password').fill('Password1!')
      const input = page.locator(`#${field.id}`)
      const error = page.locator(`#${field.id}-error`)
      await input.fill(field.invalid)
      await expect(input).not.toHaveAttribute('aria-invalid', 'true')
      await expect(error).toHaveCount(0)
      await input.press('Tab')
      await expect(input).toHaveAttribute('aria-invalid', 'true')
      await expect(error).toBeVisible()
      await expect(input).toHaveAttribute('aria-describedby', `${field.id}-error`)
      await input.fill(field.valid)
      await expect(input).not.toHaveAttribute('aria-invalid', 'true')
      await expect(error).toHaveCount(0)
    }

    await page.goto('/signup')
    await page.locator('#password').fill('Password1!')
    await page.locator('#password-confirm').fill('Password1!')
    await page.locator('#password-confirm').press('Tab')
    await expect(page.locator('#password-confirm-error')).toHaveCount(0)
    await page.locator('#password').fill('Different1!')
    await expect(page.locator('#password-confirm')).toHaveAttribute('aria-invalid', 'true')
    await expect(page.locator('#password-confirm-error')).toHaveText('비밀번호가 일치하지 않습니다.')
    await page.locator('#password-confirm').fill('Different1!')
    await expect(page.locator('#password-confirm')).not.toHaveAttribute('aria-invalid', 'true')
    await expect(page.locator('#password-confirm-error')).toHaveCount(0)
  })
}
