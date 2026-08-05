import { expect, test, type Page } from '@playwright/test'
import { installMockApi, login, type MockApiState } from './fixtures/mockApi'

const editor = (page: Page) => page.locator('.ProseMirror').first()

async function openComposer(page: Page, draftId?: number) {
  const query = draftId == null ? '' : `?draftId=${draftId}`
  await page.goto(`/board/general/write${query}`)
  await expect(page.locator('#title')).toBeVisible()
  await expect(page.getByText('자동 임시 저장이 준비되었습니다.', { exact: true }).first()).toBeVisible()
}

function serverDraft(overrides: Record<string, unknown> = {}) {
  return {
    draftId: 91,
    clientDraftKey: 'existing-client-draft-key',
    version: 0,
    boardId: 1,
    boardUrl: 'general',
    boardName: 'General',
    originalPostId: null,
    title: 'Server title',
    contents: '<p>Server body</p>',
    tags: [],
    fileIds: [],
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    isSecret: false,
    updatedAt: '2026-07-15T00:00:00Z',
    modifiedAt: '2026-07-15T00:00:00Z',
    ...overrides,
  }
}

test('body-only content is autosaved to the server', async ({ page }) => {
  const state = await installMockApi(page)
  await login(page)
  await openComposer(page)

  await editor(page).fill('Body only draft')

  await expect.poll(() => state.draftSaveCount).toBe(1)
  expect(state.writes[0]?.payload).toMatchObject({
    title: '',
    clientDraftKey: expect.any(String),
  })
  expect((state.writes[0]?.payload as { contents?: string }).contents).toContain('Body only draft')
})

test('an edit made during slow recovery is preserved and reported as a conflict', async ({ page }) => {
  const state = await installMockApi(page, {
    draft: serverDraft(),
    draftGetDelayMs: 1_500,
  })
  await login(page)

  await page.goto('/board/general/write?draftId=91')
  await expect.poll(() => state.draftGetCount).toBe(1)
  await page.locator('#title').fill('Typed while restoring')

  await expect(page.locator('#title')).toHaveValue('Typed while restoring')
  await expect(page.getByText(/로컬 초안과 서버 초안/).first()).toBeVisible()
})

test('a failed offline autosave retries automatically when connectivity returns', async ({ page }) => {
  const state = await installMockApi(page)
  await login(page)
  await openComposer(page)
  state.dropNextDraftSaveResponse = true

  await page.locator('#title').fill('Offline edit')
  await expect.poll(() => state.draftSaveCount).toBe(1)

  await page.evaluate(() => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => false })
    window.dispatchEvent(new Event('offline'))
    Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => true })
    window.dispatchEvent(new Event('online'))
  })
  await expect.poll(() => state.draftSaveCount).toBe(2)
  await expect(page.getByText(/에 저장됨$/).first()).toBeVisible()
})

test('retry after a dropped response reuses the client key and one logical draft', async ({ page }) => {
  const state = await installMockApi(page)
  await login(page)
  await openComposer(page)
  state.dropNextDraftSaveResponse = true

  await page.locator('#title').fill('Retry-safe draft')
  await expect.poll(() => state.draftSaveCount).toBe(1)

  await page.evaluate(() => window.dispatchEvent(new Event('online')))
  await expect.poll(() => state.draftSaveCount).toBe(2)

  const saves = state.writes.filter((write) => write.url === '/drafts')
  expect(saves).toHaveLength(2)
  expect((saves[0]?.payload as { clientDraftKey?: string }).clientDraftKey).toBe(
    (saves[1]?.payload as { clientDraftKey?: string }).clientDraftKey,
  )
  expect(state.draft?.draftId).toBe(91)
})

test('tabs notify about remote changes without replacing either tab content', async ({ page, context }) => {
  const state = await installMockApi(page, { draft: serverDraft() })
  await login(page)
  await openComposer(page, 91)
  await expect(page.locator('#title')).toHaveValue('Server title')

  const secondPage = await context.newPage()
  await installMockApi(secondPage, {}, state as MockApiState)
  await openComposer(secondPage, 91)
  await expect(secondPage.locator('#title')).toHaveValue('Server title')

  await page.locator('#title').fill('Saved in first tab')
  await expect.poll(() => state.draftSaveCount).toBe(1)
  await expect(secondPage.locator('#title')).toHaveValue('Server title')
  await expect(secondPage.getByText(/로컬 초안과 서버 초안/).first()).toBeVisible()

  await page.locator('#title').fill('Unsaved first-tab edit')
  await expect(secondPage.locator('#title')).toHaveValue('Server title')

  await secondPage.locator('#title').fill('Saved in second tab')
  await secondPage.getByRole('button', {
    name: /^(임시 저장|로컬 초안으로 덮어쓰기)$/,
  }).first().click()

  await expect.poll(() => state.draftSaveCount).toBe(2)
  await expect(page.locator('#title')).toHaveValue('Unsaved first-tab edit')
  await expect(page.getByText(/로컬 초안과 서버 초안/).first()).toBeVisible()
})

test('a draft deleted in another tab can be preserved as a new draft', async ({ page, context }) => {
  const state = await installMockApi(page)
  await login(page)
  await openComposer(page)
  await page.locator('#title').fill('Keep this local content')
  await expect.poll(() => state.draftSaveCount).toBe(1)
  const originalClientKey = (state.writes[0]?.payload as { clientDraftKey?: string }).clientDraftKey

  const secondPage = await context.newPage()
  await installMockApi(secondPage, {}, state as MockApiState)
  await secondPage.goto('/')
  await secondPage.evaluate(() => {
    localStorage.setItem(
      'noviis:draft-deleted:7:91',
      JSON.stringify({ deletedAt: new Date().toISOString() }),
    )
  })

  await expect(page.getByText(/다른 위치에서 삭제되었습니다/).first()).toBeVisible()
  await expect(page.locator('#title')).toHaveValue('Keep this local content')
  await page.getByRole('button', { name: '새 초안으로 저장', exact: true }).first().click()
  await expect.poll(() => state.draftSaveCount).toBe(2)

  const recreated = state.writes[1]?.payload as { draftId?: number, clientDraftKey?: string }
  expect(recreated.draftId).toBeUndefined()
  expect(recreated.clientDraftKey).not.toBe(originalClientKey)
})
