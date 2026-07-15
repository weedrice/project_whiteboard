import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

test('post create and update requests preserve their payloads', async ({ page }) => {
  const state = await installMockApi(page)
  await login(page)

  await page.goto('/board/general/write')
  await page.locator('#title').fill('Created title')
  await page.locator('.ProseMirror').fill('Created body')
  await page.getByRole('button', { name: /등록|Submit/ }).first().click()
  await expect.poll(() => state.writes.find((write) => write.url === '/boards/general/posts')).toBeTruthy()
  expect(state.writes.find((write) => write.url === '/boards/general/posts')?.payload).toMatchObject({
    title: 'Created title',
    contents: expect.stringContaining('Created body'),
  })

  await page.goto('/board/general/post/1/edit')
  await page.locator('#title').fill('Updated title')
  await page.getByRole('button', { name: /수정|Update/ }).first().click()
  await expect.poll(() => state.writes.find((write) => write.url === '/posts/1')).toBeTruthy()
  expect(state.writes.find((write) => write.url === '/posts/1')?.payload).toMatchObject({ title: 'Updated title' })
})

test('English messages load only when the user saves English', async ({ page }) => {
  const state = await installMockApi(page)
  const englishChunkRequests: string[] = []
  page.on('request', (request) => {
    if (/\/js\/en-[^/]+\.js$/.test(new URL(request.url()).pathname)) englishChunkRequests.push(request.url())
  })
  await login(page)
  expect(englishChunkRequests).toHaveLength(0)

  await page.goto('/mypage/settings')
  await page.locator('select').nth(1).selectOption('en')
  await page.getByRole('button', { name: /설정 저장|Save settings/ }).click()

  await expect(page.locator('html')).toHaveAttribute('lang', 'en')
  expect(englishChunkRequests).toHaveLength(1)
  expect(state.writes.find((write) => write.url === '/users/me/settings')?.payload)
    .toMatchObject({ language: 'en' })
})
