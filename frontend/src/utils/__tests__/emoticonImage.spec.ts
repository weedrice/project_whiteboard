import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createEmoticonImagePreview,
  createUploadableEmoticonImageFile,
  createUploadableEmoticonThumbnailFile,
  resolveEmoticonTagAddition,
  resizeEmoticonImage,
  revokeEmoticonPreviewUrl,
  uploadEmoticonImagePreviews,
  validateEmoticonImageFile
} from '../emoticonImage'

let nextImageSize = { width: 100, height: 100 }
let shouldFailImageLoad = false

class MockImage {
  width = 0
  height = 0
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  set src(_value: string) {
    queueMicrotask(() => {
      if (shouldFailImageLoad) {
        this.onerror?.()
        return
      }
      this.width = nextImageSize.width
      this.height = nextImageSize.height
      this.onload?.()
    })
  }
}

describe('emoticonImage utilities', () => {
  const createObjectURL = vi.fn((file: File) => `blob:${file.name}`)
  const revokeObjectURL = vi.fn()

  beforeEach(() => {
    nextImageSize = { width: 100, height: 100 }
    shouldFailImageLoad = false
    createObjectURL.mockClear()
    revokeObjectURL.mockClear()
    vi.stubGlobal('Image', MockImage)
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL,
      revokeObjectURL
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('validates supported image types and GIF size limits', () => {
    const textFile = new File(['text'], 'note.txt', { type: 'text/plain' })
    const unsupportedImage = new File(['image'], 'vector.svg', { type: 'image/svg+xml' })
    const gifOverOldLimit = new File([new Uint8Array(1024 * 1024 + 1)], 'ok.gif', { type: 'image/gif' })
    const oversizedGif = new File([new Uint8Array(3 * 1024 * 1024 + 1)], 'large.gif', { type: 'image/gif' })
    const validPng = new File(['image'], 'valid.png', { type: 'image/png' })

    expect(validateEmoticonImageFile(textFile, { nonImageReason: 'imageOnly' })).toBe('imageOnly')
    expect(validateEmoticonImageFile(textFile)).toBe('notImage')
    expect(validateEmoticonImageFile(unsupportedImage)).toBe('notImage')
    expect(validateEmoticonImageFile(gifOverOldLimit)).toBeNull()
    expect(validateEmoticonImageFile(oversizedGif)).toBe('gifSizeExceeded')
    expect(validateEmoticonImageFile(validPng)).toBeNull()
  })

  it('creates previews and revokes failed preview URLs', async () => {
    const file = new File(['image'], 'ok.png', { type: 'image/png' })

    await expect(createEmoticonImagePreview(file)).resolves.toEqual({
      ok: true,
      item: {
        clientId: expect.any(String),
        file,
        preview: 'blob:ok.png',
        width: 100,
        height: 100
      }
    })
    expect(revokeObjectURL).not.toHaveBeenCalled()

    nextImageSize = { width: 2049, height: 400 }
    await expect(createEmoticonImagePreview(file)).resolves.toEqual({
      ok: false,
      reason: 'sizeExceeded',
      width: 2049,
      height: 400
    })
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:ok.png')

    shouldFailImageLoad = true
    await expect(createEmoticonImagePreview(file)).resolves.toEqual({
      ok: false,
      reason: 'loadFailed'
    })
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:ok.png')
  })

  it('allows GIF previews over the static image source dimension limit', async () => {
    nextImageSize = { width: 4096, height: 4096 }
    const gif = new File(['gif'], 'animated.gif', { type: 'image/gif' })

    await expect(createEmoticonImagePreview(gif)).resolves.toEqual({
      ok: true,
      item: {
        clientId: expect.any(String),
        file: gif,
        preview: 'blob:animated.gif',
        width: 4096,
        height: 4096
      }
    })
    expect(revokeObjectURL).not.toHaveBeenCalled()
  })

  it('revokes only object preview URLs', () => {
    revokeEmoticonPreviewUrl('https://cdn.example.com/thumb.png')
    revokeEmoticonPreviewUrl(null)
    revokeEmoticonPreviewUrl('blob:local-preview')

    expect(revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:local-preview')
  })

  it('preserves GIF uploads and wraps resized blobs as files', async () => {
    const gif = new File(['gif'], 'animated.gif', { type: 'image/gif' })
    const resizedGifBlob = await resizeEmoticonImage(gif)

    expect(resizedGifBlob.type).toBe('image/gif')

    const uploadFile = await createUploadableEmoticonImageFile({
      clientId: 'gif-preview',
      file: gif,
      preview: 'blob:animated.gif',
      width: 120,
      height: 120
    })

    expect(uploadFile).toBe(gif)
  })

  it('wraps resized non-GIF blobs as files with the original name', async () => {
    const originalCreateElement = document.createElement.bind(document)
    const drawImage = vi.fn()

    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      if (tagName === 'canvas') {
        return {
          width: 0,
          height: 0,
          getContext: () => ({ drawImage }),
          toBlob: (callback: BlobCallback, type?: string) => {
            callback(new Blob(['resized'], { type }))
          }
        } as unknown as HTMLCanvasElement
      }
      return originalCreateElement(tagName)
    })

    nextImageSize = { width: 240, height: 120 }
    const png = new File(['png'], 'big.png', { type: 'image/png' })
    const uploadFile = await createUploadableEmoticonImageFile({
      clientId: 'png-preview',
      file: png,
      preview: 'blob:big.png',
      width: 240,
      height: 120
    })

    expect(uploadFile).toBeInstanceOf(File)
    expect(uploadFile.name).toBe('big.png')
    expect(uploadFile.type).toBe('image/png')
    expect(drawImage).toHaveBeenCalled()
  })

  it('resizes thumbnail uploads with the thumbnail dimension limit', async () => {
    const originalCreateElement = document.createElement.bind(document)
    const drawImage = vi.fn()
    const canvasSizes: Array<{ width: number; height: number }> = []

    vi.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      if (tagName === 'canvas') {
        const canvas = {
          width: 0,
          height: 0,
          getContext: () => ({ drawImage }),
          toBlob: (callback: BlobCallback, type?: string) => {
            canvasSizes.push({ width: canvas.width, height: canvas.height })
            callback(new Blob(['resized-thumbnail'], { type }))
          }
        } as unknown as HTMLCanvasElement
        return canvas
      }
      return originalCreateElement(tagName)
    })

    nextImageSize = { width: 1024, height: 512 }
    const thumbnail = new File(['thumbnail'], 'thumb.png', { type: 'image/png' })
    const uploadFile = await createUploadableEmoticonThumbnailFile(thumbnail)

    expect(uploadFile).toBeInstanceOf(File)
    expect(uploadFile.name).toBe('thumb.png')
    expect(uploadFile.type).toBe('image/png')
    expect(canvasSizes).toEqual([{ width: 256, height: 128 }])
  })

  it('uploads previews concurrently while preserving original result order', async () => {
    const files = [
      new File(['first'], 'first.png', { type: 'image/png' }),
      new File(['second'], 'second.png', { type: 'image/png' })
    ]
    const progress: Array<{ completed: number; total: number }> = []
    const resolvers: Array<(value: number) => void> = []

    const resultPromise = uploadEmoticonImagePreviews(
      files.map((file) => ({
        clientId: `preview-${file.name}`,
        file,
        preview: `blob:${file.name}`,
        width: 80,
        height: 80
      })),
      (file, _item, index) => new Promise<number>((resolve) => {
        expect(file).toBe(files[index])
        resolvers[index] = resolve
      }),
      (completed, total) => {
        progress.push({ completed, total })
      }
    )

    await Promise.resolve()
    resolvers[1](22)
    await Promise.resolve()
    expect(progress).toEqual([{ completed: 1, total: 2 }])

    resolvers[0](11)
    await expect(resultPromise).resolves.toEqual([11, 22])
    expect(progress).toEqual([
      { completed: 1, total: 2 },
      { completed: 2, total: 2 }
    ])
  })

  it('normalizes tag additions and enforces the tag limit', () => {
    expect(resolveEmoticonTagAddition(' #happy ', [])).toEqual({ tag: 'happy', error: null })
    expect(resolveEmoticonTagAddition('happy', ['happy'])).toEqual({ tag: null, error: null })
    expect(resolveEmoticonTagAddition('', [])).toEqual({ tag: null, error: null })
    expect(resolveEmoticonTagAddition('new', Array.from({ length: 10 }, (_, index) => `tag-${index}`))).toEqual({
      tag: null,
      error: 'maxTags'
    })
  })
})
