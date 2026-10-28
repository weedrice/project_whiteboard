import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const mode of ['page', 'dropdown'] as const) {
  test(`notification ${mode} exposes read state in accessible names`, async ({ page }) => {
    await page.setViewportSize({ width: mode === 'page' ? 390 : 1280, height: 844 })
    await installMockApi(page, { authenticated: true })
    await page.route('**/api/v1/notifications**', (route) => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/stream')) return route.fallback()
      return route.fulfill({ json: { success: true, data: path.endsWith('/unread-count') ? 1 : {
        content: [false, true].map((isRead, index) => ({
          notificationId: index + 1, notificationType: 'SYSTEM', sourceType: 'SYSTEM',
          sourceId: 1, message: isRead ? 'Read notice' : 'Unread notice', isRead,
          createdAt: '2026-10-01T09:00:00', actor: { userId: 8, displayName: 'Writer' },
        })),
        number: 0, size: 15, totalElements: 2, totalPages: 1, first: true, last: true,
      } } })
    })
    await page.goto(mode === 'page' ? '/mypage/notifications' : '/')
    if (mode === 'dropdown') await page.locator('button[aria-controls="notification-dropdown-panel"]').click()
    await expect(page.getByRole('button', { name: /읽지 않음.*Unread notice/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /읽음.*Read notice/ })).toBeVisible()
  })
}
