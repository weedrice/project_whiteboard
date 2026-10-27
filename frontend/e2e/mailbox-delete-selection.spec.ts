import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test('deletes the confirmed message after a live message refresh clears its checkbox', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  await page.addInitScript(() => {
    const originalFetch = window.fetch.bind(window)
    window.fetch = (input, init) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
      if (!url.includes('/notifications/stream')) return originalFetch(input, init)
      const stream = new ReadableStream<Uint8Array>({ start(controller) {
        const target = window as typeof window & { emitMailboxNotification?: (notification: unknown) => void }
        target.emitMailboxNotification = notification => controller.enqueue(new TextEncoder().encode(
          `event: notification\ndata: ${JSON.stringify(notification)}\n\n`,
        ))
        init?.signal?.addEventListener('abort', () => controller.close(), { once: true })
      } })
      return Promise.resolve(new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }))
    }
  })

  let messages = [51, 52].map(messageId => ({
    messageId, content: `Mailbox message ${messageId}`, partner: { userId: 100, displayName: 'Mailbox partner' },
    isRead: false, createdAt: '2026-10-27T00:00:00',
  }))
  const deletes: number[][] = []
  let listRequests = 0
  await page.route('**/api/v1/messages**', async route => {
    const request = route.request()
    if (request.method() === 'DELETE') {
      const ids = request.postDataJSON() as number[]
      deletes.push(ids)
      messages = messages.filter(message => !ids.includes(message.messageId))
      return route.fulfill({ json: { success: true, data: null } })
    }
    if (new URL(request.url()).pathname.endsWith('/unread-count')) {
      return route.fulfill({ json: { success: true, data: 0 } })
    }
    listRequests += 1
    return route.fulfill({ json: { success: true, data: {
      content: messages, page: 0, size: 15, totalPages: 1,
      totalElements: messages.length, hasNext: false, hasPrevious: false,
    } } })
  })
  await page.goto('/mypage/messages')
  await expect(page.getByText('Mailbox message 51', { exact: true })).toBeVisible()
  await expect.poll(() => page.evaluate(() => typeof (window as typeof window & {
    emitMailboxNotification?: unknown
  }).emitMailboxNotification)).toBe('function')
  await page.locator('#message-51-select').check()
  await page.getByRole('button', { name: /삭제 \(1\)/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  const beforeStream = listRequests
  await page.evaluate(() => {
    (window as typeof window & { emitMailboxNotification: (notification: unknown) => void })
      .emitMailboxNotification({
        notificationId: 91, notificationType: 'MESSAGE', sourceType: 'MESSAGE', sourceId: 52,
        message: 'New mailbox message', isRead: false, groupCount: 1,
        createdAt: '2026-10-27T00:00:00Z', actor: { userId: 100, displayName: 'Mailbox partner' },
      })
  })
  await expect.poll(() => listRequests).toBeGreaterThan(beforeStream)
  await expect(page.locator('#message-51-select')).not.toBeChecked()
  await dialog.getByRole('button', { name: '예', exact: true }).click()
  await expect.poll(() => deletes).toEqual([[51]])
  await expect(page.getByText('Mailbox message 51', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Mailbox message 52', { exact: true })).toBeVisible()
})
