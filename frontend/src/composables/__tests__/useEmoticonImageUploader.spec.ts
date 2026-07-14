import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEmoticonImageUploader } from '../useEmoticonImageUploader'
import { fileApi } from '@/api/file'
import { apiDataResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'
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

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
  await new Promise((resolve) => setTimeout(resolve, 0))
}

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
      resetUploadProgress: vi.fn(),
      recordUploadedFile: vi.fn()
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
    expect(session.recordUploadedFile).toHaveBeenCalledWith(11)
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

  it('limits concurrent preview file preparation', async () => {
    const { session } = createSession()
    const uploader = useEmoticonImageUploader(session)
    const previews = Array.from({ length: 5 }, (_, index) => ({
      clientId: `preview-${index}`,
      file: new File([`image-${index}`], `image-${index}.png`),
      preview: `blob:image-${index}`,
      width: 80,
      height: 80
    }))
    const deferreds = previews.map(() => createDeferred<File>())
    const started: string[] = []
    let activeCount = 0
    let maxActiveCount = 0

    createUploadableEmoticonImageFile.mockImplementation(async (preview) => {
      const index = Number(preview.clientId.replace('preview-', ''))
      started.push(preview.clientId)
      activeCount += 1
      maxActiveCount = Math.max(maxActiveCount, activeCount)

      try {
        return await deferreds[index].promise
      } finally {
        activeCount -= 1
      }
    })

    const resultPromise = uploader.preparePreviewFiles(previews)

    await flushPromises()

    expect(started).toEqual(['preview-0', 'preview-1', 'preview-2'])
    expect(maxActiveCount).toBe(3)

    deferreds[0].resolve(previews[0].file)
    await flushPromises()
    expect(started).toEqual(['preview-0', 'preview-1', 'preview-2', 'preview-3'])

    deferreds[1].resolve(previews[1].file)
    await flushPromises()
    expect(started).toEqual(['preview-0', 'preview-1', 'preview-2', 'preview-3', 'preview-4'])

    deferreds[2].resolve(previews[2].file)
    deferreds[3].resolve(previews[3].file)
    deferreds[4].resolve(previews[4].file)

    await expect(resultPromise).resolves.toEqual(previews.map((preview) => preview.file))
    expect(maxActiveCount).toBe(3)
  })

  it('limits concurrent uploads and stops queued files after a failure', async () => {
    const { session } = createSession()
    const uploader = useEmoticonImageUploader(session)
    const files = Array.from({ length: 5 }, (_, index) => (
      new File([`image-${index}`], `image-${index}.png`)
    ))
    const deferreds = files.map(() => (
      createDeferred<Awaited<ReturnType<typeof fileApi.uploadFile>>>()
    ))
    const started: string[] = []
    const error = new Error('upload failed')
    let activeCount = 0
    let maxActiveCount = 0

    vi.mocked(fileApi.uploadFile).mockImplementation(async (file) => {
      const index = Number(file.name.replace('image-', '').replace('.png', ''))
      started.push(file.name)
      activeCount += 1
      maxActiveCount = Math.max(maxActiveCount, activeCount)

      try {
        return await deferreds[index].promise
      } finally {
        activeCount -= 1
      }
    })

    const resultPromise = uploader.uploadFiles(files, 7)

    await flushPromises()

    expect(started).toEqual(['image-0.png', 'image-1.png', 'image-2.png'])
    expect(maxActiveCount).toBe(3)

    deferreds[0].reject(error)
    await flushPromises()

    expect(started).toEqual(['image-0.png', 'image-1.png', 'image-2.png'])
    expect(fileApi.uploadFile).toHaveBeenCalledTimes(3)

    deferreds[1].resolve(apiDataResponse<typeof fileApi.uploadFile>({ fileId: 21 }))
    deferreds[2].resolve(apiDataResponse<typeof fileApi.uploadFile>({ fileId: 22 }))

    await expect(resultPromise).rejects.toBe(error)
    expect(started).toEqual(['image-0.png', 'image-1.png', 'image-2.png'])
    expect(fileApi.uploadFile).toHaveBeenCalledTimes(3)
    expect(session.abortPendingUploads).toHaveBeenCalled()
    expect(session.recordUploadedFile).toHaveBeenCalledWith(21)
    expect(session.recordUploadedFile).toHaveBeenCalledWith(22)
    expect(maxActiveCount).toBe(3)
  })
})
