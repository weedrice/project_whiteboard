import { expect, test, type Route } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

const fulfill = (route: Route, data: unknown) => route.fulfill({ json: { success: true, data } })
const inquiry = {
  inquiryId: 91, authorUserId: mockUser.userId, authorName: mockUser.displayName,
  category: 'TECHNICAL', title: 'Keep inquiry draft', status: 'NEW', effectivePriority: 'NORMAL',
  closureReason: null, closureDetail: null,
  allowedActions: { canAddMessage: true, canWithdraw: false, canClose: false },
  messages: [], histories: [], createdAt: '2026-10-01T09:00:00', modifiedAt: '2026-10-01T09:00:00',
}

for (const width of [390, 1280]) {
  test(`reconnect failure keeps inquiry attachments until an actual exit at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await installMockApi(page, { authenticated: true })
    let failRead = false
    let reads = 0
    const discarded: number[][] = []
    await page.route('**/api/v1/inquiries/91', route => {
      reads++
      return failRead
        ? route.fulfill({ status: 500, json: { success: false, error: { code: 'INTERNAL_ERROR', message: 'Unavailable' } } })
        : fulfill(route, inquiry)
    })
    await page.route('**/api/v1/files/upload', route => fulfill(route, { fileId: 501 }))
    await page.route('**/api/v1/files/uploads/discard', route => {
      discarded.push(route.request().postDataJSON().fileIds)
      return fulfill(route, { discardedCount: 1 })
    })
    await page.route('**/api/v1/inquiries?**', route => fulfill(route, {
      content: [], number: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true,
    }))
    await page.goto('/inquiries/91')
    const reply = page.getByRole('textbox', { name: '추가 답변', exact: true })
    await reply.fill('Keep this reply')
    await page.locator('form input[type="file"]').setInputFiles({
      name: 'evidence.png', mimeType: 'image/png',
      buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'),
    })
    await expect(page.getByText('evidence.png', { exact: true })).toBeVisible()
    failRead = true
    const initialReads = reads
    await page.evaluate(() => window.dispatchEvent(new Event('offline')))
    await page.evaluate(() => window.dispatchEvent(new Event('online')))
    await expect.poll(() => reads).toBeGreaterThan(initialReads)
    const error = page.getByRole('alert').filter({ hasText: '불러오지 못했습니다.' })
    await expect(error).toBeVisible({ timeout: 15_000 })
    await expect(reply).toHaveValue('Keep this reply')
    await expect(page.getByText('evidence.png', { exact: true })).toBeVisible()
    expect(discarded).toEqual([])
    failRead = false
    await error.getByRole('button', { name: '다시 시도', exact: true }).click()
    await expect(error).toHaveCount(0)
    await expect(reply).toHaveValue('Keep this reply')
    await page.getByRole('button', { name: '목록', exact: true }).click()
    await page.getByRole('dialog', { name: '확인', exact: true }).getByRole('button', { name: '예', exact: true }).click()
    await expect(page).toHaveURL('/inquiries')
    await expect.poll(() => discarded).toEqual([[501]])
  })
}
