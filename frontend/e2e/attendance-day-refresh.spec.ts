import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test('home attendance refreshes at KST midnight while the page remains open', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-10-31T14:59:00Z') })
  await installMockApi(page, { authenticated: true })
  let today = '2026-10-31'
  let requests = 0
  await page.route('**/api/v1/attendance/me*', route => {
    requests += 1
    return route.fulfill({ json: { success: true, data: {
      month: today.slice(0, 7), today, checkedInToday: today === '2026-10-31',
      currentStreakCount: 3, days: [],
    } } })
  })
  await page.goto('/')
  const checkIn = page.locator('.nv-attendance-button')
  await expect(checkIn).toBeDisabled()
  expect(requests).toBe(1)
  today = '2026-11-01'
  await page.clock.fastForward(61_000)
  await expect(checkIn).toBeEnabled()
  expect(requests).toBe(2)
})

test('home attendance catches up after the device wakes on a later KST date', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-10-31T14:59:00Z') })
  await installMockApi(page, { authenticated: true })
  let checkedInToday = true
  await page.route('**/api/v1/attendance/me*', route => route.fulfill({ json: { success: true, data: {
    month: checkedInToday ? '2026-10' : '2026-11',
    today: checkedInToday ? '2026-10-31' : '2026-11-02',
    checkedInToday, currentStreakCount: 3, days: [],
  } } }))
  await page.goto('/')
  const checkIn = page.locator('.nv-attendance-button')
  await expect(checkIn).toBeDisabled()
  checkedInToday = false
  // Move the wall clock without running the scheduled midnight callback.
  await page.clock.setSystemTime(new Date('2026-11-02T00:00:00Z'))
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(checkIn).toBeEnabled()
})
