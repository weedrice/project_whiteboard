import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

test('protected attachment downloads with the server-provided UTF-8 filename', async ({ page }) => {
  await installMockApi(page, {
    postContents: '<p><a href="/api/v1/files/31">보고서 다운로드</a></p>',
  })
  await login(page)
  await page.goto('/board/general/post/1')

  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('link', { name: '보고서 다운로드' }).click()
  const download = await downloadPromise

  expect(download.suggestedFilename()).toBe('보고서.pdf')
})
