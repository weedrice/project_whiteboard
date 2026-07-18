import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEmoticonRegisterSubmit } from '../useEmoticonRegisterSubmit'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'
import { emoticonApiData, emoticonApiSuccess } from '@/test/emoticonApiFixtures'
import { createDeferred } from '@/test/async'

const mocks = vi.hoisted(() => ({
  createEmoticon: vi.fn(),
  uploadFile: vi.fn(),
  createUploadableEmoticonImageFile: vi.fn(),
  createUploadableEmoticonThumbnailFile: vi.fn(),
  extractErrorCode: vi.fn(),
}))

vi.mock('@/api/emoticon', () => ({
  emoticonApi: {
    createEmoticon: mocks.createEmoticon,
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
  extractErrorCode: mocks.extractErrorCode,
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

describe('useEmoticonRegisterSubmit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.createEmoticon.mockResolvedValue(emoticonApiSuccess())
    mocks.extractErrorCode.mockReturnValue(null)
    mocks.createUploadableEmoticonImageFile.mockImplementation((item) => Promise.resolve(item.file))
    mocks.createUploadableEmoticonThumbnailFile.mockImplementation((file) => Promise.resolve(file))
    mocks.uploadFile.mockImplementation((file: File) => Promise.resolve(
      emoticonApiData({ fileId: file.name === 'thumb.png' ? 10 : 20 })
    ))
  })

  it('uploads thumbnail and images before creating the emoticon', async () => {
    const uploadSession = createUploadSession()
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const isSubmitting = ref(false)
    const { handleSubmit } = useEmoticonRegisterSubmit({
      isFormValid: computed(() => true),
      isSubmitting,
      thumbnailFile: ref(new File(['thumb'], 'thumb.png', { type: 'image/png' })),
      emoticonPreviews: ref([createPreview('image.png')]),
      emoticonName: ref(' New pack '),
      tags: ref(['fun']),
      uploadSession,
      fallbackErrorMessage: 'failed',
      onSuccess,
      onError,
    })

    await handleSubmit()

    expect(mocks.createUploadableEmoticonThumbnailFile).toHaveBeenCalledWith(expect.objectContaining({
      name: 'thumb.png',
    }))
    expect(mocks.uploadFile).toHaveBeenCalledWith(expect.any(File), {
      signal: expect.any(AbortSignal),
      skipGlobalErrorHandler: true,
    })
    expect(mocks.createEmoticon).toHaveBeenCalledWith({
      name: 'New pack',
      thumbnailFileId: 10,
      tags: ['fun'],
      imageFileIds: [20],
    }, {
      skipGlobalErrorHandler: true,
    })
    expect(onSuccess).toHaveBeenCalledTimes(1)
    expect(onError).not.toHaveBeenCalled()
    expect(uploadSession.clearTrackedUploads).toHaveBeenCalledTimes(1)
    expect(isSubmitting.value).toBe(false)
  })

  it('does not create the emoticon when image preparation fails', async () => {
    mocks.createUploadableEmoticonImageFile.mockRejectedValueOnce(new Error('resize failed'))
    const uploadSession = createUploadSession()
    const onSuccess = vi.fn()
    const onError = vi.fn()
    const { handleSubmit } = useEmoticonRegisterSubmit({
      isFormValid: computed(() => true),
      isSubmitting: ref(false),
      thumbnailFile: ref(new File(['thumb'], 'thumb.png', { type: 'image/png' })),
      emoticonPreviews: ref([createPreview('image.png')]),
      emoticonName: ref('New pack'),
      tags: ref(['fun']),
      uploadSession,
      fallbackErrorMessage: 'failed',
      onSuccess,
      onError,
    })

    await handleSubmit()

    expect(mocks.createEmoticon).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).toHaveBeenCalledWith('failed')
    expect(uploadSession.discardTrackedUploads).toHaveBeenCalledTimes(1)
  })

  it('waits for a late image upload success before discarding after thumbnail failure', async () => {
    const thumbnailError = new Error('thumbnail upload failed')
    const lateImageUpload = createDeferred<ReturnType<typeof emoticonApiData<{ fileId: number }>>>()
    mocks.uploadFile.mockImplementation((file: File) => (
      file.name === 'thumb.png'
        ? Promise.reject(thumbnailError)
        : lateImageUpload.promise
    ))
    const uploadSession = createUploadSession()
    const { handleSubmit } = useEmoticonRegisterSubmit({
      isFormValid: computed(() => true),
      isSubmitting: ref(false),
      thumbnailFile: ref(new File(['thumb'], 'thumb.png', { type: 'image/png' })),
      emoticonPreviews: ref([createPreview('image.png')]),
      emoticonName: ref('New pack'),
      tags: ref([]),
      uploadSession,
      fallbackErrorMessage: 'failed',
      onSuccess: vi.fn(),
      onError: vi.fn(),
    })

    const submitPromise = handleSubmit()
    await vi.waitFor(() => expect(mocks.uploadFile).toHaveBeenCalledTimes(2))
    expect(uploadSession.discardTrackedUploads).not.toHaveBeenCalled()

    lateImageUpload.resolve(emoticonApiData({ fileId: 20 }))
    await submitPromise

    expect(uploadSession.recordUploadedFile).toHaveBeenCalledWith(20, 1)
    expect(uploadSession.discardTrackedUploads).toHaveBeenCalledTimes(1)
    expect(mocks.createEmoticon).not.toHaveBeenCalled()
  })

  it('refreshes the image policy when the server returns EM006', async () => {
    mocks.createEmoticon.mockRejectedValueOnce(new Error('limit exceeded'))
    mocks.extractErrorCode.mockReturnValueOnce('EM006')
    const uploadSession = createUploadSession()
    const onLimitExceeded = vi.fn()
    const { handleSubmit } = useEmoticonRegisterSubmit({
      isFormValid: computed(() => true),
      isSubmitting: ref(false),
      thumbnailFile: ref(new File(['thumb'], 'thumb.png', { type: 'image/png' })),
      emoticonPreviews: ref([createPreview('image.png')]),
      emoticonName: ref('New pack'),
      tags: ref([]),
      uploadSession,
      fallbackErrorMessage: 'failed',
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onLimitExceeded,
    })

    await handleSubmit()

    expect(onLimitExceeded).toHaveBeenCalledTimes(1)
    expect(uploadSession.discardTrackedUploads).toHaveBeenCalledTimes(1)
  })
})
