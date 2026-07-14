import { expect, test } from '@playwright/test'

test('real server login reaches user settings', async ({ page }) => {
  const loginId = process.env.E2E_LOGIN_ID
  const password = process.env.E2E_LOGIN_PASSWORD
  if (!loginId || !password) {
    throw new Error('E2E_LOGIN_ID and E2E_LOGIN_PASSWORD are required for test:e2e:full')
  }

  await page.goto('/login')
  await page.locator('#login-id').fill(loginId)
  await page.locator('#password').fill(password)
  await page.locator('form button[type="submit"]').click()
  await page.goto('/mypage/settings')
  await expect(page).toHaveURL(/\/mypage\/settings/)
})
