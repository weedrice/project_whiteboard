import { expect, test } from '@playwright/test'
import { installMockApi, login, mockSettings } from './fixtures/mockApi'

for (const width of [390, 640, 768]) {
  test(`English table tools leave usable editing space at ${width}px`, async ({ page }) => {
    const previousLanguage = mockSettings.language
    mockSettings.language = 'en'
    try {
      await page.setViewportSize({ width, height: 844 })
      await installMockApi(page, {
        postContents: '<p>Before</p><table><tbody><tr><td><p>Alpha</p></td><td><p>Beta</p></td></tr><tr><td><p>Gamma</p></td><td><p>Delta</p></td></tr></tbody></table><p>After</p>',
      })
      await login(page)
      await page.goto('/board/general/post/1/edit')
      await expect(page.getByRole('combobox', { name: 'Font size', exact: true })).toBeVisible()
      const editor = page.locator('.ProseMirror')
      const content = page.locator('.tiptap-content')
      const tools = page.locator('.tiptap-table-tools')
      await editor.getByText('Before', { exact: true }).click()
      await expect(tools).toBeHidden()
      const inactiveHeight = (await content.boundingBox())!.height
      expect(inactiveHeight).toBeGreaterThanOrEqual(120)

      await editor.getByText('Alpha', { exact: true }).click()
      await expect(tools).toBeVisible()
      expect(Math.abs((await content.boundingBox())!.height - inactiveHeight)).toBeLessThanOrEqual(1)
      await page.keyboard.press('End')
      await page.keyboard.type(' edited')
      await expect(editor.locator('td').first()).toHaveText('Alpha edited')

      // Actions at either end remain reachable through the row's horizontal scroll.
      await tools.locator('[data-table-action="toggleHeaderColumn"]').click()
      await expect(editor.locator('th')).toHaveCount(2)
      await tools.locator('[data-table-action="addRowBefore"]').click()
      await expect(editor.locator('tr')).toHaveCount(3)
      expect((await content.boundingBox())!.height).toBeGreaterThanOrEqual(120)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(overflow).toBeLessThanOrEqual(1)
      await tools.locator('[data-table-action="deleteTable"]').click()
      await expect(editor.locator('table')).toHaveCount(0)
      await expect(tools).toHaveCount(0)
      await expect(editor).toContainText('Before')
      await expect(editor).toContainText('After')
    } finally {
      mockSettings.language = previousLanguage
    }
  })
}

