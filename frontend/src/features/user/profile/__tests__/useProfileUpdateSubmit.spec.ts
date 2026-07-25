import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useProfileUpdateSubmit } from '../useProfileUpdateSubmit'
import { axiosApiSuccess } from '@/test/factories'
import { createDeferred } from '@/test/async'
import { FILE_UPLOAD_TARGETS } from '@/api/fileUploadTargets'

const mocks = vi.hoisted(() => ({
  uploadFile: vi.fn(),
  discardUploads: vi.fn(),
  loggerError: vi.fn(),
  loggerWarn: vi.fn(),
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: mocks.uploadFile,
    discardUploads: mocks.discardUploads,
  },
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: mocks.loggerError,
    warn: mocks.loggerWarn,
  },
}))

function createSubmitter(options: {
  displayName?: string
  selectedFile?: File | null
  profileImageChangeCost?: number
  profileImageChangeFree?: boolean
  currentPoints?: number
  updateResponse?: unknown
} = {}) {
  const addToast = vi.fn()
  const onClose = vi.fn()
  const onRefreshed = vi.fn()
  const refreshUser = vi.fn().mockResolvedValue(undefined)
  const updateProfile = vi.fn().mockResolvedValue(options.updateResponse)
  const t = vi.fn((key: string) => key)
  const selectedFile = ref<File | null>(options.selectedFile ?? null)
  const authSession = { sessionGeneration: 1, user: { userId: 7 } }
  const submitter = useProfileUpdateSubmit({
    selectedFile,
    getDisplayName: () => options.displayName ?? '  Display Name  ',
    updateProfile,
    refreshUser,
    addToast,
    t,
    confirm: vi.fn().mockResolvedValue(true),
    getProfileImageChangeCost: () => options.profileImageChangeCost ?? 0,
    isProfileImageChangeFree: () => options.profileImageChangeFree ?? true,
    getCurrentPoints: () => options.currentPoints ?? 0,
    authSession,
    onRefreshed,
    onClose,
  })

  return {
    addToast,
    onClose,
    onRefreshed,
    refreshUser,
    selectedFile,
    submitter,
    t,
    updateProfile,
    authSession,
  }
}

describe('useProfileUpdateSubmit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.discardUploads.mockResolvedValue(axiosApiSuccess({ discardedCount: 1 }))
  })

  it('submits a trimmed display name without a profile image', async () => {
    const { onClose, onRefreshed, refreshUser, submitter, updateProfile } = createSubmitter()

    await submitter.updateProfile()

    expect(updateProfile).toHaveBeenCalledWith({
      displayName: 'Display Name',
      profileImageId: null,
    })
    expect(refreshUser).toHaveBeenCalled()
    expect(onRefreshed).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
    expect(submitter.loading.value).toBe(false)
  })

  it('uploads the selected profile image before updating profile', async () => {
    const file = new File(['profile'], 'profile.png', { type: 'image/png' })
    const { submitter, updateProfile } = createSubmitter({ selectedFile: file })
    mocks.uploadFile.mockResolvedValueOnce(axiosApiSuccess({ fileId: 123 }))

    await submitter.updateProfile()

    expect(mocks.uploadFile).toHaveBeenCalledWith(
        file,
        { signal: expect.any(AbortSignal) },
        FILE_UPLOAD_TARGETS.PROFILE_IMAGE,
    )
    expect(updateProfile).toHaveBeenCalledWith({
      displayName: 'Display Name',
      profileImageId: 123,
    })
  })

  it('stops update when image upload does not return a file id', async () => {
    const file = new File(['profile'], 'profile.png', { type: 'image/png' })
    const { addToast, submitter, updateProfile } = createSubmitter({ selectedFile: file })
    mocks.uploadFile.mockResolvedValueOnce(axiosApiSuccess({ fileId: null }))

    await submitter.updateProfile()

    expect(updateProfile).not.toHaveBeenCalled()
    expect(addToast).toHaveBeenCalledWith('common.messages.uploadFailed', 'error')
    expect(submitter.loading.value).toBe(false)
  })

  it('passes point values to the insufficient-points translation', async () => {
    const file = new File(['profile'], 'profile.png', { type: 'image/png' })
    const { submitter, t, updateProfile } = createSubmitter({
      selectedFile: file,
      profileImageChangeCost: 1000,
      profileImageChangeFree: false,
      currentPoints: 300,
    })

    await submitter.updateProfile()

    expect(t).toHaveBeenCalledWith('user.profile.profileImageInsufficientPoints', {
      current: 300,
      cost: 1000,
    })
    expect(updateProfile).not.toHaveBeenCalled()
  })

  it('passes spent points to the success translation', async () => {
    const file = new File(['profile'], 'profile.png', { type: 'image/png' })
    const { submitter, t } = createSubmitter({
      selectedFile: file,
      updateResponse: { data: { spentPoints: 1000 } },
    })
    mocks.uploadFile.mockResolvedValueOnce(axiosApiSuccess({ fileId: 123 }))

    await submitter.updateProfile()

    expect(t).toHaveBeenCalledWith('user.profile.profileImageCostSpent', { points: 1000 })
  })

  it('discards an uploaded image when the session changes before profile update', async () => {
    const file = new File(['profile'], 'profile.png', { type: 'image/png' })
    const uploadResponse = axiosApiSuccess({ fileId: 456 })
    const upload = createDeferred<typeof uploadResponse>()
    mocks.uploadFile.mockReturnValueOnce(upload.promise)
    const { authSession, submitter, updateProfile } = createSubmitter({ selectedFile: file })

    const submission = submitter.updateProfile()
    await vi.waitFor(() => expect(mocks.uploadFile).toHaveBeenCalledTimes(1))
    authSession.sessionGeneration += 1
    upload.resolve(uploadResponse)
    await submission

    expect(updateProfile).not.toHaveBeenCalled()
    expect(mocks.discardUploads).toHaveBeenCalledWith([456], {
      skipAuthRefresh: true,
      skipGlobalErrorHandler: true,
    })
  })

  it('reloads the profile and asks for retry on a concurrent modification conflict', async () => {
    const { addToast, refreshUser, submitter, updateProfile } = createSubmitter()
    updateProfile.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 409, data: { error: { code: 'C012' } } },
    })

    await submitter.updateProfile()

    expect(refreshUser).toHaveBeenCalledTimes(1)
    expect(addToast).toHaveBeenCalledWith('common.messages.concurrentModification', 'warning')
  })
})
