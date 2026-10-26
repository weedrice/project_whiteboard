import { expect, test, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const fixture = '/e2e/fixtures/viewport-html-effect.html'
// Two lossless WebP frames, generated locally. The original animation must not use a thumbnail route.
const animatedWebp = Buffer.from('UklGRoQAAABXRUJQVlA4WAoAAAASAAAAAQAAAQAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAAAAEAAAEAAJYAAAJWUDhMDwAAAC8BQAAQBxD9j9IGIqL/AQBBTk1GKAAAAAAAAAAAAAEAAAEAAJYAAAJWUDhMDwAAAC8BQAAQBxDR/9IGIqL/AQA=', 'base64')
const frameSelector = '[data-testid="viewport-effect-frame"]'

async function play(page: Page) {
  await page.getByTestId('viewport-effect-panel').getByRole('button').click()
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(1)
}

for (const viewport of [{ width: 1280, height: 800 }, { width: 390, height: 844 }]) {
  test(`explicitly plays isolated CSS animation across the ${viewport.width}px viewport`, async ({ page }, testInfo) => {
    // Cold WebKit startup plus keyboard/modal checks can exceed the default 30s on CI.
    test.setTimeout(60_000)
    await page.setViewportSize(viewport)
    await page.goto(fixture)
    const frame = page.getByTestId('viewport-effect-frame')
    await expect(page.getByTestId('viewport-effect-panel')).toBeVisible()
    await expect(frame).toHaveCount(0)
    await play(page)
    await expect(frame).toHaveAttribute('sandbox', '')
    await expect(frame).toHaveAttribute('inert', '')
    await expect(frame).toHaveAttribute('tabindex', '-1')
    expect(await frame.boundingBox()).toEqual({ x: 0, y: 0, ...viewport })
    const inner = page.frameLocator(frameSelector)
    await expect(inner.locator('#moving')).toBeVisible()
    expect(await inner.locator('#moving').evaluate(element => getComputedStyle(element).animationName)).toBe('falling')
    await expect.poll(() => inner.locator('#moving').evaluate(element => element.getAnimations().length)).toBeGreaterThan(0)
    await page.screenshot({ path: testInfo.outputPath(`viewport-active-${viewport.width}.png`) })
    await expect(inner.locator('script,a,input')).toHaveCount(0)
    expect(await page.evaluate(() => '__viewportScriptRan' in window)).toBe(false)
    expect(await inner.locator('meta[http-equiv="Content-Security-Policy"]').getAttribute('content')).toContain("script-src 'none'")
    await page.evaluate(() => window.scrollTo(0, 300))
    expect(await frame.boundingBox()).toEqual({ x: 0, y: 0, ...viewport })
    await page.locator('#underneath').click()
    await expect(page.locator('#click-count')).toHaveText('1')
    await page.locator('#underneath').focus()
    for (let index = 0; index < 10; index += 1) {
      await page.keyboard.press('Tab')
      expect(await page.evaluate(() => document.activeElement?.tagName)).not.toBe('IFRAME')
    }
    const layerZ = await frame.evaluate(element => Number(getComputedStyle(element).zIndex))
    expect(await page.locator('#host-nav').evaluate(element => Number(getComputedStyle(element).zIndex))).toBeGreaterThan(layerZ)
    await page.locator('#show-modal').click()
    expect(await page.locator('#host-modal').evaluate(element => Number(getComputedStyle(element).zIndex))).toBeGreaterThan(layerZ)
    await page.locator('#close-modal').click()
    await expect(page.locator('#host-modal')).toHaveCount(0)
    await page.keyboard.press('Escape')
    await expect(frame).toHaveCount(0)
  })
}

test('stops on content, access, session and document visibility changes', async ({ page }) => {
  for (const action of ['#change-content', '#disable', '#session', 'hidden']) {
    await page.goto(fixture)
    await play(page)
    if (action === 'hidden') {
      await page.evaluate(() => {
        Object.defineProperty(document, 'hidden', { configurable: true, value: true })
        document.dispatchEvent(new Event('visibilitychange'))
      })
    } else await page.locator(action).click()
    await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
  }
})

test('expires the effect after thirty seconds', async ({ page }) => {
  await page.goto(fixture)
  await expect(page.getByTestId('viewport-effect-panel')).toBeVisible()
  await page.clock.install()
  await play(page)
  await page.clock.fastForward(30_001)
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
})

test('honors reduced motion before playback and while an effect is active', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto(fixture)
  await expect(page.getByTestId('viewport-effect-panel').getByRole('button')).toBeDisabled()
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await play(page)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
})

