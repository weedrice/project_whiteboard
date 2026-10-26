import { expect, test } from '@playwright/test'
import { installMockApi, mockUser } from './fixtures/mockApi'

for (const initiallyFree of [true, false]) {
  test(`profile hydration failure preserves ${initiallyFree ? 'consumed free change' : 'zero paid balance'} without another write`, async ({ page }) => {
    await installMockApi(page, { authenticated: true })
    const emptyPage = {
      content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true,
    }
    for (const endpoint of ['posts', 'comments']) {
      await page.route(`**/api/v1/users/me/${endpoint}?**`, route => route.fulfill({
        contentType: 'application/json', body: JSON.stringify({ success: true, data: emptyPage }),
      }))
    }
    await page.route('**/api/v1/users/me/agents', route => route.fulfill({
      contentType: 'application/json', body: JSON.stringify({ success: true, data: { agents: [] } }),
    }))
    let uploads = 0
    let writes = 0
    let failedHydrations = 0
    const user = {
      ...mockUser,
      profileImageUrl: null as string | null,
      points: initiallyFree ? 6000 : 1000,
      profileImageChangeCost: 1000,
      profileImageChangeFreeAvailable: initiallyFree,
    }
    await page.route('**/api/v1/files/upload', route => {
      uploads++
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ success: true, data: { fileId: 500 + uploads } }),
      })
    })
    await page.route('**/api/v1/users/me', route => {
      if (route.request().method() === 'PUT') {
        writes++
        expect(route.request().postDataJSON()).toEqual({ displayName: 'Saved Profile', profileImageId: 501 })
        user.displayName = 'Saved Profile'
        user.profileImageUrl = '/api/v1/files/501/download'
        user.profileImageChangeFreeAvailable = false
        if (!initiallyFree) user.points = 0
        return route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({ success: true, data: {
            userId: user.userId, displayName: user.displayName, profileImageUrl: user.profileImageUrl,
            spentPoints: initiallyFree ? null : 1000, remainingPoints: initiallyFree ? null : 0,
          } }),
        })
      }
      if (writes > 0 && failedHydrations === 0) {
        failedHydrations++
        return route.fulfill({
          status: 503, contentType: 'application/json',
          body: JSON.stringify({ success: false, error: { code: 'INTERNAL_SERVER_ERROR', message: 'Temporary read failure' } }),
        })
      }
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data: user }) })
    })

    await page.goto('/mypage')
    await page.getByRole('button', { name: '프로필 수정', exact: true }).click()
    const editor = page.getByRole('dialog', { name: '프로필 수정', exact: true })
    const selectPhoto = async () => {
      await editor.locator('#profile-photo-input').setInputFiles({
        name: 'profile.png', mimeType: 'image/png',
        buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64'),
      })
      await expect(editor.locator('img[src^="blob:"]')).toBeVisible()
    }
    await selectPhoto()
    await editor.locator('#profile-display-name').fill('Saved Profile')
    await editor.locator('#profile-display-name').press('Enter')
    const confirmation = page.getByRole('dialog').filter({ hasText: '프로필 이미지 변경 시 1000P가 차감됩니다. 계속할까요?' })
    if (!initiallyFree) {
      await expect(confirmation).toBeVisible()
      await confirmation.getByRole('button', { name: '예', exact: true }).click()
    }
    await expect(editor).toBeHidden()
    expect(failedHydrations).toBe(1)

    await page.getByRole('button', { name: '프로필 수정', exact: true }).click()
    await expect(editor.locator('#profile-display-name')).toHaveValue('Saved Profile')
    await expect(editor.getByText('프로필 이미지 최초 변경은 무료입니다.')).toBeHidden()
    await selectPhoto()
    await editor.locator('#profile-display-name').press('Enter')
    if (initiallyFree) {
      await expect(confirmation).toBeVisible()
      await confirmation.getByRole('button', { name: '아니오', exact: true }).click()
      await expect(confirmation).toBeHidden()
    } else {
      await expect(editor.getByText('포인트가 부족합니다. 보유 0P / 필요 1000P')).toBeVisible()
      await expect(confirmation).toBeHidden()
    }
    expect(uploads).toBe(1)
    expect(writes).toBe(1)
  })
}