for (const width of [390, 640, 768, 1024, 1280]) {
  test(`dragging from code into a table preserves cell coordinates at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await installMockApi(page, {
      postContents: '<pre><code class="language-typescript">// Before</code></pre><table><tbody><tr><td><p>Alpha</p></td><td><p>Beta</p></td></tr><tr><td><p>Gamma</p></td><td><p>Delta</p></td></tr></tbody></table><p>After</p>',
    })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    const toolbar = page.locator('.tiptap-toolbar')
    const codeExit = page.getByRole('button', { name: '코드 아래에 본문 작성', exact: true })
    await editor.locator('pre').click()
    await expect(codeExit).toBeVisible()
    await editor.locator('td').first().scrollIntoViewIfNeeded()
    const first = (await editor.locator('td').first().boundingBox())!
    const second = (await editor.locator('td').nth(1).boundingBox())!
    const initialToolbarHeight = (await toolbar.boundingBox())!.height

    await page.mouse.move(first.x + 15, first.y + first.height / 2)
    await page.mouse.down()
    try {
      await expect(page.getByRole('group', { name: '표 편집', exact: true })).toBeVisible()
      await expect(codeExit).toBeHidden()
      const afterActivation = (await editor.locator('td').first().boundingBox())!
      expect(Math.abs(afterActivation.y - first.y)).toBeLessThanOrEqual(1)
      expect(Math.abs((await toolbar.boundingBox())!.height - initialToolbarHeight)).toBeLessThanOrEqual(1)
      await page.mouse.move(second.x + 30, second.y + second.height / 2, { steps: 20 })
    } finally {
      await page.mouse.up()
    }
    await expect(editor.locator('.selectedCell')).toHaveCount(2)
    await page.getByRole('button', { name: '셀 병합', exact: true }).click()
    await expect(editor.locator('td[colspan="2"]')).toContainText('Alpha')
    await expect(editor.locator('td[colspan="2"]')).toContainText('Beta')
    await page.getByRole('button', { name: '셀 분할', exact: true }).click()
    await expect(editor.locator('td')).toHaveCount(4)

    await editor.locator('pre').click()
    await expect(codeExit).toBeVisible()
    expect(Math.abs((await toolbar.boundingBox())!.height - initialToolbarHeight)).toBeLessThanOrEqual(1)
    // The controls remain reachable by scrolling the row, including at tablet widths.
    await codeExit.click()
    await page.keyboard.type('After code')
    await expect(editor.locator('pre + p')).toHaveText('After code')
    const metrics = await page.evaluate(() => ({
      width: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }))
    expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.width + 1)
  })
}

for (const mobile of [false, true]) {
  test(`selecting an inactive table does not move its cells on ${mobile ? 'mobile width' : 'desktop'}`, async ({ page }) => {
    if (mobile) await page.setViewportSize({ width: 390, height: 844 })
    await installMockApi(page, {
      postContents: '<p>Before</p><table><tbody><tr><td><p>Alpha</p></td><td><p>Beta</p></td></tr><tr><td><p>Gamma</p></td><td><p>Delta</p></td></tr></tbody></table><p>After</p>',
    })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    const tools = page.locator('.tiptap-table-tools')
    await editor.getByText('Before', { exact: true }).click()
    await expect(tools).toBeHidden()
    await editor.locator('td').first().scrollIntoViewIfNeeded()
    const first = (await editor.locator('td').nth(0).boundingBox())!
    const second = (await editor.locator('td').nth(1).boundingBox())!

    await page.mouse.move(first.x + 15, first.y + first.height / 2)
    await page.mouse.down()
    try {
      await expect(tools).toBeVisible()
      const afterActivation = (await editor.locator('td').first().boundingBox())!
      expect(Math.abs(afterActivation.y - first.y)).toBeLessThanOrEqual(1)
      await page.mouse.move(second.x + 30, second.y + second.height / 2, { steps: 20 })
    } finally {
      await page.mouse.up()
    }
    await expect(editor.locator('.selectedCell')).toHaveCount(2)
    await tools.getByRole('button', { name: '셀 병합', exact: true }).click()
    await expect(editor.locator('td[colspan="2"]')).toContainText('Alpha')
    await expect(editor.locator('td[colspan="2"]')).toContainText('Beta')
    await tools.getByRole('button', { name: '셀 분할', exact: true }).click()
    await expect(editor.locator('td')).toHaveCount(4)
  })

  test(`table and code tools preserve edited content on ${mobile ? 'mobile' : 'desktop'}`, async ({ page }) => {
    if (mobile) {
      await page.setViewportSize({ width: 390, height: 844 })
      await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
    }
    const state = await installMockApi(page, {
      postContents: '<table><tbody><tr><td><p>First</p></td><td><p>Second</p></td></tr><tr><td><p>Third</p></td><td><p>Fourth</p></td></tr></tbody></table><p>After table</p>',
    })
    await login(page)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    await editor.getByText('First', { exact: true }).click()
    const tools = page.getByRole('group', { name: '표 편집', exact: true })
    await expect(tools).toBeVisible()
    await expect(tools.getByRole('button', { name: '셀 병합', exact: true })).toBeDisabled()
    await tools.getByRole('button', { name: '아래에 행 추가', exact: true }).click()
    await expect(editor.locator('tr')).toHaveCount(3)
    await tools.getByRole('button', { name: '오른쪽에 열 추가', exact: true }).click()
    await expect(editor.locator('tr').first().locator('td')).toHaveCount(3)
    await tools.getByRole('button', { name: '헤더 행 전환', exact: true }).click()
    await expect(editor.locator('th')).toHaveCount(3)

    const metrics = await page.evaluate(() => ({
      width: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
    }))
    expect(metrics.scrollWidth).toBeLessThanOrEqual(metrics.width + 1)

    await editor.getByText('After table', { exact: true }).click()
    await expect(tools).toBeHidden()
    await page.keyboard.press('End')
    await page.keyboard.press('Enter')
    await page.getByRole('button', { name: '코드 블록', exact: true }).click()
    await page.getByRole('combobox', { name: '코드 언어', exact: true }).selectOption('typescript')
    await page.keyboard.type('// comment')
    await expect(page.locator('#editor-slash-dialog')).toBeHidden()
    await expect(editor.locator('pre code')).toHaveText('// comment')
    await page.getByRole('button', { name: '코드 아래에 본문 작성', exact: true }).click()
    await page.keyboard.type('After code')
    await expect(editor.locator('pre + p')).toHaveText('After code')

    await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
    await page.waitForURL(/\/board\/general\/post\/1\/?$/)
    const update = state.writes.findLast((write) => write.method === 'PUT' && write.url === '/posts/1')
    const saved = String((update?.payload as { contents?: string }).contents ?? '')
    expect(saved).toContain('language-typescript')
    expect(saved).toContain('After code')
    expect(saved).toContain('First')
    expect(saved).toContain('Fourth')
    await page.goto('/board/general/post/1/edit')
    await expect(editor.locator('tr')).toHaveCount(3)
    await expect(editor.locator('th')).toHaveCount(3)
    await expect(editor.locator('pre code')).toHaveText('// comment')
  })
}