test('fetches an original animated upload through the parent and renders its raster bytes in the opaque sandbox', async ({ page }) => {
  const requests: { path: string; type: string }[] = []
  await page.route('**/api/v1/files/**', async route => {
    requests.push({ path: new URL(route.request().url()).pathname, type: route.request().resourceType() })
    await route.fulfill({ contentType: 'image/webp', body: animatedWebp })
  })
  await page.goto(`${fixture}?mode=image`)
  await expect(page.getByTestId('viewport-effect-panel')).toBeVisible()
  expect(requests).toEqual([])
  // Install the current deployment policy after the fixture's inline bootstrap.
  // The child srcdoc inherits this policy in addition to its stricter own CSP.
  const policy = readFileSync(resolve(process.cwd(), 'nginx.conf'), 'utf8')
    .match(/add_header Content-Security-Policy "([^"]+)"/)?.[1]
  expect(policy).toBeTruthy()
  await page.evaluate(value => {
    const meta = document.createElement('meta')
    meta.httpEquiv = 'Content-Security-Policy'
    meta.content = value!
    document.head.append(meta)
  }, policy)
  await play(page)
  const image = page.frameLocator(frameSelector).locator('#animated-upload')
  await expect(image).toHaveAttribute('src', `data:image/webp;base64,${animatedWebp.toString('base64')}`)
  await expect.poll(() => image.evaluate(element => (element as HTMLImageElement).naturalWidth)).toBe(2)
  expect(requests).toHaveLength(1)
  expect(requests[0]?.path).toBe('/api/v1/files/9901')
  expect(requests[0]?.type).not.toBe('image')
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
})

test('does not mount a frame when the authenticated upload fails', async ({ page }) => {
  await page.route('**/api/v1/files/**', route => route.fulfill({ status: 500, body: 'failed' }))
  await page.goto(`${fixture}?mode=image`)
  await page.getByTestId('viewport-effect-panel').getByRole('button').click()
  await expect(page.getByTestId('viewport-effect-panel').getByRole('status')).toHaveText('화면 효과 이미지를 불러올 수 없습니다.')
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
})

test('explains unsupported downloaded image types separately from network failures', async ({ page }) => {
  let requests = 0
  await page.route('**/api/v1/files/**', async route => {
    requests += 1
    await route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg"/>' })
  })
  await page.goto(`${fixture}?mode=image`)
  const panel = page.getByTestId('viewport-effect-panel')
  await expect(panel.getByRole('button')).toBeEnabled()
  await panel.getByRole('button').click()
  await expect(panel.getByRole('status')).toHaveText('화면 효과에는 PNG, JPEG, GIF, WebP 이미지만 사용할 수 있습니다.')
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
  expect(requests).toBe(1)
})

for (const invalid of [
  { mode: 'duplicate', correction: '중복된 영역을 제거해 주세요.' },
  { mode: 'too-many-images', correction: '최대 8개입니다. 이미지 태그 수를 줄여 주세요.' },
  { mode: 'unsupported-image', correction: '일반 편집기에서 업로드한 img 태그를 그대로 옮겨 주세요.' },
]) {
  test(`explains and disables ${invalid.mode} before playback or image downloads`, async ({ page }) => {
    const requests: string[] = []
    await page.route(/\/api\/v1\/files\/|https:\/\/invalid\.test\//, async route => {
      requests.push(route.request().url())
      await route.abort()
    })
    await page.goto(`${fixture}?mode=${invalid.mode}`)
    const panel = page.getByTestId('viewport-effect-panel')
    await expect(panel.getByRole('status')).toContainText(invalid.correction)
    await expect(panel.getByRole('button')).toBeDisabled()
    await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
    expect(requests).toEqual([])
  })
}

for (const mode of ['css-url', 'css-comment']) {
  test(`rejects ${mode} CSS resources before mounting or requesting them`, async ({ page }) => {
    const requests: string[] = []
    await page.route('**/forbidden-decoration.png', async route => {
      requests.push(route.request().url())
      await route.abort()
    })
    await page.goto(`${fixture}?mode=${mode}`)
    const panel = page.getByTestId('viewport-effect-panel')
    await expect(panel.getByRole('status')).toHaveText('CSS로 이미지나 외부 리소스를 불러올 수 없습니다. 이미지는 업로드한 img 태그로 넣어 주세요.')
    await expect(panel.getByRole('button')).toBeDisabled()
    await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
    expect(requests).toEqual([])
  })
}

test('does not reserve a blank body frame for an effect-only post', async ({ page }) => {
  await page.goto(`${fixture}?mode=blank`)
  await expect(page.getByTestId('viewport-effect-panel')).toBeVisible()
  await expect(page.locator('#post-content iframe')).toHaveCount(0)
  await expect(page.locator('#post-content')).toHaveText('')
  expect(await page.locator('#post-content').evaluate(element => element.getBoundingClientRect().height)).toBe(0)
  await play(page)
  await expect(page.locator('#post-content iframe')).toHaveCount(0)
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('viewport-effect-frame')).toHaveCount(0)
})
