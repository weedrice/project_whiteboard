import { expect, test } from '@playwright/test'
import { installMockApi } from './fixtures/mockApi'

test('original read failure stays blocked and a later edit preserves the original body', async ({ page }) => {
  const state = await installMockApi(page, { authenticated: true, postContents: '<p>Keep the original body</p>' })
  let failDetail = true
  await page.route(/\/api\/v1\/posts\/1(?:\?.*)?$/, async route => {
    if (route.request().method() === 'GET' && failDetail) {
      await route.fulfill({
        status: 503, contentType: 'application/json',
        body: JSON.stringify({ success: false, error: { code: 'INTERNAL_SERVER_ERROR', message: 'Temporarily unavailable' } }),
      })
    } else await route.fallback()
  })
  await page.goto('/board/general/post/1/edit')
  const composer = page.locator('.nv-compose-page')
  // The route author guard rejects the initial failure before mounting the form.
  // Component-level failures and retry are covered by PostFormLoading.spec.ts.
  await expect(page).toHaveURL(/\/error\?status=500/)
  await expect(page.locator('#title')).toHaveCount(0)
  await page.keyboard.press('Control+Enter')
  expect(state.writes).toHaveLength(0)
  expect(state.draftSaveCount).toBe(0)
  expect(state.draftRecoveryCount).toBe(0)

  failDetail = false
  await page.goto('/board/general/post/1/edit')
  await expect(page.locator('#title')).toHaveValue('Accessible post')
  await expect(page.locator('.ProseMirror')).toContainText('Keep the original body')
  await expect(composer.locator('.nv-compose-header button').last()).toBeEnabled()
  await page.locator('#title').fill('Safe edit after retry')
  await composer.locator('.nv-compose-header button').last().click()
  await expect.poll(() => state.writes.filter(write => write.method === 'PUT').length).toBe(1)
  expect(state.writes.find(write => write.method === 'PUT')?.payload).toMatchObject({
    title: 'Safe edit after retry', contents: '<p>Keep the original body</p>',
  })
})
