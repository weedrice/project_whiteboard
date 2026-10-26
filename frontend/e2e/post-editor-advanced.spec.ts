import { expect, test, type Locator, type Page } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a7XkAAAAASUVORK5CYII=', 'base64')
async function paste(editor: Locator, html: string, text: string) {
  await editor.evaluate((element, value) => {
    const clipboardData = new DataTransfer()
    clipboardData.setData('text/html', value.html)
    clipboardData.setData('text/plain', value.text)
    element.dispatchEvent(new ClipboardEvent('paste', { clipboardData, bubbles: true, cancelable: true }))
  }, { html, text })
}

async function insertBlock(page: Page, label: string) {
  await page.getByRole('button', { name: '블록 삽입', exact: true }).click()
  await page.getByRole('menuitem', { name: label, exact: true }).click()
}

for (const width of [390, 1280]) {
  test(`paste modes, selection tools, document tools and blocks at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, { postContents: '<h2>First heading</h2><p>Alpha Alpha</p><p>Paste here</p>' })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    await editor.getByText('Paste here', { exact: true }).click()
    await page.keyboard.press('End')
    const pasteMode = page.getByRole('combobox', { name: '붙여넣기 방식', exact: true })
    await pasteMode.selectOption('clean')
    await paste(editor, '<section><p style="color:red"><strong>Imported</strong></p></section>', 'Imported')
    await expect(editor.locator('strong')).toHaveText('Imported')
    await expect(page.locator('.raw-html-block')).toHaveCount(0)
    await page.getByRole('button', { name: '실행 취소', exact: true }).click()
    await expect(editor).not.toContainText('Imported')
    await pasteMode.selectOption('text')
    await editor.getByText('Paste here', { exact: true }).click()
    await page.keyboard.press('End')
    await paste(editor, '<b>literal</b>', '<b>literal</b>')
    await expect(editor).toContainText('<b>literal</b>')

    await page.getByRole('button', { name: '찾기·바꾸기', exact: true }).click()
    await page.getByLabel('찾을 내용', { exact: true }).fill('Alpha')
    await page.getByLabel('찾을 내용', { exact: true }).dispatchEvent('keydown', { key: 'Escape', isComposing: true })
    await expect(page.getByLabel('찾을 내용', { exact: true })).toBeVisible()
    await page.getByLabel('바꿀 내용', { exact: true }).fill('Beta')
    await page.getByRole('button', { name: '모두 바꾸기', exact: true }).click()
    await expect(editor).toContainText('Beta Beta')
    await page.getByRole('button', { name: '실행 취소', exact: true }).click()
    await expect(editor).toContainText('Alpha Alpha')
    await page.getByRole('button', { name: '문서 개요', exact: true }).click()
    await page.getByRole('navigation', { name: '문서 개요' }).getByRole('button', { name: 'First heading' }).click()
    await expect(editor).toBeFocused()
    await page.getByRole('button', { name: '문서 개요', exact: true }).click()

    await editor.getByText('Alpha Alpha', { exact: true }).click()
    await page.keyboard.press('Home')
    await page.keyboard.press('Shift+End')
    const selectionTools = page.getByRole('group', { name: '선택 영역 도구', exact: true })
    await expect(selectionTools).toBeVisible()
    await selectionTools.locator('button').first().click()
    await expect(editor.locator('strong')).toHaveText('Alpha Alpha')
    await selectionTools.getByRole('button', { name: '링크', exact: true }).click()
    const linkDialog = page.getByRole('dialog', { name: '링크 설정', exact: true })
    await linkDialog.locator('#editor-link-url').fill('https://example.com/reference')
    await linkDialog.locator('#editor-link-url').press('Enter')
    await expect(editor.locator('a')).toHaveText('Alpha Alpha')
    await expect(editor.locator('a')).toHaveAttribute('href', 'https://example.com/reference')
    await selectionTools.getByRole('button', { name: '색상', exact: true }).click()
    await page.getByRole('dialog', { name: '글자 색상', exact: true }).getByRole('button', { name: '빨강', exact: true }).click()
    await expect(editor.locator('a span')).toHaveCSS('color', 'rgb(239, 68, 68)')
    await page.getByRole('button', { name: '집중 모드', exact: true }).click()
    await expect(page.locator('.post-editor-focus')).toBeVisible()
    await page.getByRole('button', { name: '집중 모드 종료', exact: true }).dispatchEvent('keydown', { key: 'Escape', isComposing: true })
    await expect(page.locator('.post-editor-focus')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.locator('.post-editor-focus')).toHaveCount(0)

    await editor.locator('p').last().click()
    await expect(page.getByRole('combobox', { name: '콘텐츠 블록', exact: true })).toHaveCount(0)
    await insertBlock(page, '주의 상자')
    await expect(editor.locator('aside[data-callout="warning"]')).toContainText('Paste here')
    await page.getByRole('button', { name: '블록 아래에 쓰기', exact: true }).click()
    await insertBlock(page, '체크리스트')
    await page.keyboard.type('Task item')
    await editor.getByRole('checkbox', { name: '완료 상태 변경' }).click()
    await expect(editor.getByRole('checkbox')).toHaveAttribute('aria-checked', 'true')
    await editor.getByText('Task item', { exact: true }).click()
    await insertBlock(page, '체크리스트')
    await expect(editor.getByRole('checkbox')).toHaveCount(0)
    await expect(editor).toContainText('Task item')
    await page.getByRole('button', { name: '실행 취소', exact: true }).click()
    await expect(editor.getByRole('checkbox')).toHaveAttribute('aria-checked', 'true')
    await expect(editor.locator('.post-editor-task-item')).toHaveCSS('display', 'flex')
    await expect(page.getByRole('button', { name: '블록 아래에 쓰기', exact: true })).toHaveCSS('white-space', 'nowrap')
    await editor.getByText('Task item', { exact: true }).click()
    await page.getByRole('button', { name: '블록 아래에 쓰기', exact: true }).click()
    await insertBlock(page, '접기 영역')
    await editor.locator('details [data-details-content] p').click()
    await page.keyboard.type('Hidden body')
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(1)
    await page.screenshot({ path: testInfo.outputPath('content-blocks.png') })
    await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
    await page.waitForURL(/\/board\/general\/post\/1\/?$/)
    const viewer = page.locator('.nv-rich-content')
    await expect(viewer.locator('li[data-checked="true"]')).toContainText('Task item')
    await expect(viewer.locator('details')).not.toHaveAttribute('open')
    await viewer.locator('summary').click()
    await expect(viewer.getByText('Hidden body', { exact: true })).toBeVisible()
    await page.goto('/board/general/post/1/edit')
    await expect(editor.getByRole('checkbox')).toHaveAttribute('aria-checked', 'true')
    await expect(editor.locator('aside')).toContainText('Paste here')
    await expect(editor.locator('details')).toContainText('Hidden body')
    await expect(page.locator('.raw-html-block')).toHaveCount(0)
  })

  test(`image replacement retains the original on failure and preserves descriptions at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, { postContents: '<p><img src="https://cdn.noviis.kr/old.png" width="240" alt="Original alt" data-caption="Original caption" class="tiptap-image-align-right"></p>' })
    await page.route('https://cdn.noviis.kr/**', route => route.fulfill({ contentType: 'image/png', body: png }))
    let fail = true
    await page.route('**/api/v1/files/99', route => route.fulfill({ contentType: 'image/png', body: png }))
    await page.route('**/api/v1/files/upload', route => route.fulfill({ status: fail ? 500 : 200, contentType: 'application/json', body: JSON.stringify(fail ? { success: false, message: 'Failed' } : { success: true, data: { fileId: 99, fileUrl: '/api/v1/files/99' } }) }))
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    const image = editor.locator('.post-editor-image-node > img')
    await image.click()
    const button = page.getByRole('button', { name: '이미지 교체', exact: true })
    await expect(button).toBeVisible()
    const upload = async () => {
      const chooser = page.waitForEvent('filechooser')
      await button.click()
      await (await chooser).setFiles({ name: 'replacement.png', mimeType: 'image/png', buffer: png })
    }
    await upload()
    await expect(button).toBeEnabled()
    await expect(image).toHaveAttribute('src', 'https://cdn.noviis.kr/old.png')
    fail = false
    await upload()
    await expect(image).toHaveAttribute('src', /^blob:/)
    await expect(image).toHaveAttribute('alt', 'Original alt')
    await expect(editor.locator('.post-editor-image-caption')).toHaveText('Original caption')
    await expect(editor.locator('.post-editor-image-node')).toHaveClass(/post-editor-image-node--right/)
    await page.getByRole('button', { name: '실행 취소', exact: true }).click()
    await expect(image).toHaveAttribute('src', 'https://cdn.noviis.kr/old.png')
    await page.getByRole('button', { name: '다시 실행', exact: true }).click()
    const saveRequest = page.waitForRequest(request => request.method() === 'PUT' && request.url().endsWith('/posts/1'))
    await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
    expect((await saveRequest).postDataJSON().contents).toContain('src="/api/v1/files/99"')
    await page.waitForURL(/\/board\/general\/post\/1\/?$/)
    await expect(page.locator('.nv-rich-content img')).toHaveAttribute('data-authenticated-file-src', '/files/99')
    await expect(page.locator('.nv-rich-content img')).toHaveAttribute('src', /^blob:/)
    await page.goto('/board/general/post/1/edit')
    await expect(image).toHaveAttribute('alt', 'Original alt')
    await expect(editor.locator('.post-editor-image-caption')).toHaveText('Original caption')
  })

  for (const source of ['/api/v1/files/99', 'https://cdn.noviis.kr/retry.png']) {
    test(`image retry restores editor selection and Delete for ${source} at ${width}px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 1000 })
      await installMockApi(page, {
        postContents: `<p><img src="${source}" width="240" alt="Retry image" data-caption="Original caption"></p><p>Keep this text</p>`,
      })
      let requests = 0
      let release!: () => void
      const gate = new Promise<void>(resolve => { release = resolve })
      await page.route(source.startsWith('/') ? `**${source}` : source, async route => {
        // Parsing the initial editor HTML can request the protected URL natively
        // before the node view downloads it through the authenticated API.
        if (source.startsWith('/') && route.request().resourceType() === 'image') {
          await route.fulfill({ status: 403, body: 'authentication required' })
          return
        }
        requests += 1
        if (requests === 1) {
          await route.fulfill({ status: 503, body: 'temporary failure' })
          return
        }
        await gate
        await route.fulfill({ contentType: 'image/png', body: png })
      })
      try {
        await login(page)
        await page.goto('/board/general/post/1/edit')
        const editor = page.locator('.ProseMirror')
        const image = editor.locator('.post-editor-image-node > img')
        await expect(image).toHaveAttribute('data-image-state', 'error')
        await page.getByRole('button', { name: '이미지 다시 불러오기', exact: true }).focus()
        await page.keyboard.press('Enter')
        const retry = page.getByRole('button', { name: '이미지를 다시 불러오는 중', exact: true })
        await expect(retry).toHaveAttribute('aria-disabled', 'true')
        await expect(retry).toBeFocused()
        await page.keyboard.press('Enter')
        await expect.poll(() => requests).toBe(2)
        release()
        await expect(image).toHaveAttribute('data-image-state', 'loaded')
        await expect(editor).toBeFocused()
        await expect(editor.locator('.post-editor-image-node')).toHaveClass(/ProseMirror-selectednode/)
        await expect(editor.locator('.post-editor-image-caption')).toHaveText('Original caption')
        await expect(page.getByRole('button', { name: '실행 취소', exact: true })).toBeDisabled()
        expect(requests).toBe(2)
        await page.keyboard.press('Delete')
        await expect(image).toHaveCount(0)
        await expect(editor).toContainText('Keep this text')
      } finally {
        release()
      }
    })
  }
}

for (const width of [390, 1280]) {
  test(`discards a replacement upload if its target image is deleted before completion at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, { postContents: '<p><img src="https://cdn.noviis.kr/old.png" width="240" alt="Original"></p><p>Keep this text</p>' })
    await page.route('https://cdn.noviis.kr/**', route => route.fulfill({ contentType: 'image/png', body: png }))
    let finishUpload!: () => void
    const uploadGate = new Promise<void>(resolve => { finishUpload = resolve })
    await page.route('**/api/v1/files/upload', async route => {
      await uploadGate
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data: { fileId: 99, fileUrl: '/api/v1/files/99' } }) })
    })
    const discarded: number[] = []
    await page.route('**/api/v1/files/uploads/discard', route => {
      discarded.push(...route.request().postDataJSON().fileIds)
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ success: true, data: { discardedCount: 1 } }) })
    })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    const image = editor.locator('.post-editor-image-node > img')
    await image.click()
    const chooser = page.waitForEvent('filechooser')
    await page.getByRole('button', { name: '이미지 교체', exact: true }).click()
    await (await chooser).setFiles({ name: 'replacement.png', mimeType: 'image/png', buffer: png })
    await expect(page.getByRole('button', { name: '교체 중…', exact: true })).toBeDisabled()
    await image.click()
    await expect(editor).toBeFocused()
    await page.keyboard.press('Delete')
    await expect(image).toHaveCount(0)
    finishUpload()
    await expect.poll(() => discarded).toContain(99)
    await expect(editor).toContainText('Keep this text')
    await expect(image).toHaveCount(0)
  })
}
