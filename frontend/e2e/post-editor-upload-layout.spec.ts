import { expect, test, type Page } from '@playwright/test'
import { installMockApi, login, mockSettings } from './fixtures/mockApi'

const content = '<pre><code>// Code</code></pre><table><tbody><tr><td><p>Alpha</p></td><td><p>Beta</p></td></tr><tr><td><p>Gamma</p></td><td><p>Delta</p></td></tr></tbody></table><p>After</p>'
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7XkAAAAASUVORK5CYII=', 'base64')
const files = (start: number, count: number) => Array.from({ length: count }, (_, index) => ({
  name: `a-long-image-filename-for-layout-regression-${start + index}.png`,
  mimeType: 'image/png',
  buffer: png,
}))

async function expectUsableBody(page: Page) {
  const metrics = await page.evaluate(() => {
    const body = document.querySelector('.tiptap-content')!.getBoundingClientRect()
    const wrapper = document.querySelector('.tiptap-editor-wrapper')!.getBoundingClientRect()
    const toolbar = document.querySelector('.tiptap-toolbar')!.getBoundingClientRect()
    return {
      height: body.height,
      bottomOverflow: body.bottom - wrapper.bottom,
      overlap: toolbar.bottom - body.top,
      pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    }
  })
  expect(metrics.height).toBeGreaterThanOrEqual(159)
  expect(metrics.bottomOverflow).toBeLessThanOrEqual(1)
  expect(metrics.overlap).toBeLessThanOrEqual(1)
  expect(metrics.pageOverflow).toBeLessThanOrEqual(1)
}

for (const language of ['ko', 'en']) {
  for (const width of [390, 640]) {
    test(`upload failures keep the body and controls usable in ${language} at ${width}px`, async ({ page }, testInfo) => {
      const previousLanguage = mockSettings.language
      mockSettings.language = language
      let releaseUpload!: () => void
      const uploadGate = new Promise<void>((resolve) => { releaseUpload = resolve })
      let requests = 0
      try {
        await page.setViewportSize({ width, height: 844 })
        await installMockApi(page, { postContents: content })
        await page.route('**/api/v1/files/upload', async (route) => {
          requests++
          await uploadGate
          await route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'Upload failed' }) })
        })
        await login(page)
        await page.goto('/board/general/post/1/edit')
        await expect(page.getByRole('combobox', { name: language === 'en' ? 'Font size' : '글자 크기', exact: true })).toBeVisible()
        const editor = page.locator('.ProseMirror')
        const uploadInput = page.locator('.tiptap-editor-wrap input[type="file"]')
        const status = page.locator('.image-upload-status')
        const failedItems = page.locator('.image-upload-failed-item')
        await editor.getByText('Alpha', { exact: true }).click()
        await uploadInput.setInputFiles(files(1, 3))
        await expect(status).toHaveAttribute('role', 'status')
        await expectUsableBody(page)
        releaseUpload()
        await expect(failedItems).toHaveCount(3)
        await expectUsableBody(page)
        await uploadInput.setInputFiles(files(4, 5))
        await expect(failedItems).toHaveCount(8)
        await expectUsableBody(page)

        // Both contextual toolbar states must leave the editing surface usable.
        await editor.locator('pre').click()
        await expect(page.locator('.tiptap-table-tools')).toBeHidden()
        await expectUsableBody(page)
        await editor.getByText('Alpha', { exact: true }).click()
        await page.keyboard.press('End')
        await page.keyboard.type(' edited')
        await expect(editor.locator('td').first()).toHaveText('Alpha edited')
        await expectUsableBody(page)
        expect(await page.locator('.tiptap-toolbar').evaluate(el => el.scrollHeight > el.clientHeight)).toBe(true)

        // Keyboard focus scrolls to the last file's retry/dismiss controls.
        const requestsBeforeRetry = requests
        await failedItems.last().locator('button').first().focus()
        await page.keyboard.press('Enter')
        await expect.poll(() => requests).toBe(requestsBeforeRetry + 1)
        await expect(failedItems).toHaveCount(8)
        await expectUsableBody(page)
        await failedItems.last().locator('button').last().focus()
        await page.keyboard.press('Space')
        await expect(failedItems).toHaveCount(7)
        await expectUsableBody(page)
        if (language === 'en' && width === 640) {
          await page.screenshot({ path: testInfo.outputPath('upload-errors.png') })
        }
        await status.locator(':scope > button.image-upload-status-btn--icon').click()
        await expect(status).toHaveCount(0)
        await page.locator('[data-table-action="addRowAfter"]').click()
        await expect(editor.locator('tr')).toHaveCount(3)
        await expectUsableBody(page)
      } finally {
        releaseUpload()
        mockSettings.language = previousLanguage
      }
    })
  }
}
