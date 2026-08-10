import { expect, test, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { buildSandboxedPostHtmlSource } from '../src/utils/postHtmlSandbox'

const heightBridgeSource = readFile(
  new URL('../public/sandbox-height-bridge.js', import.meta.url),
  'utf8',
)

function buildSandboxSource(html: string): string {
  return buildSandboxedPostHtmlSource(html, 'e2e-frame')
}

test.beforeEach(async ({ page }) => {
  await page.route('https://noviis.kr/sandbox-height-bridge.js*', async (route) => {
    await route.fulfill({
      contentType: 'application/javascript',
      body: await heightBridgeSource,
    })
  })
})

async function loadSandbox(page: Page, html: string, width: number, height: number) {
  const source = buildSandboxSource(html)
  await page.setContent(`<iframe id="sandbox" sandbox="allow-scripts" style="display:block;border:0;width:${width}px;height:${height}px"></iframe>`)
  await page.locator('#sandbox').evaluate((frame, srcdoc) => {
    ;(frame as HTMLIFrameElement).srcdoc = srcdoc
  }, source)

  const sandbox = page.frameLocator('#sandbox')
  await expect(sandbox.locator('body')).toBeVisible()
  return sandbox
}

test('author overflow styles cannot hide content beyond the iframe height', async ({ page }) => {
  const sandbox = await loadSandbox(page, `
    <style>html body { overflow: hidden !important; }</style>
    <main style="height:5000px">Long content</main>
  `, 800, 4000)

  const metrics = await sandbox.locator('html').evaluate((root) => ({
    rootOverflowY: getComputedStyle(root).overflowY,
    bodyOverflowY: getComputedStyle(document.body).overflowY,
    scrollHeight: root.scrollHeight,
    clientHeight: root.clientHeight,
  }))

  expect(metrics.rootOverflowY).toBe('auto')
  expect(metrics.bodyOverflowY).toBe('auto')
  expect(metrics.scrollHeight).toBeGreaterThan(metrics.clientHeight)
})

test('only a narrow grid with overflowing descendants is stacked', async ({ page }) => {
  const sandbox = await loadSandbox(page, `
    <style>
      body { margin: 0; padding: 24px; }
      .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
      .value { overflow: hidden; white-space: nowrap; font: 30px monospace; }
    </style>
    <div id="fits" class="grid"><div>A</div><div>B</div></div>
    <div id="overflows" class="grid"><div><div class="value">53,885.10</div></div><div><div class="value">7,710.03</div></div></div>
  `, 328, 1000)

  await expect(sandbox.locator('#overflows')).toHaveAttribute('data-noviis-responsive-stack', '')
  await expect(sandbox.locator('#fits')).not.toHaveAttribute('data-noviis-responsive-stack', '')

  const columns = await sandbox.locator('#overflows').evaluate((grid) => getComputedStyle(grid).gridTemplateColumns)
  expect(columns.split(' ')).toHaveLength(1)
})

test('allowlisted external font stylesheets can load inside the sandbox', async ({ page }) => {
  await page.route('https://fonts.googleapis.com/**', async (route) => {
    await route.fulfill({
      contentType: 'text/css',
      body: '.remote-font-style { color: rgb(1, 2, 3); }',
    })
  })

  const sandbox = await loadSandbox(page, `
    <style>@import url('https://fonts.googleapis.com/css2?family=Inter');</style>
    <p class="remote-font-style">Styled content</p>
  `, 800, 500)

  await expect(sandbox.locator('.remote-font-style')).toHaveCSS('color', 'rgb(1, 2, 3)')
})

test('iframe height follows shrinking content, including out-of-flow blocks', async ({ page }) => {
  const source = buildSandboxSource('<main id="resizable" style="position:absolute;inset:0 0 auto;height:1400px">Resizable content</main>')
  await page.setContent('<iframe id="sandbox" sandbox="allow-scripts" style="display:block;border:0;width:800px;height:240px"></iframe>')
  await page.evaluate((srcdoc) => {
    const frame = document.querySelector<HTMLIFrameElement>('#sandbox')
    if (!frame) throw new Error('sandbox frame not found')
    window.addEventListener('message', (event) => {
      const data = event.data
      if (
        event.source !== frame.contentWindow
        || data?.type !== 'noviis-post-html-height'
        || data?.channel !== 'noviis-post-html-sandbox'
        || data?.id !== 'e2e-frame'
      ) return
      frame.style.height = `${Math.max(240, Math.min(Math.ceil(Number(data.height)), 4000))}px`
    })
    frame.srcdoc = srcdoc
  }, source)

  const frameHeight = () => page.locator('#sandbox').evaluate((frame) => (
    Math.round(frame.getBoundingClientRect().height)
  ))
  await expect.poll(frameHeight).toBeGreaterThan(1300)

  const sandbox = page.frameLocator('#sandbox')
  await sandbox.locator('#resizable').evaluate((element) => {
    ;(element as HTMLElement).style.height = '120px'
  })

  await expect.poll(frameHeight).toBe(240)
})

test('height bridge runs when the embedding page enforces the production script CSP', async ({ page }) => {
  const source = buildSandboxSource('<main style="height:960px">CSP constrained content</main>')
  await page.route('https://noviis.kr/csp-height-shell', async (route) => {
    await route.fulfill({
      contentType: 'text/html',
      headers: {
        'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'",
      },
      body: '<iframe id="sandbox" sandbox="allow-scripts" style="display:block;border:0;width:800px;height:240px"></iframe>',
    })
  })
  await page.goto('https://noviis.kr/csp-height-shell')
  await page.evaluate((srcdoc) => {
    const frame = document.querySelector<HTMLIFrameElement>('#sandbox')
    if (!frame) throw new Error('sandbox frame not found')
    window.addEventListener('message', (event) => {
      const data = event.data
      if (event.source !== frame.contentWindow || data?.id !== 'e2e-frame') return
      frame.style.height = `${Math.max(240, Math.min(Math.ceil(Number(data.height)), 4000))}px`
    })
    frame.srcdoc = srcdoc
  }, source)

  await expect.poll(() => page.locator('#sandbox').evaluate((frame) => (
    Math.round(frame.getBoundingClientRect().height)
  ))).toBe(960)
})
