import { chromium, expect, test, type Browser, type Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { createServer } from 'node:http'
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

  await expect(sandbox.locator('html')).toHaveCSS('overflow-y', 'auto')
  await expect(sandbox.locator('body')).toHaveCSS('overflow-y', 'auto')
  const metrics = await sandbox.locator('html').evaluate((root) => ({
    scrollHeight: root.scrollHeight,
    clientHeight: root.clientHeight,
  }))

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
  await expect(sandbox.locator('#missing')).toHaveAttribute('aria-label', 'Missing — 이미지를 불러올 수 없습니다')
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
    const activeIntervals = new Set<number>()
    const nativeSetInterval = window.setInterval.bind(window)
    const nativeClearInterval = window.clearInterval.bind(window)
    window.setInterval = (handler, timeout) => {
      const id = nativeSetInterval(handler, timeout)
      activeIntervals.add(id)
      return id
    }
    window.clearInterval = (id) => {
      if (id !== undefined) activeIntervals.delete(id)
      nativeClearInterval(id)
    }
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
    const afterCleanup = snapshot()
    const stoppedIntervals = activeIntervals.size
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
    const resumed = { ...snapshot(), intervals: activeIntervals.size }
    flushFrame()
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }))
    flushFrame()
    const stoppedAgain = { ...snapshot(), intervals: activeIntervals.size }
    window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
    flushFrame()
    const resumedAgain = { ...snapshot(), intervals: activeIntervals.size }
    window.dispatchEvent(new Event('pagehide'))
    return { loadedImages, startup, firstFrame, burst, burstFrame, beforeCleanup, afterCleanup, stoppedIntervals, resumed, stoppedAgain, resumedAgain }
  }, await heightBridgeSource)

  expect(result.loadedImages).toBe(150)
  expect(result.startup).toEqual({ scans: 0, queuedFrames: 1 })
  expect(result.firstFrame).toEqual({ scans: 1, queuedFrames: 0 })
  expect(result.burst).toEqual({ scans: 1, queuedFrames: 1 })
  expect(result.burstFrame).toEqual({ scans: 2, queuedFrames: 0 })
  expect(result.beforeCleanup).toEqual({ scans: 2, queuedFrames: 1 })
  expect(result.afterCleanup).toEqual({ scans: 2, queuedFrames: 0 })
  expect(result.stoppedIntervals).toBe(0)
  expect(result.resumed).toEqual({ scans: 2, queuedFrames: 1, intervals: 1 })
  expect(result.stoppedAgain).toEqual({ scans: 3, queuedFrames: 0, intervals: 0 })
  expect(result.resumedAgain).toEqual({ scans: 4, queuedFrames: 0, intervals: 1 })
})

