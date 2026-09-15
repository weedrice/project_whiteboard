import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type Route } from '@playwright/test'
import { installMockApi, login, mockUser } from './fixtures/mockApi'

const viewports = [
  { name: 'mobile', size: { width: 390, height: 844 } },
  { name: 'desktop', size: { width: 1280, height: 800 } },
] as const

const emptyPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

const apiResponse = (data: unknown) => ({
  success: true,
  data,
  error: null,
  timestamp: '2026-09-15T00:00:00Z',
})

const fulfill = (route: Route, data: unknown) => route.fulfill({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(apiResponse(data)),
})

async function expectNoSeriousAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze()
  const violations = results.violations.filter((violation) =>
    violation.impact === 'serious' || violation.impact === 'critical')
  expect(violations, JSON.stringify(violations, null, 2)).toEqual([])
}

async function installCommonizedPageApi(page: Page) {
  let emoticonActive = true
  let visibilityWrites = 0
  const emoticon = () => ({
    emoticonId: 12,
    name: '공통 UI 노비콘',
    thumbnailUrl: '/images/default-emoticon.png',
    tags: ['공통화'],
    isActive: emoticonActive,
    creatorId: mockUser.userId,
    creatorName: mockUser.displayName,
    purchaseCount: 4,
    images: [{
      imageId: 121,
      emoticonId: 12,
      imageUrl: '/images/default-emoticon.png',
      sortOrder: 0,
    }],
    createdAt: '2026-09-01T09:00:00',
    modifiedAt: '2026-09-01T09:00:00',
  })

  await page.route('**/api/v1/inquiries**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/inquiries' && route.request().method() === 'GET') {
      return fulfill(route, {
        ...emptyPage,
        content: [{
          inquiryId: 81,
          category: 'TECHNICAL',
          title: '공통 UI 문의',
          status: 'IN_PROGRESS',
          lastPublicMessageSummary: '공통 컴포넌트가 적용되었는지 확인합니다.',
          modifiedAt: '2026-09-15T09:00:00',
        }],
        totalElements: 1,
        totalPages: 1,
      })
    }
    return route.fallback()
  })
  await page.route('**/api/v1/notifications**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/notifications/unread-count') return fulfill(route, 1)
    if (path === '/api/v1/notifications') {
      return fulfill(route, {
        ...emptyPage,
        content: [{
          notificationId: 71,
          notificationType: 'BADGE',
          message: '새 뱃지를 획득했습니다.',
          sourceType: 'SYSTEM',
          sourceId: 3,
          isRead: false,
          createdAt: '2026-09-15T09:00:00',
          actor: { userId: 0, authorType: 'SYSTEM', displayName: '시스템' },
          actorDisplayName: '시스템',
          actorInitial: '시',
        }],
        totalElements: 1,
        totalPages: 1,
      })
    }
    return route.fallback()
  })
  await page.route('**/api/v1/emoticons/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    if (path === '/api/v1/emoticons/popular') return fulfill(route, [emoticon()])
    if (path === '/api/v1/emoticons/search/all') {
      return fulfill(route, {
        ...emptyPage,
        content: [emoticon()],
        totalElements: 1,
        totalPages: 1,
      })
    }
    if (path === '/api/v1/emoticons/12/purchased') {
      return fulfill(route, { purchased: false, available: true, price: 100 })
    }
    if (path === '/api/v1/emoticons/12/visibility' && method === 'PATCH') {
      emoticonActive = false
      visibilityWrites += 1
      return fulfill(route, emoticon())
    }
    if (path === '/api/v1/emoticons/12') return fulfill(route, emoticon())
    return route.fallback()
  })

  await page.route('**/api/v1/search/popular**', (route) => fulfill(route, {
    keywords: [{ keyword: 'Vue 공통화', count: 7 }],
  }))
  await page.route('**/api/v1/search/recent**', (route) => fulfill(route, {
    content: [{ logId: 51, keyword: 'dialog contract', searchedAt: '2026-09-15T08:00:00' }],
    page: 0,
    size: 8,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
  }))
  await page.route('**/api/v1/tags**', (route) => fulfill(route, {
    tags: [{ tagId: 31, tagName: 'frontend', postCount: 3 }],
  }))

  return {
    getVisibilityWrites: () => visibilityWrites,
  }
}

