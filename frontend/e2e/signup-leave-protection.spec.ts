import { expect, test, type Page, type Route } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

const fulfill = (route: Route, data: unknown = null) => route.fulfill({
  json: { success: true, data, error: null },
})

async function installSignupApi(page: Page) {
  await installMockApi(page)
  const state = { ticketDeletes: 0, signups: 0 }
  await page.route('**/api/v1/auth/oauth/signup-ticket', route => {
    if (route.request().method() === 'DELETE') {
      state.ticketDeletes++
      return fulfill(route)
    }
    return route.fulfill({ status: 404, json: { success: false, error: { message: 'No ticket' } } })
  })
  await page.route('**/api/v1/auth/email/send-verification', route => fulfill(route))
  await page.route('**/api/v1/auth/email/verify', route => fulfill(route, {
    verificationTicket: 'signup-verification', isReregister: false,
  }))
  await page.route('**/api/v1/auth/signup', route => {
    state.signups++
    return fulfill(route)
  })
  return state
}

async function openSignupFromLogin(page: Page) {
  await page.goto('/login')
  await page.getByRole('link', { name: '계정 만들기', exact: true }).click()
  await expect(page).toHaveURL(/\/signup$/)
  // Login and signup share input IDs; wait for the entering signup form,
  // including its OAuth initialization, before filling either shared input.
  await expect(page.getByRole('heading', { name: '계정 만들기', exact: true })).toBeVisible()
  await expect(page.locator('#password-confirm')).toBeVisible()
}

for (const viewport of [
  { name: 'mobile', width: 390, height: 844 },
  { name: 'desktop', width: 1280, height: 800 },
]) {
  test(`signup back preserves draft until discard is approved (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport)
    const state = await installSignupApi(page)
    await openSignupFromLogin(page)
    await page.locator('#login-id').fill('unfinished')
    const back = page.getByRole('button', { name: '뒤로', exact: true })
    const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
    await back.click()
    await expect(confirmation).toContainText('회원가입 정보와 이메일 인증')
    expect(state.ticketDeletes).toBe(0)
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(page.locator('#login-id')).toHaveValue('unfinished')
    await back.click()
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(confirmation).toHaveCount(0)
    expect(state.ticketDeletes).toBe(1)
  })
}

test('browser history cancellation and reload protection retain signup inputs', async ({ page }) => {
  const state = await installSignupApi(page)
  await openSignupFromLogin(page)
  await page.locator('#login-id').fill('unfinished')
  await page.evaluate(() => history.back())
  const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
  await expect(confirmation).toBeVisible()
  await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
  await expect(page).toHaveURL(/\/signup$/)
  await expect(page.locator('#login-id')).toHaveValue('unfinished')
  expect(state.ticketDeletes).toBe(0)

  const dialogPromise = page.waitForEvent('dialog')
  // A cancelled reload never reaches Playwright's requested load milestone.
  // Trigger the browser action without waiting for a navigation that must not occur.
  await page.evaluate(() => { window.setTimeout(() => window.location.reload(), 0) })
  const dialog = await dialogPromise
  expect(dialog.type()).toBe('beforeunload')
  await dialog.dismiss()
  await expect(page.locator('#login-id')).toHaveValue('unfinished')
})

test('email verification is protected and pending signup cannot be abandoned before success', async ({ page }) => {
  const state = await installSignupApi(page)
  let finishSignup: (() => void) | undefined
  const pendingSignup = new Promise<void>(resolve => { finishSignup = resolve })
  await page.route('**/api/v1/auth/signup', async route => {
    state.signups++
    await pendingSignup
    return fulfill(route)
  })
  await openSignupFromLogin(page)
  await page.locator('#login-id').fill('signup_user')
  await page.locator('#password').fill('Password1!')
  await page.locator('#password-confirm').fill('Password1!')
  await page.locator('#signup-email').fill('signup@example.com')
  await page.getByRole('button', { name: '인증 코드 발송', exact: true }).click()
  await page.locator('#signup-verification-code').fill('123456')
  await page.getByRole('button', { name: '인증하기', exact: true }).click()
  await expect(page.locator('#signup-email')).toBeDisabled()
  const back = page.getByRole('button', { name: '뒤로', exact: true })
  await back.click()
  const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
  await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
  await expect(page.locator('#signup-email')).toBeDisabled()
  await page.locator('#display-name').fill('Signup User')
  await page.getByRole('button', { name: '회원가입', exact: true }).click()
  await expect.poll(() => state.signups).toBe(1)
  await expect(back).toBeDisabled()
  await page.evaluate(() => history.back())
  await expect(page).toHaveURL(/\/signup$/)
  await expect(confirmation).toHaveCount(0)
  finishSignup?.()
  await expect(page).toHaveURL(/\/login$/)
  await expect(confirmation).toHaveCount(0)
  expect(state.ticketDeletes).toBe(0)
})
