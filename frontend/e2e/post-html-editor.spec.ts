import { expect, test } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'
import {
  encodeSandboxedPostHtml,
  expandSandboxedPostHtml,
} from '../src/utils/postHtmlSandbox'

const rawHtml = [
  '<!doctype html><html lang="ko"><head>',
  '<style>',
  'body{margin:0;padding:32px;font-family:sans-serif}',
  '.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}',
  '.card{min-height:150px;padding:18px;border:1px solid #ddd;border-radius:12px}',
  '</style></head><body>',
  '<main class="grid">',
  ...Array.from({ length: 10 }, (_, index) => `<section class="card">카드 ${index + 1}</section>`),
  '</main><script>window.authorScriptExecuted=true</script>',
  '</body></html>',
].join('')

test('preserved HTML remains isolated while surrounding content can be edited, saved, and reopened', async ({ page }) => {
  const state = await installMockApi(page, {
    postContents: encodeSandboxedPostHtml(rawHtml),
  })
  await login(page)
  await page.goto('/board/general/post/1/edit')

  const editor = page.locator('.ProseMirror')
  const htmlBlock = page.locator('.raw-html-block')
  await expect(page.locator('.tiptap-toolbar')).toBeVisible()
  await expect(editor).toHaveAttribute('contenteditable', 'true')
  await expect(htmlBlock).toBeVisible()

  const previewFrame = htmlBlock.locator('iframe')
  await expect.poll(async () => Math.round((await previewFrame.boundingBox())?.height ?? 0)).toBeGreaterThan(700)
  await expect(previewFrame.contentFrame().locator('.card')).toHaveCount(10)
  expect(await previewFrame.contentFrame().locator('body').evaluate(() => (
    (window as typeof window & { authorScriptExecuted?: boolean }).authorScriptExecuted
  ))).toBeUndefined()

  await htmlBlock.locator('.raw-html-block__header').click()
  await expect(htmlBlock).toHaveClass(/ProseMirror-selectednode/)
  await expect(page.locator('.tiptap-toolbar-context')).toBeVisible()
  await page.keyboard.press('ArrowLeft')
  await page.keyboard.type('앞쪽 일반 본문')

  await htmlBlock.locator('.raw-html-block__header').click()
  await page.keyboard.press('ArrowRight')
  await page.keyboard.type('뒤쪽 일반 본문')
  await expect(editor).toContainText('앞쪽 일반 본문')
  await expect(editor).toContainText('뒤쪽 일반 본문')

  await page.getByRole('button', { name: 'HTML', exact: true }).click()
  const sourceEditor = page.locator('#content')
  await expect(sourceEditor).toHaveValue(/<!--noviis-preserved-html-block:start-->/)
  await expect(sourceEditor).toHaveValue(/window\.authorScriptExecuted=true/)
  await expect(sourceEditor).toHaveValue(/앞쪽 일반 본문/)
  await expect(sourceEditor).toHaveValue(/뒤쪽 일반 본문/)

  await page.getByRole('button', { name: '에디터 보기', exact: true }).click()
  await expect(page.locator('.raw-html-block')).toHaveCount(1)
  await expect(editor).toContainText('앞쪽 일반 본문')
  await expect(editor).toContainText('뒤쪽 일반 본문')

  await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
  await page.waitForURL(/\/board\/general\/post\/1\/?$/)

  const update = state.writes.findLast((write) => write.method === 'PUT' && write.url === '/posts/1')
  expect(update).toBeDefined()
  const savedContents = String((update?.payload as { contents?: string }).contents ?? '')
  expect(savedContents).toContain('noviis-sandboxed-post-html')
  expect(savedContents).not.toContain('noviis-preserved-html-block:start')
  expect(expandSandboxedPostHtml(savedContents)).toContain(rawHtml)
  expect(expandSandboxedPostHtml(savedContents)).toContain('앞쪽 일반 본문')
  expect(expandSandboxedPostHtml(savedContents)).toContain('뒤쪽 일반 본문')

  await page.goto('/board/general/post/1/edit')
  await expect(page.locator('.raw-html-block')).toHaveCount(1)
  await expect(page.locator('.ProseMirror')).toContainText('앞쪽 일반 본문')
  await expect(page.locator('.ProseMirror')).toContainText('뒤쪽 일반 본문')
})

test('preserved HTML editing remains keyboard-usable without page overflow on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await installMockApi(page, {
    postContents: encodeSandboxedPostHtml(rawHtml),
  })
  await login(page)
  await page.goto('/board/general/post/1/edit')

  const htmlBlock = page.locator('.raw-html-block')
  const toolbarRows = page.locator('.tiptap-toolbar-row--scrollable')
  await expect(htmlBlock).toBeVisible()
  await expect(toolbarRows).toHaveCount(2)
  await expect(htmlBlock.locator('iframe')).toHaveAttribute('loading', 'lazy')
  await expect(htmlBlock.locator('iframe').contentFrame().locator('body')).toHaveCSS('color', 'rgb(249, 250, 251)')

  const viewportMetrics = await page.evaluate(() => ({
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }))
  expect(viewportMetrics.scrollWidth).toBeLessThanOrEqual(viewportMetrics.clientWidth + 1)
  expect(await toolbarRows.first().evaluate((row) => row.scrollWidth >= row.clientWidth)).toBe(true)

  await htmlBlock.locator('.raw-html-block__header').click()
  await expect(page.locator('.tiptap-toolbar-context')).toBeVisible()
  await page.keyboard.press('ArrowRight')
  await page.keyboard.type('모바일 키보드 본문')
  await expect(page.locator('.ProseMirror')).toContainText('모바일 키보드 본문')
})
