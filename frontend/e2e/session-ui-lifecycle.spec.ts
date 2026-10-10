import { expect, test, type Route } from '@playwright/test'
import { installMockApi, mockSettings, mockUser } from './fixtures/mockApi'

function fulfillJson(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data, error: null }),
  })
}

test('bootstrap restores settings after the user identity is hydrated', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const meRequest = Promise.withResolvers<Route>()
  const settingsRequest = Promise.withResolvers<Route>()
  let settingsRequests = 0
  await page.route('**/api/v1/users/me', route => meRequest.resolve(route))
  await page.route('**/api/v1/users/me/settings', (route) => {
    settingsRequests += 1
    settingsRequest.resolve(route)
  })

  await page.goto('/')
  const meRoute = await meRequest.promise
  expect(settingsRequests).toBe(0)
  await fulfillJson(meRoute, mockUser)
  const settingsRoute = await settingsRequest.promise
  await fulfillJson(settingsRoute, {
    ...mockSettings, theme: 'DARK', language: 'en', timezone: 'America/New_York',
  })

  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect.poll(() => page.evaluate(() => localStorage.getItem('displayTimeZone')))
    .toBe('America/New_York')
  expect(settingsRequests).toBe(1)
})

test('leaving a submitting inquiry preserves the current page and refreshes its list', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const createRequest = Promise.withResolvers<Route>()
  let created = false
  const item = {
    inquiryId: 92, category: 'SERVICE_USE', title: '지연 응답 문의', status: 'NEW',
    lastPublicMessageSummary: '완료를 기다리는 동안 목록으로 이동합니다.',
    modifiedAt: '2026-10-10T09:00:00',
  }
  await page.route('**/api/v1/inquiries**', (route) => {
    if (route.request().method() === 'POST') {
      createRequest.resolve(route)
      return
    }
    return fulfillJson(route, {
      content: created ? [item] : [], page: 0, size: 20,
      totalElements: created ? 1 : 0, totalPages: created ? 1 : 0,
      first: true, last: true,
    })
  })

  await page.goto('/inquiries/new')
  const form = page.locator('form')
  await form.getByRole('textbox', { name: '제목' }).fill(item.title)
  await form.getByRole('textbox', { name: /문의 내용/ }).fill(item.lastPublicMessageSummary)
  await form.locator('button[type="submit"]').click()
  const createRoute = await createRequest.promise
  await page.locator('header').getByRole('link', { name: '내 문의', exact: true }).click()
  await expect(page).toHaveURL('/inquiries')
  await expect(page.getByRole('heading', { name: '내 문의', exact: true })).toBeVisible()

  created = true
  await fulfillJson(createRoute, item)
  await expect(page.getByRole('link', { name: new RegExp(item.title) })).toBeVisible()
  await page.evaluate(() => new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  }))
  await expect(page).toHaveURL('/inquiries')
  await expect(page.getByRole('heading', { name: '내 문의', exact: true })).toBeVisible()
})
