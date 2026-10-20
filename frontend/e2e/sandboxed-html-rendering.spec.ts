import { expect, test, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { buildSync } from 'esbuild'
import { fileURLToPath } from 'node:url'

const heightBridgeSource = readFile(
  new URL('../public/sandbox-height-bridge.js', import.meta.url),
  'utf8',
)

const sandboxBuilder = buildSync({
  absWorkingDir: fileURLToPath(new URL('..', import.meta.url)),
  entryPoints: ['./src/utils/postHtmlSandbox.ts'],
  tsconfig: './tsconfig.json',
  bundle: true,
  write: false,
  format: 'iife',
  globalName: 'sandboxBuilder',
}).outputFiles[0].text

async function buildSandboxSource(page: Page, html: string): Promise<string> {
  // Use the actual browser DOMParser so first-paint normalization is exercised.
  return page.evaluate(({ code, content }) => {
    const build = new Function(`${code}; return sandboxBuilder.buildSandboxedPostHtmlSource;`)()
    return build(content, 'e2e-frame', '이미지를 불러올 수 없습니다')
  }, { code: sandboxBuilder, content: html })
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
  const source = await buildSandboxSource(page, html)
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
  const source = await buildSandboxSource(page, '<main id="resizable" style="position:absolute;inset:0 0 auto;height:1400px">Resizable content</main>')
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
  const source = await buildSandboxSource(page, '<main style="height:960px">CSP constrained content</main>')
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

test('images show a placeholder during download, then reveal the image and settle failures', async ({ page }) => {
  let releaseImage!: () => void
  const responseReady = new Promise<void>((resolve) => { releaseImage = resolve })
  await page.route('https://cdn.noviis.kr/delayed-image.svg', async (route) => {
    await responseReady
    await route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480"><rect width="640" height="480" fill="red"/></svg>',
    })
  })
  await page.route('https://cdn.noviis.kr/missing-image.png', async (route) => {
    await route.fulfill({ status: 404, body: '' })
  })
  const sandbox = await loadSandbox(page, `
    <img id="delayed" src="https://cdn.noviis.kr/delayed-image.svg" width="320" height="240" alt="Photo">
    <img id="missing" src="https://cdn.noviis.kr/missing-image.png" width="100" height="100" alt="Missing">
  `, 800, 500)
  const image = sandbox.locator('#delayed')
  try {
    await expect(image).toHaveAttribute('data-image-state', 'loading')
    await expect(image).toHaveAttribute('aria-busy', 'true')
    expect(await image.evaluate((element) => getComputedStyle(element).content)).toContain('data:image/svg+xml')
    expect(await image.boundingBox()).toMatchObject({ width: 320, height: 240 })
  } finally {
    releaseImage()
  }
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  await expect(image).not.toHaveAttribute('aria-busy')
  expect(await image.evaluate((element) => (element as HTMLImageElement).naturalWidth)).toBe(640)
  await expect(sandbox.locator('#missing')).toHaveAttribute('data-image-state', 'error')
  await expect(sandbox.locator('#missing')).toHaveAttribute('aria-label', '이미지를 불러올 수 없습니다')
  await expect(sandbox.locator('#missing')).not.toHaveAttribute('aria-busy')
})

test('cached images and changed image sources update presentation and frame height', async ({ page }) => {
  await page.route('https://cdn.noviis.kr/replacement.svg', async (route) => {
    await route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="700"></svg>',
    })
  })
  const source = await buildSandboxSource(page, '<img id="image" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%22200%22 height=%22100%22%3E%3C/svg%3E" aria-label="Original label">')
  await page.setContent('<iframe id="sandbox" sandbox="allow-scripts" style="display:block;border:0;width:800px;height:240px"></iframe>')
  await page.evaluate((srcdoc) => {
    const frame = document.querySelector<HTMLIFrameElement>('#sandbox')!
    window.addEventListener('message', (event) => {
      if (event.source === frame.contentWindow && event.data?.id === 'e2e-frame') {
        frame.style.height = `${Math.max(240, event.data.height)}px`
      }
    })
    frame.srcdoc = srcdoc
  }, source)
  const image = page.frameLocator('#sandbox').locator('#image')
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  await image.evaluate((element) => { (element as HTMLImageElement).src = 'data:image/png,invalid' })
  await expect(image).toHaveAttribute('data-image-state', 'error')
  await image.evaluate((element) => { (element as HTMLImageElement).src = 'https://cdn.noviis.kr/replacement.svg' })
  await expect(image).toHaveAttribute('data-image-state', 'loaded')
  await expect(image).toHaveAttribute('aria-label', 'Original label')
  await expect.poll(() => page.locator('#sandbox').evaluate((frame) => frame.getBoundingClientRect().height)).toBeGreaterThanOrEqual(700)
})

test('height scans are batched for cached images and event bursts, and cancelled on cleanup', async ({ page }) => {
  const image = '<img width="20" height="20" src="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2220%22 height=%2220%22/%3E">'
  await page.setContent(Array.from({ length: 150 }, () => `<div>${image}</div>`).join(''))

  const result = await page.evaluate((bridge) => {
    const images = Array.from(document.images)
    if (images.some((element) => !element.complete || element.naturalWidth === 0)) {
      throw new Error('Expected decoded images before installing the bridge')
    }

    // Control frame delivery, while retaining actual browser image/layout behavior.
    // Count the body measurement that starts each full-document height scan.
    const frames = new Map<number, FrameRequestCallback>()
    let nextFrameId = 0
    window.requestAnimationFrame = (callback) => {
      const id = ++nextFrameId
      frames.set(id, callback)
      return id
    }
    window.cancelAnimationFrame = (id) => { frames.delete(id) }
    let scans = 0
    const measureBody = document.body.getBoundingClientRect.bind(document.body)
    document.body.getBoundingClientRect = () => {
      scans += 1
      return measureBody()
    }
    const flushFrame = () => {
      const callbacks = Array.from(frames.values())
      frames.clear()
      callbacks.forEach((callback) => callback(performance.now()))
    }
    const snapshot = () => ({ scans, queuedFrames: frames.size })

    const script = document.createElement('script')
    script.dataset.frameId = 'batch-test'
    script.textContent = bridge
    document.body.append(script)
    const startup = snapshot()
    const loadedImages = images.filter((element) => element.dataset.imageState === 'loaded').length
    flushFrame()
    const firstFrame = snapshot()

    images.forEach((element) => {
      element.dispatchEvent(new Event('error'))
      element.dispatchEvent(new Event('load'))
    })
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('load'))
    const burst = snapshot()
    flushFrame()
    const burstFrame = snapshot()

    window.dispatchEvent(new Event('resize'))
    const beforeCleanup = snapshot()
    window.dispatchEvent(new Event('pagehide'))
    images[0]!.dispatchEvent(new Event('load'))
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('load'))
    flushFrame()
    return { loadedImages, startup, firstFrame, burst, burstFrame, beforeCleanup, afterCleanup: snapshot() }
  }, await heightBridgeSource)

  expect(result.loadedImages).toBe(150)
  expect(result.startup).toEqual({ scans: 0, queuedFrames: 1 })
  expect(result.firstFrame).toEqual({ scans: 1, queuedFrames: 0 })
  expect(result.burst).toEqual({ scans: 1, queuedFrames: 1 })
  expect(result.burstFrame).toEqual({ scans: 2, queuedFrames: 0 })
  expect(result.beforeCleanup).toEqual({ scans: 2, queuedFrames: 1 })
  expect(result.afterCleanup).toEqual({ scans: 2, queuedFrames: 0 })
})
