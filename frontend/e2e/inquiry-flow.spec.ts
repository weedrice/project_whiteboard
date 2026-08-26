import { expect, test, type Page, type Route } from '@playwright/test'
import { installMockApi, login, mockUser } from './fixtures/mockApi'

const apiResponse = (data: unknown) => ({
  success: true,
  data,
  error: null,
  timestamp: '2026-08-25T00:00:00Z',
})

const fulfillJson = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(apiResponse(data)),
})

function detail(overrides: Record<string, unknown> = {}) {
  return {
    inquiryId: 91,
    authorUserId: 7,
    authorName: 'Tester',
    category: 'TECHNICAL',
    title: '업로드 오류 문의',
    status: 'NEW',
    effectivePriority: 'HIGH',
    closureReason: null,
    closureDetail: null,
    allowedActions: { canAddMessage: true, canWithdraw: true, canClose: false },
    messages: [{
      messageId: 101,
      authorUserId: 7,
      authorName: 'Tester',
      messageType: 'USER_MESSAGE',
      content: '이미지 업로드가 완료되지 않습니다.',
      attachments: [],
      createdAt: '2026-08-25T09:00:00',
    }],
    histories: [{
      historyId: 201,
      actionType: 'CREATED',
      fromStatus: null,
      toStatus: 'NEW',
      createdAt: '2026-08-25T09:00:00',
    }],
    firstRespondedAt: null,
    resolvedAt: null,
    closedAt: null,
    createdAt: '2026-08-25T09:00:00',
    modifiedAt: '2026-08-25T09:00:00',
    ...overrides,
  }
}

async function installInquiryApi(page: Page) {
  let current = detail()
  const writes: Array<{ path: string, payload: unknown }> = []

  await page.route('**/api/v1/inquiries**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/inquiries' && method === 'POST') {
      const payload = request.postDataJSON() as Record<string, unknown>
      writes.push({ path, payload })
      const created = detail({ category: payload.category, title: payload.title })
      current = detail({
        category: payload.category,
        title: payload.title,
        status: 'RESOLVED',
        effectivePriority: null,
        allowedActions: { canAddMessage: true, canWithdraw: false, canClose: true },
      })
      return fulfillJson(route, created)
    }
    if (path === '/inquiries/91' && method === 'GET') return fulfillJson(route, current)
    if (path === '/inquiries/91/messages' && method === 'POST') {
      const payload = request.postDataJSON() as Record<string, unknown>
      writes.push({ path, payload })
      current = detail({
        category: current.category,
        title: current.title,
        messages: [
          ...(current.messages as unknown[]),
          {
            messageId: 102,
            authorUserId: 7,
            authorName: 'Tester',
            messageType: 'USER_MESSAGE',
            content: payload.content,
            attachments: [],
            createdAt: '2026-08-25T10:00:00',
          },
        ],
      })
      return fulfillJson(route, current)
    }
    return fulfillJson(route, [])
  })

  return { writes }
}

test('legacy inquiry URL creates a dedicated inquiry and reopens a resolved inquiry', async ({ page }) => {
  await installMockApi(page)
  const state = await installInquiryApi(page)
  await login(page)

  await page.goto('/inquiry')
  await expect(page).toHaveURL('/inquiries/new')

  const form = page.locator('form')
  await form.locator('select').selectOption('TECHNICAL')
  await form.getByRole('textbox', { name: '제목' }).fill('업로드 오류 문의')
  await form.getByRole('textbox', { name: /문의 내용/ }).fill('이미지 업로드가 완료되지 않습니다.')
  await form.locator('button[type="submit"]').click()

  await expect(page).toHaveURL('/inquiries/91')
  await expect(page.getByRole('heading', { name: '업로드 오류 문의' })).toBeVisible()
  await expect(page.locator('[data-inquiry-status="RESOLVED"]')).toBeVisible()

  await page.locator('form textarea').fill('아직 같은 문제가 발생합니다.')
  await page.locator('form button[type="submit"]').click()

  await expect.poll(() => state.writes).toHaveLength(2)
  await expect(page.locator('[data-inquiry-status="NEW"]')).toBeVisible()
  await expect(page.getByText('아직 같은 문제가 발생합니다.')).toBeVisible()
  expect(state.writes).toEqual([
    {
      path: '/inquiries',
      payload: {
        category: 'TECHNICAL',
        title: '업로드 오류 문의',
        content: '이미지 업로드가 완료되지 않습니다.',
        fileIds: [],
      },
    },
    {
      path: '/inquiries/91/messages',
      payload: { content: '아직 같은 문제가 발생합니다.', fileIds: [] },
    },
  ])
})

test('super admin starts and resolves a new inquiry from the support queue', async ({ page }) => {
  await installMockApi(page, { authenticated: true })
  const adminUser = { ...mockUser, userId: 1, displayName: 'Admin', role: 'SUPER_ADMIN' }
  let current = detail({ authorName: 'Tester' })
  const actions: string[] = []

  await page.route('**/api/v1/users/me', (route) => fulfillJson(route, adminUser))
  await page.route('**/api/v1/admin/support/inquiries**', async (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '')
    const method = route.request().method()
    if (path === '/admin/support/inquiries' && method === 'GET') {
      return fulfillJson(route, {
        content: [current], page: 0, size: 20, totalElements: 1, totalPages: 1,
        first: true, last: true,
      })
    }
    if (path === '/admin/support/inquiries/91' && method === 'GET') return fulfillJson(route, current)
    if (path.endsWith('/start') && method === 'POST') {
      actions.push('start')
      current = detail({ status: 'IN_PROGRESS', allowedActions: { canAddMessage: false, canWithdraw: false, canClose: false } })
      return fulfillJson(route, current)
    }
    if (path.endsWith('/reply') && method === 'POST') {
      actions.push('reply')
      current = detail({
        status: 'RESOLVED',
        effectivePriority: null,
        allowedActions: { canAddMessage: true, canWithdraw: false, canClose: true },
      })
      return fulfillJson(route, current)
    }
    return fulfillJson(route, current)
  })

  await page.goto('/admin/inquiries')
  await page.getByText('업로드 오류 문의').click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: '처리 시작' }).click()
  await expect.poll(() => actions).toEqual(['start'])
  await expect(page.getByRole('dialog').locator('[data-inquiry-status="IN_PROGRESS"]')).toBeVisible()

  await page.getByRole('dialog').locator('textarea').fill('조치가 완료되었습니다.')
  await page.getByRole('dialog').getByRole('button', { name: /답변 등록/ }).click()
  await expect.poll(() => actions).toEqual(['start', 'reply'])
  await expect(page.getByRole('dialog').locator('[data-inquiry-status="RESOLVED"]')).toBeVisible()
  expect(actions).toEqual(['start', 'reply'])
})
