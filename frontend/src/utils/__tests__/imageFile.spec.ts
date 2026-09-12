import { afterEach, describe, expect, it, vi } from 'vitest'
import { calculateBoundedImageSize, getFileExtension, resizeImageToBoundsFile, validateImageFile } from '../imageFile'

describe('imageFile', () => {
  const allowedMimeTypes = new Set(['image/png', 'image/webp'])
  const allowedExtensions = new Set(['.png', '.webp'])

  it('extracts lower-case file extensions', () => {
    expect(getFileExtension('photo.PNG')).toBe('.png')
    expect(getFileExtension('archive.preview.webp')).toBe('.webp')
    expect(getFileExtension('no-extension')).toBe('')
  })

  it('validates mime type, extension and max size', () => {
    expect(validateImageFile(
      new File(['x'], 'icon.png', { type: 'image/png' }),
      { allowedMimeTypes, allowedExtensions, maxSizeBytes: 10 }
    )).toBeNull()

    expect(validateImageFile(
      new File(['x'], 'icon.svg', { type: 'image/svg+xml' }),
      { allowedMimeTypes, allowedExtensions, maxSizeBytes: 10 }
    )).toBe('type')

    expect(validateImageFile(
      new File(['x'], 'icon.png', { type: 'image/webp' }),
      { allowedMimeTypes, allowedExtensions, maxSizeBytes: 10 }
    )).toBeNull()

    expect(validateImageFile(
      new File([new ArrayBuffer(11)], 'icon.webp', { type: 'image/webp' }),
      { allowedMimeTypes, allowedExtensions, maxSizeBytes: 10 }
    )).toBe('size')
  })

  it('calculates integer bounded image sizes without upscaling', () => {
    expect(calculateBoundedImageSize(2048, 1024, 160, 160)).toEqual({
      width: 160,
      height: 80,
    })
    expect(calculateBoundedImageSize(100, 80, 160, 160)).toEqual({
      width: 100,
      height: 80,
    })
    expect(calculateBoundedImageSize(900, 1600, 256, 128)).toEqual({
      width: 72,
      height: 128,
    })
  })
})


describe('resizeImageToBoundsFile', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  function mockImageEncoding(encodedType: string) {
    const encodedBytes = new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])
    const encodedBlob = new Blob([encodedBytes], { type: encodedType })
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:profile-source')
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    vi.spyOn(globalThis, 'Image').mockImplementation(function () {
      const image = document.createElement('img')
      image.width = 320
      image.height = 160
      queueMicrotask(() => image.onload?.(new Event('load')))
      return image
    })
    const drawImage = vi.fn()
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage } as unknown as CanvasRenderingContext2D)
    const encode = vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) => callback(encodedBlob))
    return { drawImage, encode, encodedBlob, revoke }
  }

  it('uses the PNG encoder output type and filename when GIF encoding falls back', async () => {
    const { drawImage, encode, encodedBlob, revoke } = mockImageEncoding('image/png')
    const source = new File(['gif-source'], 'avatar.animation.gif', { type: 'image/gif' })

    const resized = await resizeImageToBoundsFile(source, 100, 100)

    expect(encode).toHaveBeenCalledWith(expect.any(Function), 'image/gif', undefined)
    expect(resized.name).toBe('avatar.animation.png')
    expect(resized.type).toBe('image/png')
    expect(resized.size).toBe(encodedBlob.size)
    expect(drawImage).toHaveBeenCalledWith(expect.any(HTMLImageElement), 0, 0, 100, 50)
    expect(revoke).toHaveBeenCalledWith('blob:profile-source')
  })

  it.each([
    ['image/png', 'avatar.PNG'],
    ['image/jpeg', 'avatar.jpg'],
    ['image/jpeg', 'avatar.JPEG'],
    ['image/webp', 'avatar.webp'],
  ])('preserves a compatible filename for %s output', async (type, name) => {
    mockImageEncoding(type)
    const resized = await resizeImageToBoundsFile(new File(['source'], name, { type }), 100, 100)

    expect(resized.name).toBe(name)
    expect(resized.type).toBe(type)
  })
})
