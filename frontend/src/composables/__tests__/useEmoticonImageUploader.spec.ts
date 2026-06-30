import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEmoticonImageUploader } from '../useEmoticonImageUploader'
import { fileApi } from '@/api/file'
import { apiDataResponse } from '@/test/apiResponseFixtures'
import type { useEmoticonUploadSession } from '../useEmoticonUploadSession'

const createUploadableEmoticonImageFile = vi.hoisted(() => vi.fn())

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: vi.fn()
  }
}))

vi.mock('@/utils/emoticonImage', () => ({
  createUploadableEmoticonImageFile
}))

function createSession() {
  const controller = new AbortController()

  return {
    controller,
    session: {
      assertSubmitActive: vi.fn(),
      createUploadController: vi.fn(() => controller),
      releaseUploadController: vi.fn(),
      abortPendingUploads: vi.fn(),
      createUploadCancelledError: vi.fn(() => new DOMException('cancelled', 'AbortError')),
      isSubmitActive: vi.fn(() => true),
      setUploadProgress: vi.fn(),
      resetUploadProgress: vi.fn()
    } as unknown as ReturnType<typeof useEmoticonUploadSession>
  }
}

describe('useEmoticonImageUploader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    createUploadableEmoticonImageFile.mockImplementation(async (preview) => preview.file)
    vi.mocked(fileApi.uploadFile).mockResolvedValue(
      apiDataResponse<typeof fileApi.uploadFile>({
        fileId: 11
      })
    )
  })

  it('uploads converted previews with shared progress and abort controller handling', async () => {
    const { controller, session } = createSession()
    const uploader = useEmoticonImageUploader(session)
    const file = new File(['image'], 'image.png', { type: 'image/png' })
    const preview = { clientId: 'preview-1', file, preview: 'blob:image', width: 80, height: 80 }

    const result = await uploader.uploadPreviews([preview], 7, { skipGlobalErrorHandler: true })

    expect(result).toEqual([11])
    expect(createUploadableEmoticonImageFile).toHaveBeenCalledWith(preview)
    expect(fileApi.uploadFile).toHaveBeenCalledWith(file, {
      signal: controller.signal,
      skipGlobalErrorHandler: true
    })
    expect(session.setUploadProgress).toHaveBeenNthCalledWith(1, 0, 1)
    expect(session.setUploadProgress).toHaveBeenNthCalledWith(2, 1)
    expect(session.releaseUploadController).toHaveBeenCalledWith(controller)
  })

  it('aborts pending uploads when a preview upload fails', async () => {
    const { session } = createSession()
    const uploader = useEmoticonImageUploader(session)
    const error = new Error('upload failed')
    vi.mocked(fileApi.uploadFile).mockRejectedValueOnce(error)

    await expect(uploader.uploadPreviews([
      { clientId: 'preview-1', file: new File(['image'], 'image.png'), preview: 'blob:image', width: 80, height: 80 }
    ], 7)).rejects.toBe(error)

    expect(session.abortPendingUploads).toHaveBeenCalled()
  })
})
