import { afterEach, describe, expect, it, vi } from 'vitest'
import { encodeSandboxedPostHtml, decodeSandboxedPostHtml } from '../postHtmlSandbox'
import { extractViewportHtml, hydrateViewportImages, MAX_VIEWPORT_IMAGE_BYTES, ViewportImageValidationError } from '../postViewportEffect'

afterEach(() => vi.restoreAllMocks())
const layer = '<template data-noviis-layer="viewport"><style>.petal{animation:fall 2s infinite}</style><div class="petal">🌸</div></template>'

describe('viewport HTML authoring', () => {
  it('keeps the HTML source through preservation and extracts only the explicit layer', () => {
    const source = '<p>본문</p>' + layer
    const saved = encodeSandboxedPostHtml(source)
    expect(decodeSandboxedPostHtml(saved)).toBe(source)
    expect(extractViewportHtml(saved)).toEqual(extractViewportHtml(source))
    expect(extractViewportHtml(saved).html).toContain('🌸')
    expect(extractViewportHtml('<p>ordinary</p>').html).toBeNull()
    expect(extractViewportHtml('<p data-example="&lt;template data-noviis-layer=viewport&gt;">example</p>').html).toBeNull()
  })
  it('rejects ambiguous layers and oversized content', () => {
    expect(extractViewportHtml(layer + layer)).toMatchObject({ invalid: true, reason: 'duplicateLayers' })
    expect(extractViewportHtml(`<template data-noviis-layer="viewport">${'x'.repeat(65537)}</template>`)).toMatchObject({ invalid: true, reason: 'htmlTooLarge' })
    expect(extractViewportHtml(`<template data-noviis-layer="viewport">${'<p>x</p>'.repeat(301)}</template>`)).toMatchObject({ invalid: true, reason: 'tooManyElements' })
  })
  it.each([
    ['<style>.x{background:url(/image.png)}</style>', 'cssResource'],
    ['<img src="/api/v1/files/12">'.repeat(9), 'tooManyImages'],
    ['<img src="https://outside.example/image.png">', 'imageSource'],
    ['<img src="/api/v1/files/12" data-file-id="13">', 'imageSource'],
  ])('identifies static authoring errors before playback: %s', (html, reason) => {
    expect(extractViewportHtml(`<template data-noviis-layer="viewport">${html}</template>`))
      .toEqual({ html: null, invalid: true, reason })
  })
  it('does not report unsupported images from discarded subtrees', () => {
    expect(extractViewportHtml('<template data-noviis-layer="viewport"><form><img src="https://outside.example/x"></form></template>').invalid).toBe(false)
  })
  it.each(['svg', 'math'])('does not mistake a foreign %s element for an HTML template', (tag) => {
    expect(extractViewportHtml(`<${tag}><template data-noviis-layer="viewport"></template></${tag}>`)).toEqual({ html: null, invalid: true })
  })
})

describe('viewport image resources', () => {
  it('rejects repeated image data before base64 encoding while retaining download deduplication', async () => {
    const download = vi.fn().mockResolvedValue(new Blob([new Uint8Array(2 * 1024 * 1024)], { type: 'image/png' }))
    const read = vi.spyOn(FileReader.prototype, 'readAsDataURL')
    await expect(hydrateViewportImages('<img src="/api/v1/files/12">'.repeat(5), download, () => true)).rejects.toMatchObject({ reason: 'imageDataTooLarge' })
    expect(download).toHaveBeenCalledExactlyOnceWith('/files/12')
    expect(read).not.toHaveBeenCalled()
  })
  it('does not download images from subtrees the renderer removes', async () => {
    const download = vi.fn()
    const result = await hydrateViewportImages('<form><img src="/api/v1/files/1"></form><template><img src="/api/v1/files/2"></template><svg><foreignObject><img src="/api/v1/files/3"></foreignObject></svg>', download, () => true)
    expect(result.imageSources.size).toBe(0)
    expect(download).not.toHaveBeenCalled()
  })
  it('validates all image paths before starting any download', async () => {
    const download = vi.fn()
    await expect(hydrateViewportImages('<img src="/api/v1/files/1"><img src="https://example.com/image.png">', download, () => true)).rejects.toBeInstanceOf(ViewportImageValidationError)
    expect(download).not.toHaveBeenCalled()
  })
  it('loads original uploaded images once and embeds unchanged bytes separately from author HTML', async () => {
    const download = vi.fn().mockResolvedValue(new Blob(['animated'], { type: 'image/webp' }))
    const result = await hydrateViewportImages('<img src="/api/v1/files/12" data-file-id="12" srcset="https://evil.test/a 2x"><img src="/files/12">', download, () => true)
    expect(download).toHaveBeenCalledExactlyOnceWith('/files/12')
    expect(result.imageSources.size).toBe(1)
    expect(result.imageSources.get('noviis-viewport-image:0')).toBe('data:image/webp;base64,YW5pbWF0ZWQ=')
    expect(result.html).not.toContain('srcset')
    expect(result.html).toContain('noviis-viewport-image:0')
  })
  it.each([
    '<img src="https://evil.test/api/v1/files/12">',
    '<img src="/api/v1/files/12" data-file-id="13">',
    '<img src="/api/v1/files/12?download=true">',
    '<img src="data:image/png;base64,aaa">',
    '<img src="/api/v1/files/12">'.repeat(9),
  ])('rejects unsupported resources before downloading', async (html) => {
    const download = vi.fn()
    await expect(hydrateViewportImages(html, download, () => true)).rejects.toThrow()
    expect(download).not.toHaveBeenCalled()
  })
  it('does not return a partially hydrated effect if a later image fails', async () => {
    const download = vi.fn().mockResolvedValueOnce(new Blob(['ok'], { type: 'image/png' })).mockRejectedValueOnce(new Error('Forbidden'))
    await expect(hydrateViewportImages('<img src="/api/v1/files/1"><img src="/api/v1/files/2">', download, () => true)).rejects.toThrow()
  })
  it('does not encode image bytes after a session or content change', async () => {
    const read = vi.spyOn(FileReader.prototype, 'readAsDataURL')
    let current = true
    await expect(hydrateViewportImages('<img src="/api/v1/files/1">', async () => {
      current = false
      return new Blob(['ok'], { type: 'image/png' })
    }, () => current)).rejects.toThrow()
    expect(read).not.toHaveBeenCalled()
  })
  it.each([
    { blob: new Blob(['<svg/>'], { type: 'image/svg+xml' }), reason: 'imageType' },
    { blob: new Blob([new Uint8Array(MAX_VIEWPORT_IMAGE_BYTES + 1)], { type: 'image/png' }), reason: 'imageBytes' },
  ])('identifies downloaded image format and byte-budget errors', async ({ blob, reason }) => {
    await expect(hydrateViewportImages('<img src="/api/v1/files/1">', async () => blob, () => true))
      .rejects.toMatchObject({ reason })
  })
})