test('sandbox srcdoc resumes height and image observation after repeated BFCache returns', async () => {
  const bridge = await heightBridgeSource
  const image = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 width=%2220%22 height=%2220%22/%3E'
  const frame = `
    <style>body{margin:0}#content{height:1200px}@media(max-width:500px){#content{height:2400px}}</style>
    <div id="content">Responsive content</div>
    <script src="/bridge.js" data-frame-id="bfcache"></script>
    <script>
      addEventListener('message', function (event) {
        if (event.data === 'grow') document.querySelector('#content').style.height = '1800px';
        if (event.data === 'image') {
          var image = document.createElement('img');
          image.src = '${image}';
          image.style.position = 'absolute';
          image.style.top = '0';
          document.body.append(image);
          image.decode().then(function () {
            requestAnimationFrame(function () {
              parent.postMessage({ imageState: image.dataset.imageState }, '*');
            });
          });
        }
      });
    </script>`
  const srcdoc = frame.replace(/&/g, '&amp;').replace(/"/g, '&quot;')
  const server = createServer((request, response) => {
    response.setHeader('Content-Type', request.url === '/bridge.js' ? 'application/javascript' : 'text/html')
    if (request.url === '/bridge.js') {
      response.end(bridge)
    } else if (request.url === '/away') {
      response.end('<h1>Another page</h1>')
    } else {
      response.end(`
        <a id="leave" href="/away">Leave</a>
        <button id="grow" onclick="document.querySelector('iframe').contentWindow.postMessage('grow','*')">Grow</button>
        <button id="image" onclick="document.querySelector('iframe').contentWindow.postMessage('image','*')">Image</button>
        <iframe srcdoc="${srcdoc}" sandbox="allow-scripts" style="display:block;width:100%;height:240px;border:0"></iframe>
        <script>
          var restores = 0;
          addEventListener('pageshow', function (event) {
            if (event.persisted) document.body.dataset.restores = String(++restores);
          });
          addEventListener('message', function (event) {
            var frame = document.querySelector('iframe');
            if (event.source !== frame.contentWindow) return;
            if (event.data.id === 'bfcache') frame.style.height = event.data.height + 'px';
            if (event.data.imageState) document.body.dataset.imageState = event.data.imageState;
          });
        </script>`)
    }
  })
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  // Headless shell and Playwright's default flag disable BFCache. Use the full
  // installed Chromium, with actual HTTP navigation rather than intercepted routes.
  let browser: Browser | undefined
  try {
    browser = await chromium.launch({
      channel: 'chromium',
      headless: true,
      ignoreDefaultArgs: ['--disable-back-forward-cache'],
    })
    const address = server.address()
    if (!address || typeof address === 'string') throw new Error('Expected a local HTTP port')
    const page = await browser.newPage({ viewport: { width: 1000, height: 800 } })
    await page.goto(`http://127.0.0.1:${address.port}/post`)
    const frameHeight = () => page.locator('iframe').evaluate((element) => element.clientHeight)
    await expect.poll(frameHeight).toBe(1200)
    for (const [index, width] of [400, 1000].entries()) {
      await page.locator('#leave').click()
      await page.waitForURL('**/away')
      // BFCache restoration does not emit another load event.
      await page.evaluate(() => history.back())
      await expect(page.locator('body')).toHaveAttribute('data-restores', String(index + 1))
      await page.setViewportSize({ width, height: 800 })
      await expect.poll(frameHeight).toBe(width === 400 ? 2400 : 1200)
    }
    // A content-only change also needs a restored observer/height poller.
    await page.locator('#grow').click()
    await expect.poll(frameHeight).toBe(1800)
    await page.locator('#image').click()
    await expect(page.locator('body')).toHaveAttribute('data-image-state', 'loaded')
  } finally {
    await browser?.close()
    await new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
  }
})


test('loading skeletons pause offscreen and resume after scrolling and cached-page returns', async ({ page }) => {
  let release!: () => void
  const responseReady = new Promise<void>((resolve) => { release = resolve })
  await page.route('https://cdn.noviis.kr/pending-visibility.svg', async (route) => {
    await responseReady
    await route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="180"/>' })
  })
  const sandbox = await loadSandbox(page, `
    <img id="top" src="https://cdn.noviis.kr/pending-visibility.svg" width="320" height="180">
    <div style="height:4000px"></div>
    <img id="bottom" src="https://cdn.noviis.kr/pending-visibility.svg" width="320" height="180">
  `, 800, 500)
  const top = sandbox.locator('#top')
  const bottom = sandbox.locator('#bottom')
  try {
    await expect(top).toHaveAttribute('data-image-state', 'loading')
    await expect(top).not.toHaveAttribute('data-image-offscreen')
    await expect(top).toHaveCSS('animation-play-state', 'running')
    await expect(bottom).toHaveAttribute('data-image-offscreen', '')
    await expect(bottom).toHaveCSS('animation-play-state', 'paused')
    await bottom.scrollIntoViewIfNeeded()
    await expect(bottom).not.toHaveAttribute('data-image-offscreen')
    await expect(bottom).toHaveCSS('animation-play-state', 'running')
    await expect(top).toHaveAttribute('data-image-offscreen', '')
    for (let index = 0; index < 2; index += 1) {
      await bottom.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true })))
      await expect(bottom).toHaveCSS('animation-play-state', 'paused')
      await bottom.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true })))
      await expect(bottom).toHaveCSS('animation-play-state', 'running')
    }
    const detachedMarker = await top.evaluate(async (element) => {
      element.remove()
      await Promise.resolve()
      return element.hasAttribute('data-image-offscreen')
    })
    expect(detachedMarker).toBe(false)
  } finally {
    release()
  }
  await expect(bottom).toHaveAttribute('data-image-state', 'loaded')
  await expect(bottom).not.toHaveAttribute('data-image-offscreen')
})

