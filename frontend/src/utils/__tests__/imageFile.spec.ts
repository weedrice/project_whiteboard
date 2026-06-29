import { describe, expect, it } from 'vitest'
import { getFileExtension, validateImageFile } from '../imageFile'

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
})
