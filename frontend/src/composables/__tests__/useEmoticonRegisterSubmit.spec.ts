import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useEmoticonRegisterSubmit } from '../useEmoticonRegisterSubmit'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'

const mocks = vi.hoisted(() => ({
  createEmoticon: vi.fn(),
  uploadFile: vi.fn(),
  createUploadableEmoticonImageFile: vi.fn(),
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
  }
})

vi.mock('@/utils/errorHandler', () => ({
  extractErrorMessage: vi.fn(() => ''),
}))

const createUploadSession = () => {
  const isDisposed = computed(() => false)
  let activeRunId = 0

  return {
    uploadProgress: ref({ current: 0, total: 0 }),
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
    mocks.createEmoticon.mockResolvedValue({ data: { success: true } })
    mocks.createUploadableEmoticonImageFile.mockImplementation((item) => Promise.resolve(item.file))
    mocks.uploadFile.mockImplementation((file: File) => Promise.resolve({
      data: {
        data: {
          fileId: file.name === 'thumb.png' ? 10 : 20,
        },
      },
    }))
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
  })
})
