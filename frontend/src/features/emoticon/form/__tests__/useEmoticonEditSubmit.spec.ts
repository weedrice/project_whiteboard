import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { QueryClient } from '@tanstack/vue-query'
import { useEmoticonEditSubmit } from '../useEmoticonEditSubmit'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'

const mocks = vi.hoisted(() => ({
  deleteImage: vi.fn(),
  addImage: vi.fn(),
  updateEmoticon: vi.fn(),
  uploadFile: vi.fn(),
  createUploadableEmoticonImageFile: vi.fn(),
  createUploadableEmoticonThumbnailFile: vi.fn(),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    deleteImage: mocks.deleteImage,
    addImage: mocks.addImage,
    updateEmoticon: mocks.updateEmoticon,
  },
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: mocks.uploadFile,
  },
}))

vi.mock('@/utils/emoticonImage', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/emoticonImage')>()

  return {
    ...actual,
    createUploadableEmoticonImageFile: mocks.createUploadableEmoticonImageFile,
    createUploadableEmoticonThumbnailFile: mocks.createUploadableEmoticonThumbnailFile,
  }
})

vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: vi.fn(() => ''),
  extractErrorCode: vi.fn(() => null),
}))

const createUploadSession = () => {
  const isDisposed = computed(() => false)
  let activeRunId = 0

  return {
    uploadProgress: ref({ current: 0, total: 0 }),
    trackedUploadedFileIds: computed(() => []),
    isDisposed,
    startSubmitRun: vi.fn(() => {
      activeRunId += 1
      return activeRunId
    }),
    assertSubmitActive: vi.fn(),
    isSubmitActive: vi.fn((runId?: number) => runId === activeRunId),
    cancelSubmitRun: vi.fn(() => {
      activeRunId = 0
    }),
    resetUploadProgress: vi.fn(),
    setUploadProgress: vi.fn(),
    createUploadController: vi.fn(() => new AbortController()),
    releaseUploadController: vi.fn(),
    abortPendingUploads: vi.fn(),
    createUploadCancelledError: vi.fn(() => new DOMException('cancelled', 'AbortError')),
    isUploadCancelledError: vi.fn((error: unknown) => error instanceof DOMException && error.name === 'AbortError'),
    recordUploadedFile: vi.fn(),
    clearTrackedUploads: vi.fn(),
    discardTrackedUploads: vi.fn().mockResolvedValue(undefined),
  }
}

const createPreview = (fileName: string): EmoticonImagePreview => ({
  clientId: fileName,
  file: new File(['image'], fileName, { type: 'image/png' }),
  preview: `blob:${fileName}`,
  width: 80,
  height: 80,
})

describe('useEmoticonEditSubmit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.deleteImage.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
    mocks.addImage.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
    mocks.updateEmoticon.mockResolvedValue(apiSuccessResponse<() => Promise<unknown>>())
    mocks.createUploadableEmoticonImageFile.mockImplementation((item) => Promise.resolve(item.file))
    mocks.createUploadableEmoticonThumbnailFile.mockImplementation((file) => Promise.resolve(file))
    mocks.uploadFile.mockImplementation((file: File) => Promise.resolve({
      data: {
        data: {
          fileId: file.name === 'thumb.png' ? 10 : 20,
        },
      },
    }))
  })

  it('submits edits and invalidates the same detail and list cache keys', async () => {
    const uploadSession = createUploadSession()
    const invalidateQueries = vi.fn()
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const isSubmitting = ref(false)
    const { handleSubmit } = useEmoticonEditSubmit({
      emoticonId: computed(() => 7),
      isFormValid: computed(() => true),
      isSubmitting,
      thumbnailFile: ref(new File(['thumb'], 'thumb.png', { type: 'image/png' })),
      imagesToDelete: ref([10]),
      newEmoticonPreviews: ref([createPreview('new.png')]),
      emoticonName: ref(' Updated '),
      tags: ref(['fun']),
      uploadSession,
      queryClient: { invalidateQueries } as Partial<QueryClient> as QueryClient,
      fallbackErrorMessage: 'failed',
      onSuccess,
      onError,
    })

    await handleSubmit()

    expect(mocks.deleteImage).not.toHaveBeenCalled()
    expect(mocks.createUploadableEmoticonThumbnailFile).toHaveBeenCalledWith(expect.objectContaining({
      name: 'thumb.png',
    }))
    expect(mocks.uploadFile).toHaveBeenCalledWith(expect.any(File), {
      signal: expect.any(AbortSignal),
      skipGlobalErrorHandler: true,
    })
    expect(mocks.addImage).not.toHaveBeenCalled()
    expect(mocks.updateEmoticon).toHaveBeenCalledWith(7, {
      name: 'Updated',
      thumbnailFileId: 10,
      tags: ['fun'],
      addImageFileIds: [20],
      deleteImageIds: [10],
    }, {
      skipGlobalErrorHandler: true,
    })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticon', 7] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['emoticons'] })
    expect(onSuccess).toHaveBeenCalledTimes(1)
    expect(onError).not.toHaveBeenCalled()
    expect(uploadSession.clearTrackedUploads).toHaveBeenCalledTimes(1)
    expect(isSubmitting.value).toBe(false)
  })

  it('does not delete or update images when new image preparation fails', async () => {
    mocks.createUploadableEmoticonImageFile.mockRejectedValueOnce(new Error('resize failed'))
    const uploadSession = createUploadSession()
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const { handleSubmit } = useEmoticonEditSubmit({
      emoticonId: computed(() => 7),
      isFormValid: computed(() => true),
      isSubmitting: ref(false),
      thumbnailFile: ref(null),
      imagesToDelete: ref([10]),
      newEmoticonPreviews: ref([createPreview('new.png')]),
      emoticonName: ref('Updated'),
      tags: ref(['fun']),
      uploadSession,
      queryClient: { invalidateQueries: vi.fn() } as Partial<QueryClient> as QueryClient,
      fallbackErrorMessage: 'failed',
      onSuccess,
      onError,
    })

    await handleSubmit()

    expect(mocks.deleteImage).not.toHaveBeenCalled()
    expect(mocks.uploadFile).not.toHaveBeenCalled()
    expect(mocks.addImage).not.toHaveBeenCalled()
    expect(mocks.updateEmoticon).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith('failed')
    expect(uploadSession.discardTrackedUploads).toHaveBeenCalledTimes(1)
  })
})
