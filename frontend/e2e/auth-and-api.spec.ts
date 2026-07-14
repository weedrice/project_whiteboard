import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

test('login succeeds and a protected route redirects unauthenticated users', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/mypage/settings')
  await expect(page).toHaveURL(/\/login\?redirect=/)

  await page.locator('#login-id').fill('tester')
  await page.locator('#password').fill('password')
  await page.locator('form button[type="submit"]').click()
  await expect(page).toHaveURL(/\/mypage\/settings/)
})

test('a 401 refreshes once and retries the original request', async ({ page }) => {
  const state = await installMockApi(page, { homeUnauthorizedOnce: true })
  await login(page)

  await expect(page.getByRole('heading', { level: 1 })).toBeAttached()
  await expect.poll(() => state.homeCount).toBe(2)
  await expect.poll(() => state.refreshCount).toBeGreaterThanOrEqual(1)
})

test('OAuth callback hydrates the user or returns to login on failure', async ({ page }) => {
  const state = await installMockApi(page, { authenticated: true })
  await page.goto('/auth/oauth/callback?accessToken=must-be-cleared')
  await expect(page).toHaveURL('/')
  expect(new URL(page.url()).search).toBe('')
  expect(state.refreshCount).toBeGreaterThanOrEqual(1)

  state.authenticated = false
  await page.goto('/auth/oauth/callback')
  await expect(page).toHaveURL('/login')
})
