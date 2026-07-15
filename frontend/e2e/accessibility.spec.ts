import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

async function expectNoSeriousAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze()
  const violations = results.violations.filter((violation) =>
    violation.impact === 'serious' || violation.impact === 'critical')
  expect(violations, JSON.stringify(violations, null, 2)).toEqual([])
}

test('login and home have no serious or critical axe violations', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/login')
  await expectNoSeriousAccessibilityViolations(page)
  await page.goto('/')
  await expectNoSeriousAccessibilityViolations(page)
})

test('post detail has no serious or critical axe violations', async ({ page }) => {
  await installMockApi(page)
  await page.goto('/board/general/post/1')
  await expect(page.getByRole('heading', { name: 'Accessible post' })).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})

test('settings and post editor have no serious or critical axe violations', async ({ page }) => {
  await installMockApi(page)
  await login(page)
  await page.goto('/mypage/settings')
  await expectNoSeriousAccessibilityViolations(page)
  await page.goto('/board/general/write')
  await expect(page.locator('#title')).toBeVisible()
  await expectNoSeriousAccessibilityViolations(page)
})
