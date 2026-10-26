import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

for (const initialLoad of [true, false]) {
  test(`SSE notification survives older ${initialLoad ? 'initial' : 'read-all refetch'} responses`, async ({ page }) => {
    await installMockApi(page, { authenticated: true })
    await page.addInitScript(() => {
      const originalFetch = window.fetch.bind(window)
      window.fetch = (input, init) => {
        const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
        if (!url.includes('/notifications/stream')) return originalFetch(input, init)
        const stream = new ReadableStream<Uint8Array>({ start(controller) {
          const target = window as typeof window & { emitTestNotification?: (notification: unknown) => void }
          target.emitTestNotification = notification => controller.enqueue(new TextEncoder().encode(
            `event: notification\ndata: ${JSON.stringify(notification)}\n\n`,
          ))
          init?.signal?.addEventListener('abort', () => controller.close(), { once: true })
        } })
        return Promise.resolve(new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } }))
      }
    })
    const incoming = {
      notificationId: 77, notificationType: 'COMMENT', sourceType: 'POST', sourceId: 1,
      message: 'New SSE notification', isRead: false, groupCount: 1,
      createdAt: '2026-10-26T00:00:00Z', actor: { userId: 8, displayName: 'Writer' },
    }
    let readAll = false
    let eventSent = false
    let heldList = false
    let heldCount = false
    let releaseOld!: () => void
    const oldResponses = new Promise<void>(resolve => { releaseOld = resolve })
    await page.route('**/api/v1/notifications**', async route => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/read-all')) {
        readAll = true
        return route.fulfill({ json: { success: true, data: null } })
      }
      const isCount = path.endsWith('/unread-count')
      const shouldHold = (initialLoad || readAll) && !eventSent
      if (shouldHold) {
        if (isCount) heldCount = true
        else heldList = true
        await oldResponses
      }
      const items = shouldHold ? [] : eventSent ? [incoming] : initialLoad ? [] : [{ ...incoming, notificationId: 1, message: 'Earlier notification' }]
      const count = shouldHold ? 0 : items.length
      return route.fulfill({ json: { success: true, data: isCount ? count : {
        content: items, number: 0, size: 15, totalElements: items.length,
        totalPages: items.length ? 1 : 0, first: true, last: true, empty: items.length === 0,
      } } })
    })
    await page.goto('/mypage/notifications')
    await expect.poll(() => page.evaluate(() => typeof (window as typeof window & { emitTestNotification?: unknown }).emitTestNotification)).toBe('function')
    if (!initialLoad) {
      await expect(page.getByText('Earlier notification', { exact: true })).toBeVisible()
      await page.getByRole('button', { name: /모두 읽음/ }).click()
    }
    await expect.poll(() => heldList && heldCount).toBe(true)
    eventSent = true
    await page.evaluate(notification => {
      (window as typeof window & { emitTestNotification: (notification: unknown) => void }).emitTestNotification(notification)
    }, incoming)
    releaseOld()
    await expect(page.getByText('New SSE notification', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /모두 읽음/ })).toBeEnabled()
    await expect(page.getByText('Earlier notification', { exact: true })).toHaveCount(0)
  })
}
