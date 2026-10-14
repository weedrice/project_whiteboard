import { expect, test, type Locator } from '@playwright/test'
import { installMockApi, login } from './fixtures/mockApi'

const presets = ['#fef3c7', '#dbeafe', '#dcfce7', '#fce7f3', '#e5e7eb']

async function expectReadableCells(content: Locator) {
  await expect(content.locator('th, td')).toHaveCount(12)
  const ratios = await content.locator('th, td').evaluateAll(cells => {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 1
    const context = canvas.getContext('2d')!
    function luminance(color: string) {
      context.fillStyle = color
      context.fillRect(0, 0, 1, 1)
      const channels = Array.from(context.getImageData(0, 0, 1, 1).data).slice(0, 3).map(value => {
        const channel = value / 255
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
      })
      return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
    }
    return cells.slice(0, 10).map(cell => {
      const foreground = luminance(getComputedStyle(cell.querySelector('strong') ?? cell.querySelector('p')!).color)
      const background = luminance(getComputedStyle(cell).backgroundColor)
      return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05)
    })
  })
  expect(ratios).toHaveLength(10)
  for (const ratio of ratios) expect(ratio).toBeGreaterThanOrEqual(4.5)
  await expect(content.getByText('Custom text', { exact: true })).toHaveCSS('color', 'rgb(255, 0, 0)')
  await expect(content.locator('tr').nth(5).locator('td')).toHaveCSS('background-color', 'rgb(18, 52, 86)')
  await expect(content.locator('tr').last().locator('td')).toHaveCSS('background-color', /^rgba\(254, 243, 199, 0\.5(?:02)?\)$/)
}

for (const width of [390, 1280]) {
  test(`table presets stay readable in both themes through save and reload at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    const rows = presets.map((color, index) => `<tr><th style="background-color:${color}"><p><strong>Header ${index}</strong></p></th><td style="background-color:${color}"><p>Cell ${index}</p></td></tr>`).join('')
    await installMockApi(page, { postContents: `<table><tbody>${rows}<tr><td colspan="2" style="background-color:#123456"><p><span style="color:red">Custom text</span></p></td></tr><tr><td colspan="2" style="background-color:#fef3c780"><p>Translucent</p></td></tr></tbody></table>` })
    await login(page)
    await page.goto('/board/general/post/1')
    const viewer = page.locator('.nv-rich-content').filter({ hasText: 'Cell 0' })
    await expect(viewer.locator('td')).toHaveCount(7)
    await page.evaluate(() => document.documentElement.classList.add('dark'))
    await expectReadableCells(viewer)
    await page.goto('/board/general/post/1/edit')
    const editor = page.locator('.ProseMirror')
    for (const dark of [false, true]) {
      await page.evaluate(value => document.documentElement.classList.toggle('dark', value), dark)
      await expectReadableCells(editor)
    }
    await editor.getByText('Cell 0', { exact: true }).click()
    const colors = page.getByRole('combobox', { name: '셀 배경색', exact: true })
    await expect(colors).toHaveValue(presets[0])
    await colors.selectOption('')
    await expect(editor.locator('tr').first().locator('td')).not.toHaveAttribute('style', /background-color/)
    await colors.selectOption(presets[0])
    await expectReadableCells(editor)
    await page.getByRole('button', { name: '수정 완료', exact: true }).first().click()
    await page.waitForURL(/\/board\/general\/post\/1\/?$/)
    for (const dark of [true, false]) {
      await page.evaluate(value => document.documentElement.classList.toggle('dark', value), dark)
      await expectReadableCells(viewer)
    }
    await page.goto('/board/general/post/1/edit')
    await page.evaluate(() => document.documentElement.classList.add('dark'))
    await expectReadableCells(editor)
    await editor.getByText('Cell 0', { exact: true }).click()
    await expect(colors).toHaveValue(presets[0])
    await expect(page.locator('.raw-html-block')).toHaveCount(0)
  })
}
