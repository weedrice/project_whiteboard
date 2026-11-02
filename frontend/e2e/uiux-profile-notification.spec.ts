import { expect, test } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

for (const width of [390, 1280]) {
  test(`profile save validates before uploading at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true }
    for (const endpoint of ['posts', 'comments']) {
      await page.route(`**/api/v1/users/me/${endpoint}?**`, route => route.fulfill({ json: { success: true, data: emptyPage } }))
    }
    await page.route('**/api/v1/users/me/agents', route => route.fulfill({ json: { success: true, data: { agents: [] } } }))
    let uploads = 0
    let writes = 0
    await page.route('**/api/v1/files/upload', route => {
      uploads++
      return route.fulfill({ json: { success: true, data: { fileId: 100 } } })
    })
    await page.route('**/api/v1/users/me', route => {
      if (route.request().method() === 'PUT') writes++
      return route.fulfill({ json: { success: true, data: mockUser } })
    })
    await page.goto('/mypage')
    await page.getByRole('button', { name: '프로필 수정', exact: true }).click()
    const editor = page.getByRole('dialog', { name: '프로필 수정', exact: true })
    await editor.locator('#profile-photo-input').setInputFiles({
      name: 'avatar.png', mimeType: 'image/png',
      buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'),
    })
    await expect(editor.locator('img[src^="blob:"]')).toBeVisible()
    const name = editor.locator('#profile-display-name')
    await name.fill('x')
    await editor.getByRole('button', { name: '저장', exact: true }).click()
    await expect(editor.getByText('닉네임은 2~50자여야 합니다.')).toBeVisible()
    await expect(name).toBeFocused()
    expect(uploads).toBe(0)
    expect(writes).toBe(0)
    await name.fill('Valid name')
    await editor.getByRole('button', { name: '저장', exact: true }).click()
    await expect(editor).toBeHidden()
    expect(uploads).toBe(1)
    expect(writes).toBe(1)
  })
}

for (const surface of ['page', 'dropdown'] as const) {
  test(`notification ${surface} explains source failures and retries`, async ({ page }) => {
    await installMockApi(page, { authenticated: true })
    const notification = {
      notificationId: 1, sourceType: 'POST', sourceId: 88, notificationType: 'COMMENT',
      isRead: true, createdAt: '2026-10-01T10:00:00', message: 'Audit notification', actor: { userId: 2, displayName: 'Author' },
    }
    await page.route('**/api/v1/notifications?**', route => route.fulfill({ json: { success: true, data: {
      content: [notification], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
    } } }))
    let succeed = false
    let finishFailure!: () => void
    const failureGate = new Promise<void>(resolve => { finishFailure = resolve })
    await page.route('**/api/v1/posts/88', async route => {
      if (!succeed) {
        await failureGate
        return route.fulfill({ status: 404, json: { success: false, error: { code: 'NOT_FOUND', message: 'Missing' } } })
      }
      return route.fulfill({ json: { success: true, data: { postId: 88, board: { boardUrl: 'general' } } } })
    })
    await page.goto(surface === 'page' ? '/mypage/notifications' : '/')
    if (surface === 'dropdown') await page.getByRole('button', { name: '알림 열기', exact: true }).click()
    const container = surface === 'dropdown' ? page.getByRole('dialog', { name: '알림' }) : page.getByRole('main')
    await container.getByRole('button').filter({ hasText: 'Audit notification' }).click()
    await expect(container.getByRole('status').filter({ hasText: '알림으로 이동하고 있습니다.' })).toBeVisible()
    finishFailure()
    await expect(container.getByText('삭제되었거나 접근할 수 없는 알림 대상입니다.')).toBeVisible()
    succeed = true
    await container.getByRole('button', { name: '다시 시도', exact: true }).click()
    await expect(page).toHaveURL(/\/board\/general\/post\/88\//)
  })
}
