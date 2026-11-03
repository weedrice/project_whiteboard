import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test('a mailbox data event refreshes messages without creating a notification', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  await page.addInitScript(() => {
    const originalFetch = window.fetch.bind(window)
    window.fetch = (input, init) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (!url.includes('/notifications/stream')) return originalFetch(input, init)
      const stream = new ReadableStream<Uint8Array>({ start(controller) {
        const target = window as typeof window & { emitMailboxData?: (payload: unknown) => void }
        target.emitMailboxData = payload => controller.enqueue(new TextEncoder().encode(
          `event: message-changed\ndata: ${JSON.stringify(payload)}\n\n`,
        ))
        init?.signal?.addEventListener('abort', () => controller.close(), { once: true })
      } })
      return Promise.resolve(new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }))
    }
  })
  let content = 'Before incoming message'
  let reads = 0
  await page.route('**/api/v1/messages**', route => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/unread-count')) return route.fulfill({ json: { success: true, data: 0 } })
    reads++
    return route.fulfill({ json: { success: true, data: {
      content: [{ messageId: 51, content, partner: { userId: 42, displayName: 'Mailbox partner' }, isRead: true, createdAt: '2026-10-27T00:00:00' }],
      number: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true,
    } } })
  })
  await page.route('**/api/v1/users/me/notification-settings', route => route.fulfill({ json: {
    success: true, data: [{ notificationType: 'MESSAGE', isEnabled: false }],
  } }))
  await page.goto('/mypage/messages')
  await expect(page.getByText(content, { exact: true })).toBeVisible()
  await page.waitForFunction(() => typeof (window as typeof window & { emitMailboxData?: unknown }).emitMailboxData === 'function')
  const initialReads = reads
  content = 'Received while notifications are off'
  await page.evaluate(() => {
    (window as typeof window & { emitMailboxData: (payload: unknown) => void }).emitMailboxData({ messageId: 52, partnerId: 42 })
  })
  await expect(page.getByText(content, { exact: true })).toBeVisible()
  expect(reads).toBeGreaterThan(initialReads)
  await page.getByRole('button', { name: '알림 열기', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '알림' })).not.toContainText(content)
})
