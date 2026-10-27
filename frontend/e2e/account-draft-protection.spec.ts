import { expect, test, type Page, type Route } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true }
const fulfill = (route: Route, data: unknown) => route.fulfill({ json: { success: true, data, error: null } })
const viewports = [
  { name: 'mobile', size: { width: 390, height: 844 } },
  { name: 'desktop', size: { width: 1280, height: 800 } },
] as const

async function installProfileApi(page: Page) {
  await installMockApi(page, { authenticated: true })
  const state = { user: { ...mockUser, profileImageChangeFreeAvailable: true }, writes: 0 }
  await page.route('**/api/v1/users/me/posts?**', route => fulfill(route, emptyPage))
  await page.route('**/api/v1/users/me/comments?**', route => fulfill(route, emptyPage))
  await page.route('**/api/v1/users/me/agents', route => fulfill(route, { agents: [] }))
  await page.route('**/api/v1/users/me', route => {
    if (route.request().method() === 'PUT') {
      state.writes++
      state.user.displayName = route.request().postDataJSON().displayName
    }
    return fulfill(route, state.user)
  })
  return state
}

async function installMailboxApi(page: Page) {
  await installMockApi(page, { authenticated: true })
  const message = {
    messageId: 2, content: 'A received message', isRead: true, createdAt: '2026-10-01T09:00:00',
    partner: { userId: 2, displayName: 'Partner 2' },
    sender: { userId: 2, displayName: 'Partner 2' }, receiver: mockUser,
  }
  const sent: Array<{ receiverId: number, content: string }> = []
  await page.route('**/api/v1/messages**', route => {
    const path = new URL(route.request().url()).pathname
    if (route.request().method() === 'POST' && path.endsWith('/messages')) {
      sent.push(route.request().postDataJSON())
      return fulfill(route, 3)
    }
    if (path.endsWith('/unread-count')) return fulfill(route, 0)
    if (path.endsWith('/messages/2')) return fulfill(route, message)
    return fulfill(route, { ...emptyPage, content: [message], totalElements: 1, totalPages: 1 })
  })
  return sent
}

for (const viewport of viewports) {
  test(`profile name and photo drafts require explicit discard (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    const state = await installProfileApi(page)
    await page.goto('/mypage')
    const open = page.getByRole('button', { name: '프로필 수정', exact: true })
    await open.click()
    const profile = page.getByRole('dialog', { name: '프로필 수정', exact: true, includeHidden: true })
    const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
    const name = profile.locator('#profile-display-name')
    await name.fill('Unsaved profile draft')
    await profile.locator('#profile-photo-input').setInputFiles({
      name: 'profile.png', mimeType: 'image/png',
      buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'),
    })
    await expect(profile.locator('img[src^="blob:"]')).toBeVisible()
    await name.focus()
    await name.press('Escape')
    await expect(confirmation).toBeVisible()
    await expect(confirmation).toContainText('저장하지 않은 프로필 변경사항을 버리고 닫으시겠습니까?')
    await expect(profile).toHaveAttribute('inert', '')
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(confirmation).toHaveCount(0)
    await expect(name).toHaveValue('Unsaved profile draft')
    await expect(name).toBeFocused()
    await expect(profile.locator('img[src^="blob:"]')).toBeVisible()
    await profile.getByRole('button', { name: '닫기', exact: true }).click()
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(profile).toHaveCount(0)
    expect(state.writes).toBe(0)

    await open.click()
    await expect(name).toHaveValue('Tester')
    await expect(profile.locator('img[src^="blob:"]')).toHaveCount(0)
    await profile.getByRole('button', { name: '닫기', exact: true }).click()
    await expect(profile).toHaveCount(0)
    await expect(confirmation).toHaveCount(0)

    await open.click()
    await name.fill('Saved profile')
    await name.press('Enter')
    await expect(profile).toHaveCount(0)
    await expect(confirmation).toHaveCount(0)
    await expect(page.locator('#user-dropdown-trigger')).toContainText('Saved profile')
    expect(state.writes).toBe(1)
  })

  test(`mailbox reply survives cancellation and clears only on discard or send (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    const sent = await installMailboxApi(page)
    await page.goto('/mypage/messages?partnerId=2')
    const conversation = page.getByRole('dialog', { name: 'Partner 2님과의 최근 대화', exact: true, includeHidden: true })
    const confirmation = page.getByRole('dialog', { name: '확인', exact: true })
    const reply = conversation.getByRole('textbox', { name: '답장 보내기', exact: true, includeHidden: true })
    await reply.fill('Unsent mailbox reply')
    await reply.press('Escape')
    await expect(confirmation).toBeVisible()
    await expect(conversation).toHaveAttribute('inert', '')
    await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
    await expect(reply).toHaveValue('Unsent mailbox reply')
    await expect(reply).toBeFocused()
    await expect(page).toHaveURL(/partnerId=2/)
    await conversation.getByRole('button', { name: '닫기', exact: true }).click()
    await confirmation.getByRole('button', { name: '예', exact: true }).click()
    await expect(conversation).toHaveCount(0)
    await expect(page).not.toHaveURL(/partnerId=/)
    expect(sent).toHaveLength(0)

    await page.getByRole('button', { name: 'Partner 2 쪽지 열기', exact: true }).click()
    await expect(reply).toHaveValue('')
    await reply.fill('Send this reply')
    await conversation.getByRole('button', { name: '전송', exact: true }).click()
    await expect(reply).toHaveValue('')
    await expect(confirmation).toHaveCount(0)
    expect(sent).toEqual([{ receiverId: 2, content: 'Send this reply' }])
    await conversation.getByRole('button', { name: '닫기', exact: true }).click()
    await expect(conversation).toHaveCount(0)
    await expect(confirmation).toHaveCount(0)
  })
}