async function installAdminApi(page: Page) {
  const adminUser = { ...mockUser, userId: 1, displayName: 'Admin', role: 'SUPER_ADMIN' }
  await page.route('**/api/v1/users/me', (route) => fulfill(route, adminUser))
  await page.route('**/api/v1/admin/stats**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/admin/stats/deep') {
      return fulfill(route, {
        daily: [],
        topBoards: [],
        moderation: {
          pendingReports: 0,
          resolvedReports: 0,
          rejectedReports: 0,
          autoBlinds: 0,
          managerBlinds: 0,
        },
      })
    }
    return fulfill(route, { totalUsers: 1, pendingReports: 0, activeUsers: 1 })
  })
  await page.route('**/api/v1/admin/moderation-audits**', (route) => fulfill(route, emptyPage))
}

for (const viewport of viewports) {
  test(`commonized inquiry, notification, emoticon, and search pages are axe-clean (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page)
    const commonizedApi = await installCommonizedPageApi(page)
    await login(page)

    await page.goto('/inquiries')
    await expect(page.getByRole('heading', { level: 1, name: '내 문의' })).toBeVisible()
    await expect(page.locator('select')).toHaveCount(2)
    await expect(page.getByRole('heading', { level: 2, name: '공통 UI 문의' })).toBeVisible()
    await expect(page.locator('[data-inquiry-status="IN_PROGRESS"]')).toBeVisible()
    await expectNoSeriousAccessibilityViolations(page)

    await page.goto('/mypage/notifications')
    await expect(page.getByRole('heading', { level: 1, name: '알림 목록' })).toBeVisible()
    await expect(page.getByRole('button', { name: /새 뱃지를 획득했습니다/ })).toBeVisible()
    await expectNoSeriousAccessibilityViolations(page)

    await page.goto('/emoticons')
    await expect(page.getByRole('heading', { level: 1, name: '노비콘' })).toBeVisible()
    await expect(page.locator('.list-search-group')).toBeVisible()
    await page.getByRole('button', { name: '공통 UI 노비콘' }).first().click()
    await expect(page).toHaveURL('/emoticons/12')
    await expect(page.getByRole('heading', { level: 1, name: '공통 UI 노비콘' })).toBeVisible()
    await page.getByRole('button', { name: '판매 숨기기' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '예' }).click()
    await expect.poll(commonizedApi.getVisibilityWrites).toBe(1)
    await expect(page.getByText('판매 중단', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '수정' }).click()
    await expect(page).toHaveURL('/emoticons/12/edit')
    await expect(page.getByRole('heading', { level: 1, name: '노비콘 수정' })).toBeVisible()
    await expect(page.locator('form fieldset > .nv-surface')).toHaveCount(4)
    await expectNoSeriousAccessibilityViolations(page)

    await page.goto('/search')
    await expect(page.getByRole('heading', { level: 1, name: '검색 결과' })).toBeVisible()
    await expect(page.locator('aside section')).toHaveCount(3)
    await expect(page.getByText('Vue 공통화', { exact: true })).toBeVisible()
    await expect(page.getByText('#frontend', { exact: true })).toBeVisible()
    await expect(page.getByText('dialog contract', { exact: true })).toBeVisible()
    await expectNoSeriousAccessibilityViolations(page)
  })

  test(`commonized admin dashboard is responsive and axe-clean (${viewport.name})`, async ({ page }) => {
    await page.setViewportSize(viewport.size)
    await installMockApi(page, { authenticated: true })
    await installAdminApi(page)
    await page.goto('/admin/dashboard')

    await expect(page.getByRole('heading', { level: 1, name: '대시보드' })).toBeVisible()
    const periodControl = page.getByRole('group', { name: '심화 통계' })
    await expect(periodControl).toBeVisible()
    await periodControl.getByRole('button', { name: '90일' }).click()
    await expect(periodControl.getByRole('button', { name: '90일' })).toHaveAttribute('aria-pressed', 'true')
    await expectNoSeriousAccessibilityViolations(page)
  })
}
