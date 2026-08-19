import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Page, Route } from '@playwright/test'

const utf8ContentDispositionContract = readFileSync(
  resolve(process.cwd(), '../backend/src/test/resources/contracts/file-download-content-disposition-utf8.txt'),
  'utf8',
).trim()

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
  postContents?: string
  homeUnauthorizedOnce?: boolean
  refreshCount: number
  homeCount: number
  writes: Array<{ method: string, url: string, payload: unknown }>
  draft: Record<string, unknown> | null
  draftSaveCount: number
  draftGetCount: number
  draftGetDelayMs?: number
  draftSaveDelayMs?: number
  dropNextDraftSaveResponse?: boolean
}

export async function installMockApi(
  page: Page,
  overrides: Partial<MockApiState> = {},
  sharedState?: MockApiState,
) {
  const state: MockApiState = sharedState ?? {
    authenticated: false,
    refreshCount: 0,
    homeCount: 0,
    writes: [],
    draft: null,
    draftSaveCount: 0,
    draftGetCount: 0,
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
    if (path === '/users/me/drafts' && method === 'GET') {
      const content = state.draft ? [{
        draftId: state.draft.draftId,
        boardId: state.draft.boardId,
        boardUrl: state.draft.boardUrl,
        boardName: state.draft.boardName,
        originalPostId: state.draft.originalPostId ?? null,
        updatedAt: state.draft.updatedAt,
      }] : []
      return json(route, apiResponse({
        content,
        page: 0,
        size: 50,
        totalElements: content.length,
        totalPages: content.length ? 1 : 0,
        hasNext: false,
        hasPrevious: false,
      }))
    }
    if (/^\/drafts\/\d+$/.test(path) && method === 'GET') {
      state.draftGetCount += 1
      if (state.draftGetDelayMs) {
        await new Promise((resolve) => setTimeout(resolve, state.draftGetDelayMs))
      }
      return state.draft
        ? json(route, apiResponse(state.draft))
        : json(route, { success: false, data: null, error: { code: 'C004', message: 'Not found' } }, 404)
    }
    if (path === '/drafts' && method === 'POST') {
      const payload = request.postDataJSON() as Record<string, unknown>
      state.draftSaveCount += 1
      state.writes.push({ method, url: path, payload })
      const previousVersion = typeof state.draft?.version === 'number' ? state.draft.version : -1
      state.draft = {
        ...state.draft,
        ...payload,
        draftId: 91,
        clientDraftKey: payload.clientDraftKey ?? state.draft?.clientDraftKey,
        version: previousVersion + 1,
        boardId: 1,
        boardUrl: 'general',
        boardName: 'General',
        tags: payload.tags ?? [],
        fileIds: payload.fileIds ?? [],
        isNotice: payload.isNotice ?? false,
        isNsfw: payload.isNsfw ?? false,
        isSpoiler: payload.isSpoiler ?? false,
        isSecret: payload.isSecret ?? false,
        updatedAt: `2026-07-15T00:00:0${state.draftSaveCount}`,
        modifiedAt: `2026-07-15T00:00:0${state.draftSaveCount}`,
      }
      if (state.draftSaveDelayMs) {
        await new Promise((resolve) => setTimeout(resolve, state.draftSaveDelayMs))
      }
      if (state.dropNextDraftSaveResponse) {
        state.dropNextDraftSaveResponse = false
        return route.abort('failed')
      }
      return json(route, apiResponse(state.draft))
    }
    if (/^\/drafts\/\d+$/.test(path) && method === 'DELETE') {
      state.draft = null
      return json(route, apiResponse(null))
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
    if (path === '/files/31' && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        headers: {
          'Content-Disposition': utf8ContentDispositionContract,
        },
        body: 'mock-pdf',
      })
    }
    if (path === '/posts/1' && method === 'GET') {
      const contents = state.postContents ?? post.contents
      return json(route, apiResponse({ ...post, contents, content: contents }))
    }
    if (path === '/posts/1/related') return json(route, apiResponse([]))
    if (path === '/posts/1/comments') return json(route, apiResponse(emptyPage))
    if ((path === '/boards/general/posts' && method === 'POST') || (path === '/posts/1' && method === 'PUT')) {
      const payload = request.postDataJSON() as Record<string, unknown>
      state.writes.push({ method, url: path, payload })
      if (method === 'PUT' && typeof payload.contents === 'string') {
        state.postContents = payload.contents
      }
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