test('sandbox error symbols and surfaces retain contrast across system and explicit themes', async ({ page }, testInfo) => {
  await page.emulateMedia({ colorScheme: 'light' })
  const loadedImage = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="green"/></svg>')
  const sandbox = await loadSandbox(page, '<p id="text">Author content</p>'
    + '<img id="error" src="data:image/png,invalid" width="32" height="32">'
    + '<img id="loaded" src="' + loadedImage + '" width="32" height="32">', 800, 500)
  const image = sandbox.locator('#error')
  await expect(image).toHaveAttribute('data-image-state', 'error')
  await expect(sandbox.locator('#loaded')).toHaveAttribute('data-image-state', 'loaded')
  const symbol = () => image.evaluate((element) => decodeURIComponent(getComputedStyle(element).backgroundImage))
  const contrast = () => image.evaluate((element) => {
    const style = getComputedStyle(element)
    const foreground = decodeURIComponent(style.backgroundImage).match(/stroke="(#[0-9a-f]{6})"/i)![1]!
    const linear = (value: number) => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
    const luminance = (channels: number[]) => channels.reduce((sum, value, index) => sum + linear(value / 255) * [0.2126, 0.7152, 0.0722][index]!, 0)
    const a = luminance(foreground.slice(1).match(/../g)!.map(value => parseInt(value, 16)))
    const b = luminance(style.backgroundColor.match(/\d+/g)!.slice(0, 3).map(Number))
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
  })
  await expect.poll(symbol).toContain('#3a3a3a')
  await expect.poll(contrast).toBeGreaterThan(7)
  await page.emulateMedia({ colorScheme: 'dark' })
  await expect.poll(symbol).toContain('#c8c7c2')
  await expect.poll(contrast).toBeGreaterThan(7)
  await page.emulateMedia({ colorScheme: 'light' })
  const originalTextColor = await sandbox.locator('#text').evaluate(element => getComputedStyle(element).color)
  const originalLoadedBackground = await sandbox.locator('#loaded').evaluate(element => getComputedStyle(element).backgroundColor)
  await image.evaluate(() => document.documentElement.classList.add('dark'))
  await expect.poll(symbol).toContain('#c8c7c2')
  await expect(image).toHaveCSS('background-color', 'rgb(17, 24, 39)')
  await expect.poll(contrast).toBeGreaterThan(7)
  await expect(sandbox.locator('#text')).toHaveCSS('color', originalTextColor)
  await expect(sandbox.locator('#loaded')).toHaveCSS('background-color', originalLoadedBackground)
  await image.screenshot({ path: testInfo.outputPath('explicit-dark-error.png') })
  await image.evaluate(() => document.documentElement.classList.remove('dark'))
  await expect.poll(symbol).toContain('#3a3a3a')
  await expect(image).toHaveCSS('background-color', 'rgb(243, 244, 246)')
  await expect.poll(contrast).toBeGreaterThan(7)
})

test('sandbox image failures keep link purpose, decorative semantics and label references', async ({ page }) => {
  await page.route('https://cdn.noviis.kr/accessibility-missing.svg', (route) => route.fulfill({ status: 404, body: '' }))
  const sandbox = await loadSandbox(page,
    '<span id="reference">Reference label</span>'
    + '<a id="decorative" href="https://noviis.kr/board"><img src="https://cdn.noviis.kr/accessibility-missing.svg" alt="">Board name</a>'
    + '<a id="described" href="https://noviis.kr/guide"><img src="https://cdn.noviis.kr/accessibility-missing.svg" alt="Read the guide"></a>'
    + '<a id="explicit" href="https://noviis.kr/report"><img src="https://cdn.noviis.kr/accessibility-missing.svg" alt="" aria-label="Download report" title="Original tooltip"></a>'
    + '<a id="referenced" href="https://noviis.kr/reference"><img src="https://cdn.noviis.kr/accessibility-missing.svg" alt="" aria-labelledby="reference"></a>',
    800, 400)
  const error = '이미지를 불러올 수 없습니다'
  await expect(sandbox.locator('#decorative img')).toHaveAttribute('data-image-state', 'error')
  await expect(sandbox.locator('#decorative')).toHaveAccessibleName('Board name')
  expect(await sandbox.locator('#decorative').ariaSnapshot()).not.toContain('img')
  await expect(sandbox.locator('#described')).toHaveAccessibleName('Read the guide — ' + error)
  await expect(sandbox.locator('#explicit')).toHaveAccessibleName('Download report — ' + error)
  await expect(sandbox.locator('#referenced')).toHaveAccessibleName('Reference label')
  await expect(sandbox.locator('#referenced img')).toHaveAccessibleDescription(error)
  await sandbox.locator('img').evaluateAll((images) => images.forEach((image) => {
    ;(image as HTMLImageElement).src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="80" height="40"/%3E'
  }))
  await expect(sandbox.locator('#decorative img')).toHaveAttribute('data-image-state', 'loaded')
  await expect(sandbox.locator('#decorative')).toHaveAccessibleName('Board name')
  expect(await sandbox.locator('#decorative').ariaSnapshot()).not.toContain('img')
  await expect(sandbox.locator('#described')).toHaveAccessibleName('Read the guide')
  await expect(sandbox.locator('#explicit')).toHaveAccessibleName('Download report')
  await expect(sandbox.locator('#explicit img')).toHaveAttribute('title', 'Original tooltip')
  await expect(sandbox.locator('#referenced')).toHaveAccessibleName('Reference label')
  await expect(sandbox.locator('#referenced img')).not.toHaveAttribute('title')
})
