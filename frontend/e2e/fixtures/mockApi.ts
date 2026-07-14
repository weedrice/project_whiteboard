import type { Page, Route } from '@playwright/test'

export const mockUser = {
  userId: 7,
  loginId: 'tester',
  displayName: 'Tester',
  email: 'tester@example.com',
  role: 'USER',
  status: 'ACTIVE',
  profileImageUrl: null,
  theme: 'LIGHT',
  isEmailVerified: true,
  emailVerified: true,
  createdAt: '2026-01-01T00:00:00',
  points: 100,
}

export const mockSettings = {
  theme: 'LIGHT',
  language: 'ko',
  timezone: 'Asia/Seoul',
  hideNsfw: true,
  pushEnabled: false,
  onboardingCompletedAt: '2026-01-01T00:00:00',
}

const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true }
const board = {
  boardId: 1, boardName: 'General', boardUrl: 'general', description: '', sortOrder: 1,
  subscriberCount: 0, postCount: 1, isSubscribed: false, isActive: true, isPublic: true,
  subscriptionAccessible: true, allowNsfw: true, isAdmin: false, categories: [], latestPosts: [],
  agentUseYn: false,
  categories: [{ categoryId: 1, name: 'General', minWriteRole: 'USER' }],
}
const post = {
  postId: 1, title: 'Accessible post', contents: '<p>Post body</p>', content: '<p>Post body</p>',
  viewCount: 1, likeCount: 0, commentCount: 0, isLiked: false, isScrapped: false,
  isNotice: false, isNsfw: false, isSpoiler: false, isSecret: false, isDeleted: false,
  createdAt: '2026-01-01T00:00:00', modifiedAt: '2026-01-01T00:00:00',
  board: { boardId: 1, boardName: 'General', boardUrl: 'general' },
  author: { userId: 7, loginId: 'tester', displayName: 'Tester', profileImageUrl: null },
  tags: [], files: [], poll: null,
}

function apiResponse(data: unknown) {
  return { success: true, data, error: null, timestamp: '2026-07-15T00:00:00Z' }
}

function json(route: Route, data: unknown, status = 200) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
}

export type MockApiState = {
  authenticated: boolean
  homeUnauthorizedOnce?: boolean
  refreshCount: number
  homeCount: number
  writes: Array<{ method: string, url: string, payload: unknown }>
}

export async function installMockApi(page: Page, overrides: Partial<MockApiState> = {}) {
  const state: MockApiState = {
    authenticated: false,
    refreshCount: 0,
    homeCount: 0,
    writes: [],
    ...overrides,
  }

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()

    if (path === '/auth/login' && method === 'POST') {
      state.authenticated = true
      return json(route, apiResponse({ accessToken: 'access-login', expiresIn: 3600, user: mockUser }))
    }
    if (path === '/auth/refresh' && method === 'POST') {
      state.refreshCount += 1
      return state.authenticated
        ? json(route, apiResponse({ accessToken: `access-refresh-${state.refreshCount}`, expiresIn: 3600 }))
        : json(route, { success: false, data: null, error: { code: 'UNAUTHORIZED', message: 'Unauthorized' } }, 401)
    }
    if (path === '/auth/logout' && method === 'POST') {
      state.authenticated = false
      return json(route, apiResponse(null))
    }
    if (path === '/users/me') {
      return state.authenticated ? json(route, apiResponse(mockUser)) : json(route, {}, 401)
    }
    if (path === '/home/landing') {
      state.homeCount += 1
      if (state.homeUnauthorizedOnce && state.homeCount === 1) return json(route, {}, 401)
      return json(route, apiResponse({
        curatedPosts: [], latestPosts: [], boards: [],
        stats: { boardCount: 1, postCount: 1, liveCount: 0, onlineCount: 1, postsToday: 1,
          postsTodayDeltaPercent: 0, activeBoardCount: 1, newMembersLast24Hours: 0, commentsToday: 0 },
      }))
    }
    if (path === '/configs/public') return json(route, apiResponse([]))
    if (path === '/users/me/settings') {
      if (method === 'PUT') {
        state.writes.push({ method, url: path, payload: request.postDataJSON() })
        return json(route, apiResponse({ ...mockSettings, ...request.postDataJSON() }))
      }
      return json(route, apiResponse(mockSettings))
    }
    if (path === '/users/me/notification-settings') return json(route, apiResponse([]))
    if (path === '/users/me/sessions') return json(route, apiResponse([]))
    if (path === '/users/me/login-history') return json(route, apiResponse(emptyPage))
    if (path === '/users/me/keyword-subscriptions') return json(route, apiResponse([]))
    if (path === '/users/me/post-series') return json(route, apiResponse([]))
    if (path === '/boards/general/categories') return json(route, apiResponse([]))
    if (path === '/boards/general' && method === 'GET') return json(route, apiResponse(board))
    if (path === '/boards') return json(route, apiResponse([board]))
    if (path === '/posts/1' && method === 'GET') return json(route, apiResponse(post))
    if (path === '/posts/1/related') return json(route, apiResponse([]))
    if (path === '/posts/1/comments') return json(route, apiResponse(emptyPage))
    if ((path === '/boards/general/posts' && method === 'POST') || (path === '/posts/1' && method === 'PUT')) {
      state.writes.push({ method, url: path, payload: request.postDataJSON() })
      return json(route, apiResponse(method === 'POST' ? { postId: 55 } : 1))
    }
    if (path.endsWith('/view') || path.endsWith('/history')) return json(route, apiResponse(null))
    return json(route, apiResponse([]))
  })

  return state
}

export async function login(page: Page) {
  await page.goto('/login')
  await page.locator('#login-id').fill('tester')
  await page.locator('#password').fill('password')
  await page.locator('form button[type="submit"]').click()
  await page.waitForURL((url) => !url.pathname.includes('login'))
}
