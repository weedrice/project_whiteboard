import { expect, test, type Locator, type Page, type Route } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

function fulfillJson(route: Route, data: unknown) {
  return route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data, error: null }),
  })
}

async function installProfileApi(page: Page, freeImageChange: boolean) {
  await installMockApi(page, { authenticated: true })
  const saveRequest = Promise.withResolvers<Route>()
  const state = {
    user: {
      ...mockUser,
      points: 6000,
      profileImageChangeCost: 1000,
      profileImageChangeFreeAvailable: freeImageChange,
    },
    uploads: 0,
    writes: [] as Array<{ displayName: string, profileImageId: number }>,
    pointReads: 0,
  }
  const emptyPage = {
    content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
  }
  await page.route('**/api/v1/users/me/posts?**', route => fulfillJson(route, emptyPage))
  await page.route('**/api/v1/users/me/comments?**', route => fulfillJson(route, emptyPage))
  await page.route('**/api/v1/users/me/agents', route => fulfillJson(route, { agents: [] }))
  await page.route('**/api/v1/points/me', (route) => {
    state.pointReads += 1
    return fulfillJson(route, { currentPoint: state.user.points })
  })
  await page.route('**/api/v1/files/upload', (route) => {
    state.uploads += 1
    return fulfillJson(route, { fileId: 500 + state.uploads })
  })
  await page.route('**/api/v1/users/me', (route) => {
    if (route.request().method() === 'PUT') {
      state.writes.push(route.request().postDataJSON())
      saveRequest.resolve(route)
      return
    }
    return fulfillJson(route, state.user)
  })
  return { state, saveRequest }
}

async function selectImage(dialog: Locator) {
  await dialog.locator('#profile-photo-input').setInputFiles({
    name: 'profile.png',
    mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'),
  })
  await expect(dialog.locator('img[src^="blob:"]')).toBeVisible()
}

test('closing a pending profile save still refreshes the header and next image cost confirmation', async ({ page }) => {
  const { state, saveRequest } = await installProfileApi(page, true)
  await page.goto('/mypage')
  await page.getByRole('button', { name: '프로필 수정', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '프로필 수정', exact: true })
  await selectImage(dialog)
  await dialog.locator('#profile-display-name').fill('Saved After Closing')
  await dialog.locator('#profile-display-name').press('Enter')
  const pendingSave = await saveRequest.promise
  await dialog.getByRole('button', { name: '닫기', exact: true }).click()
  await expect(dialog).toBeHidden()

  state.user.displayName = 'Saved After Closing'
  state.user.profileImageChangeFreeAvailable = false
  await fulfillJson(pendingSave, { ...state.user, spentPoints: 0 })
  await expect(page.locator('#user-dropdown-trigger')).toContainText('Saved After Closing')
  await expect(dialog).toBeHidden()

  await page.getByRole('button', { name: '프로필 수정', exact: true }).click()
  await expect(dialog.locator('#profile-display-name')).toHaveValue('Saved After Closing')
  await expect(dialog.getByText('프로필 이미지 최초 변경은 무료입니다.')).toBeHidden()
  await selectImage(dialog)
  await dialog.locator('#profile-display-name').press('Enter')
  const confirmation = page.getByRole('dialog').filter({ hasText: '프로필 이미지 변경 시 1000P가 차감됩니다. 계속할까요?' })
  await expect(confirmation).toBeVisible()
  await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
  await expect(confirmation).toBeHidden()
  expect(state.uploads).toBe(1)
  expect(state.writes).toEqual([{ displayName: 'Saved After Closing', profileImageId: 501 }])
})

test('a paid profile image save refreshes a warm point balance when the menu reopens', async ({ page }) => {
  const { state, saveRequest } = await installProfileApi(page, false)
  await page.goto('/mypage')
  await expect(page.getByRole('heading', { name: '마이페이지', exact: true })).toBeVisible()
  const trigger = page.locator('#user-dropdown-trigger')
  await trigger.click()
  await expect(page.locator('#user-dropdown-menu')).toContainText('6,000 P')
  expect(state.pointReads).toBe(1)
  await trigger.click()
  await page.getByRole('button', { name: '프로필 수정', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '프로필 수정', exact: true })
  await selectImage(dialog)
  await dialog.locator('#profile-display-name').press('Enter')
  const confirmation = page.getByRole('dialog').filter({ hasText: '프로필 이미지 변경 시 1000P가 차감됩니다. 계속할까요?' })
  await expect(confirmation).toBeVisible()
  await confirmation.getByRole('button', { name: '예', exact: true }).click()
  const pendingSave = await saveRequest.promise
  state.user.points = 5000
  await fulfillJson(pendingSave, { ...state.user, spentPoints: 1000 })
  await expect(dialog).toBeHidden()

  await trigger.click()
  await expect(page.locator('#user-dropdown-menu')).toContainText('5,000 P')
  expect(state.pointReads).toBe(2)
  expect(state.uploads).toBe(1)
  expect(state.writes).toHaveLength(1)
})
