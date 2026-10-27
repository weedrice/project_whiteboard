import { expect, test } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

for (const screen of [
  { path: 'reports', api: 'reports' },
  { path: 'security', api: 'ip-blocks' },
]) {
  test(`admin ${screen.path} preserves an uncached next page`, async ({ page }) => {
    await installMockApi(page, { authenticated: true })
    await page.route('**/api/v1/users/me', route => route.fulfill({ json: {
      success: true, data: { ...mockUser, role: 'SUPER_ADMIN' },
    } }))
    const requests: number[] = []
    let releasePage!: () => void
    const pending = new Promise<void>(resolve => { releasePage = resolve })
    await page.route(`**/api/v1/admin/${screen.api}?*`, async route => {
      const currentPage = Number(new URL(route.request().url()).searchParams.get('page'))
      requests.push(currentPage)
      if (currentPage === 1) await pending
      const content = screen.path === 'reports'
        ? [{ reportId: currentPage + 1, reporterId: 10, reporterDisplayName: `Reporter page ${currentPage + 1}`,
          targetId: 20, targetType: 'POST', targetUserId: 20, targetDisplayName: 'Target',
          reasonType: 'SPAM', contents: 'Spam', status: 'PENDING', createdAt: '2026-01-01T00:00:00' }]
        : [{ ipAddress: `192.0.2.${currentPage + 1}`, reason: 'Spam', startDate: '2026-01-01T00:00:00',
          admin: { adminId: 1, displayName: 'Admin' } }]
      await route.fulfill({ json: { success: true, data: {
        content, page: currentPage, number: currentPage, size: 20, totalElements: 41, totalPages: 3,
        first: currentPage === 0, last: currentPage === 2,
      } } })
    })
    await page.goto(`/admin/${screen.path}`)
    await page.getByRole('button', { name: '2', exact: true }).click()
    await expect.poll(() => requests.includes(1)).toBe(true)
    releasePage()
    await expect(page.locator('button[aria-current="page"]')).toHaveText('2')
    const expectedRow = screen.path === 'reports' ? 'Reporter page 2' : '192.0.2.2'
    await expect(page.getByText(expectedRow, { exact: true }).first()).toBeVisible()
    expect(requests.filter(value => value === 0)).toHaveLength(1)
    await expect(page.getByRole('button', { name: '3', exact: true })).toBeVisible()
  })
}
