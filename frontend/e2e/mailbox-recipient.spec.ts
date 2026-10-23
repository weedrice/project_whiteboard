import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test('partner route changes clear drafts and send replies to the visible conversation', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const sends: Array<{ receiverId: number; content: string }> = []
  const messages = [2, 3].map((partnerId) => ({
    messageId: partnerId,
    partner: { userId: partnerId, displayName: `Partner ${partnerId}` },
    content: `Conversation with ${partnerId}`,
    isRead: true,
    sentByMe: false,
    createdAt: '2026-04-16T11:00:00',
  }))
  await page.route('**/api/v1/messages**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    let data: unknown = null
    if (request.method() === 'POST' && path.endsWith('/messages')) {
      sends.push(request.postDataJSON() as { receiverId: number; content: string })
      data = 10
    } else if (path.endsWith('/unread-count')) {
      data = 0
    } else {
      const partner = path.match(/\/conversations\/(\d+)$/)?.[1]
      const content = partner ? messages.filter((message) => message.partner.userId === Number(partner)) : messages
      data = { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true }
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ success: true, data }) })
  })

  await page.goto('/mypage/messages?partnerId=2')
  const dialog = page.getByRole('dialog')
  const reply = dialog.getByRole('textbox', { name: '답장 보내기' })
  await expect(dialog.getByText('Conversation with 2', { exact: true })).toBeVisible()
  await reply.fill('Reply for partner 2')
  const refreshedConversation = page.waitForResponse((response) => response.url().includes('/messages/conversations/2'))
  await dialog.getByRole('button', { name: '전송', exact: true }).click()
  await expect.poll(() => sends).toEqual([{ receiverId: 2, content: 'Reply for partner 2' }])
  await expect(reply).toHaveValue('')
  // Wait for the successful-send refresh before switching the retained route component.
  await refreshedConversation
  await reply.fill('Private draft for partner 2')
  await page.evaluate(() => {
    window.history.pushState(window.history.state, '', '/mypage/messages?partnerId=3')
    window.dispatchEvent(new PopStateEvent('popstate', { state: window.history.state }))
  })
  await expect(dialog.getByText('Conversation with 3', { exact: true })).toBeVisible()
  await expect(reply).toHaveValue('')
  await reply.fill('Reply for partner 3')
  await dialog.getByRole('button', { name: '전송', exact: true }).click()
  await expect.poll(() => sends).toEqual([
    { receiverId: 2, content: 'Reply for partner 2' },
    { receiverId: 3, content: 'Reply for partner 3' },
  ])
})
