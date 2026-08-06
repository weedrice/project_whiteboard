import { expect, test, type Page } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

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

test('server recovery and autosave remain available when draft localStorage is blocked', async ({ page }) => {
  const state = await installMockApi(page, { draft: serverDraft() })
  await login(page)
  await page.addInitScript(() => {
    const prototype = Object.getPrototypeOf(window.localStorage) as Storage
    const originalGetItem = prototype.getItem
    const originalSetItem = prototype.setItem
    const originalRemoveItem = prototype.removeItem
    const isDraftKey = (key: string) => key.startsWith('noviis:draft')
    const blocked = () => { throw new DOMException('Storage is disabled', 'SecurityError') }

    prototype.getItem = function getItem(key: string) {
      if (isDraftKey(key)) return blocked()
      return originalGetItem.call(this, key)
    }
    prototype.setItem = function setItem(key: string, value: string) {
      if (isDraftKey(key)) return blocked()
      return originalSetItem.call(this, key, value)
    }
    prototype.removeItem = function removeItem(key: string) {
      if (isDraftKey(key)) return blocked()
      return originalRemoveItem.call(this, key)
    }
  })

  await page.goto('/board/general/write?draftId=91')
  await expect(page.locator('#title')).toBeVisible()
  await expect(page.locator('#title')).toHaveValue('Server title')
  const localStorageWarning = page.getByRole('complementary')
    .getByText('브라우저 저장 공간에 기록하지 못했습니다.', { exact: true })
  await expect(localStorageWarning).toBeVisible()

  await page.locator('#title').fill('Saved without localStorage')

  await expect.poll(() => state.draftSaveCount).toBe(1)
  expect(state.draft).toMatchObject({ title: 'Saved without localStorage' })
  await expect(localStorageWarning).toBeVisible()
})

test('an edit made while a save response is pending is queued and persisted next', async ({ page }) => {
  const state = await installMockApi(page, { draftSaveDelayMs: 1_000 })
  await login(page)
  await openComposer(page)

  await page.locator('#title').fill('First edit')
  await expect.poll(() => state.draftSaveCount).toBe(1)

  await page.locator('#title').fill('Second edit while saving')

  await expect.poll(() => state.draftSaveCount).toBe(2)
  expect(state.writes).toHaveLength(2)
  expect(state.writes[0]?.payload).toMatchObject({ title: 'First edit' })
  expect(state.writes[1]?.payload).toMatchObject({ title: 'Second edit while saving' })
  expect(state.draft).toMatchObject({ title: 'Second edit while saving' })
})
