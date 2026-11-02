import { expect, test, type Page } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

async function installMailbox(page: Page) {
  await installMockApi(page, { authenticated: true })
  const message = {
    messageId: 2,
    partner: { userId: 2, displayName: 'Partner 2' },
    content: 'Conversation with 2',
    isRead: true,
    sentByMe: false,
    createdAt: '2026-04-16T11:00:00',
  }
  await page.route('**/api/v1/messages**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const data = path.endsWith('/unread-count') ? 0
      : path.endsWith('/messages/2') ? message
        : { content: [message], page: 0, size: 20, totalElements: 1, totalPages: 1, first: true, last: true }
    await route.fulfill({ json: { success: true, data } })
  })
  await page.route('**/api/v1/notifications?**', (route) => route.fulfill({ json: {
    success: true,
    data: { content: [], page: 0, size: 15, totalElements: 0, totalPages: 0, first: true, last: true },
  } }))
}

for (const viewport of [
  { name: 'desktop', size: { width: 1280, height: 800 } },
  { name: 'mobile', size: { width: 390, height: 844 } },
]) {
  test(`mailbox reply survives cancelled dismissal (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMailbox(page)
    await page.goto('/mypage/messages?partnerId=2')
    const conversation = page.getByRole('dialog', { name: /Partner 2/, includeHidden: true })
    const reply = conversation.getByRole('textbox', { name: '답장 보내기', includeHidden: true })
    const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
    await reply.fill('Keep this mailbox reply')
    await reply.press('Escape')
    await expect(confirmation).toBeVisible()
    await expect(conversation).toHaveAttribute('inert', '')
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(reply).toHaveValue('Keep this mailbox reply')
    await expect(reply).toBeFocused()

    if (viewport.name === 'desktop') {
      await conversation.click({ position: { x: 5, y: 5 } })
      await expect(confirmation).toBeVisible()
      await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
      await expect(reply).toHaveValue('Keep this mailbox reply')
    }

    await conversation.getByRole('button', { name: '닫기', exact: true }).click()
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(conversation).toHaveCount(0)
    await expect(page).not.toHaveURL(/partnerId=/)
    await page.locator('li > button').filter({ hasText: 'Partner 2' }).click()
    await expect(reply).toHaveValue('')
  })

  test(`browser back preserves mailbox reply until discard is approved (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMailbox(page)
    await page.goto('/mypage/notifications')
    await page.locator('a[href="/mypage/messages"]:visible').first().click()
    await page.locator('li > button').filter({ hasText: 'Partner 2' }).click()
    const conversation = page.getByRole('dialog', { name: /Partner 2/, includeHidden: true })
    const reply = conversation.getByRole('textbox', { name: '답장 보내기', includeHidden: true })
    const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
    await reply.fill('Reply before leaving')
    await page.evaluate(() => window.history.back())
    await expect(confirmation).toBeVisible()
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(page).toHaveURL(/\/mypage\/messages$/)
    await expect(reply).toHaveValue('Reply before leaving')
    await page.evaluate(() => window.history.back())
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(page).toHaveURL(/\/mypage\/notifications\/?$/)
    await expect(conversation).toHaveCount(0)
  })
}
