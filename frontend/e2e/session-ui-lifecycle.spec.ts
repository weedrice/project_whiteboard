import { expect, test, type Route } from '@playwright/test'
import { installMockApi, mockSettings, mockUser } from './fixtures/mockApi'

function fulfillJson(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data, error: null }),
  })
}

test('repeated Enter while a profile image save is pending sends one upload and one write', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const saveRequest = Promise.withResolvers<Route>()
  let uploads = 0
  const writes: Array<{ displayName: string, profileImageId: number }> = []
  await page.route('**/api/v1/users/me/posts?**', route => fulfillJson(route, {
    content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
  }))
  await page.route('**/api/v1/users/me/comments?**', route => fulfillJson(route, {
    content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
  }))
  await page.route('**/api/v1/users/me/agents', route => fulfillJson(route, { agents: [] }))
  await page.route('**/api/v1/files/upload', (route) => {
    uploads += 1
    return fulfillJson(route, { fileId: 500 + uploads })
  })
  await page.route('**/api/v1/users/me', (route) => {
    if (route.request().method() === 'PUT') {
      writes.push(route.request().postDataJSON())
      if (writes.length === 1) {
        saveRequest.resolve(route)
        return
      }
      return fulfillJson(route, { ...mockUser, ...writes.at(-1) })
    }
    return fulfillJson(route, { ...mockUser, profileImageChangeFreeAvailable: true })
  })

  await page.goto('/mypage')
  await page.getByRole('button', { name: '프로필 수정', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '프로필 수정', exact: true })
  await dialog.locator('#profile-photo-input').setInputFiles({
    name: 'profile.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'),
  })
  await expect(dialog.locator('img[src^="blob:"]')).toBeVisible()
  const nameInput = dialog.locator('#profile-display-name')
  await nameInput.fill('Updated Tester')
  await nameInput.press('Enter')
  const pendingSave = await saveRequest.promise
  await expect(dialog.getByRole('button', { name: '저장 중...', exact: true })).toBeDisabled()

  await nameInput.press('Enter')
  await nameInput.press('Enter')
  await page.evaluate(() => new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  }))
  await expect(dialog).toBeVisible()
  expect(uploads).toBe(1)
  expect(writes).toEqual([{ displayName: 'Updated Tester', profileImageId: 501 }])

  await fulfillJson(pendingSave, { ...mockUser, ...writes[0] })
  await expect(dialog).toBeHidden()
  expect(uploads).toBe(1)
  expect(writes).toHaveLength(1)
})

test('bootstrap restores settings after the user identity is hydrated', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const meRequest = Promise.withResolvers<Route>()
  const settingsRequest = Promise.withResolvers<Route>()
  let settingsRequests = 0
  await page.route('**/api/v1/users/me', route => meRequest.resolve(route))
  await page.route('**/api/v1/users/me/settings', (route) => {
    settingsRequests += 1
    settingsRequest.resolve(route)
  })

  await page.goto('/')
  const meRoute = await meRequest.promise
  expect(settingsRequests).toBe(0)
  await fulfillJson(meRoute, mockUser)
  const settingsRoute = await settingsRequest.promise
  await fulfillJson(settingsRoute, {
    ...mockSettings, theme: 'DARK', language: 'en', timezone: 'America/New_York',
  })

  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  await expect(page.locator('html')).toHaveClass(/dark/)
  await expect.poll(() => page.evaluate(() => localStorage.getItem('displayTimeZone')))
    .toBe('America/New_York')
  expect(settingsRequests).toBe(1)
})

test('a submitting inquiry blocks leaving until success and refreshes its cached list', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const createRequest = Promise.withResolvers<Route>()
  let created = false
  let createRequests = 0
  const item = {
    inquiryId: 92, category: 'SERVICE_USE', title: '지연 응답 문의', status: 'NEW',
    lastPublicMessageSummary: '전송이 완료될 때까지 작성 화면을 유지합니다.',
    modifiedAt: '2026-10-10T09:00:00',
  }
  const detail = {
    ...item,
    authorUserId: mockUser.userId,
    authorName: mockUser.displayName,
    createdAt: item.modifiedAt,
    allowedActions: { canAddMessage: true, canWithdraw: true, canClose: false },
    messages: [{
      messageId: 101, authorUserId: mockUser.userId, authorName: mockUser.displayName,
      messageType: 'USER_MESSAGE', content: item.lastPublicMessageSummary,
      attachments: [], createdAt: item.modifiedAt,
    }],
    histories: [],
  }
  await page.route('**/api/v1/inquiries**', (route) => {
    if (route.request().method() === 'POST') {
      createRequests += 1
      createRequest.resolve(route)
      return
    }
    if (new URL(route.request().url()).pathname.endsWith(`/inquiries/${item.inquiryId}`)) {
      return fulfillJson(route, detail)
    }
    return fulfillJson(route, {
      content: created ? [item] : [], page: 0, size: 20,
      totalElements: created ? 1 : 0, totalPages: created ? 1 : 0,
      first: true, last: true,
    })
  })

  // Warm the list cache so successful submission must invalidate the old empty result.
  await page.goto('/inquiries')
  await expect(page.getByText('등록한 문의가 없습니다.', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: '새 문의', exact: true }).click()
  await expect(page).toHaveURL('/inquiries/new')
  const form = page.locator('form')
  await form.getByRole('textbox', { name: '제목' }).fill(item.title)
  await form.getByRole('textbox', { name: /문의 내용/ }).fill(item.lastPublicMessageSummary)
  await form.locator('button[type="submit"]').click()
  const createRoute = await createRequest.promise
  await page.locator('header').getByRole('link', { name: '내 문의', exact: true }).click()
  await page.evaluate(() => new Promise<void>((resolve) => {
    requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
  }))
  await expect(page).toHaveURL('/inquiries/new')
  await expect(form.getByRole('textbox', { name: '제목' })).toHaveValue(item.title)
  await expect(form.getByRole('textbox', { name: /문의 내용/ })).toHaveValue(item.lastPublicMessageSummary)
  await expect(form.locator('button[type="submit"]')).toBeDisabled()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(createRequests).toBe(1)

  created = true
  await fulfillJson(createRoute, detail)
  await expect(page).toHaveURL(`/inquiries/${item.inquiryId}`)
  await expect(page.getByRole('heading', { name: item.title, exact: true })).toBeVisible()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.locator('header').getByRole('button', { name: '목록', exact: true }).click()
  await expect(page).toHaveURL('/inquiries')
  await expect(page.getByRole('heading', { name: '내 문의', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: new RegExp(item.title) })).toBeVisible()
  expect(createRequests).toBe(1)
})
