import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useEmoticonImageSelection } from '../useEmoticonImageSelection'

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

const asFileList = (files: File[]): FileList => files as unknown as FileList

describe('useEmoticonImageSelection', () => {
  const addToast = vi.fn()
  const t = vi.fn((key: string, params?: Record<string, string | number>) => {
    if (!params) return key
    return `${key}:${JSON.stringify(params)}`
  })
  const createObjectURL = vi.fn((file: File) => `blob:${file.name}`)
  const revokeObjectURL = vi.fn()

  beforeEach(() => {
    nextImageSize = { width: 100, height: 100 }
    shouldFailImageLoad = false
    addToast.mockClear()
    t.mockClear()
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

  it('maps thumbnail validation failures to thumbnail-specific toasts', async () => {
    const { selectThumbnailImage } = useEmoticonImageSelection(t, { addToast })

    await expect(selectThumbnailImage(new File(['text'], 'note.txt', { type: 'text/plain' }))).resolves.toBeNull()
    expect(addToast).toHaveBeenCalledWith('emoticon.validation.imageOnly', 'error')

    await expect(selectThumbnailImage(new File([new Uint8Array(3 * 1024 * 1024 + 1)], 'large.gif', { type: 'image/gif' }))).resolves.toBeNull()
    expect(addToast).toHaveBeenLastCalledWith('emoticon.validation.fileSizeExceeded', 'error')
  })

  it('returns a selected thumbnail preview', async () => {
    const { selectThumbnailImage } = useEmoticonImageSelection(t, { addToast })
    const file = new File(['image'], 'thumb.png', { type: 'image/png' })

    await expect(selectThumbnailImage(file)).resolves.toEqual({
      clientId: expect.any(String),
      file,
      preview: 'blob:thumb.png',
      width: 100,
      height: 100,
    })
    expect(addToast).not.toHaveBeenCalled()
  })

  it('collects valid multiple images and reports named failures', async () => {
    const { selectEmoticonImages } = useEmoticonImageSelection(t, { addToast })
    const valid = new File(['image'], 'valid.png', { type: 'image/png' })
    const invalid = new File(['text'], 'note.txt', { type: 'text/plain' })

    await expect(selectEmoticonImages(asFileList([valid, invalid]), 2)).resolves.toEqual([{
      clientId: expect.any(String),
      file: valid,
      preview: 'blob:valid.png',
      width: 100,
      height: 100,
    }])
    expect(addToast).toHaveBeenCalledWith(
      'emoticon.validation.notImage:{"name":"note.txt"}',
      'error'
    )
  })

  it('shows max image toast when no slots remain', async () => {
    const { selectEmoticonImages } = useEmoticonImageSelection(t, { addToast })

    await expect(selectEmoticonImages(asFileList([
      new File(['image'], 'valid.png', { type: 'image/png' })
    ]), 0)).resolves.toEqual([])
    expect(addToast).toHaveBeenCalledWith('emoticon.validation.maxImages', 'error')
    expect(createObjectURL).not.toHaveBeenCalled()
  })
})
