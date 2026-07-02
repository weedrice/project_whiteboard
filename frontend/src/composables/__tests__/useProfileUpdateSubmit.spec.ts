import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useProfileUpdateSubmit } from '../useProfileUpdateSubmit'
import { axiosApiSuccess } from '@/test/factories'

const mocks = vi.hoisted(() => ({
  uploadFile: vi.fn(),
  loggerError: vi.fn(),
}))

vi.mock('@/api/file', () => ({
  fileApi: {
    uploadFile: mocks.uploadFile,
  },
}))

vi.mock('@/utils/logger', () => ({
  default: {
    error: mocks.loggerError,
  },
}))

function createSubmitter(options: {
  displayName?: string
  selectedFile?: File | null
} = {}) {
  const addToast = vi.fn()
  const onClose = vi.fn()
  const onRefreshed = vi.fn()
  const refreshUser = vi.fn().mockResolvedValue(undefined)
  const updateProfile = vi.fn().mockResolvedValue(undefined)
  const selectedFile = ref<File | null>(options.selectedFile ?? null)
  const submitter = useProfileUpdateSubmit({
    selectedFile,
    getDisplayName: () => options.displayName ?? '  Display Name  ',
    updateProfile,
    refreshUser,
    addToast,
    t: (key: string) => key,
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
    updateProfile,
  }
}

describe('useProfileUpdateSubmit', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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

    expect(mocks.uploadFile).toHaveBeenCalledWith(file)
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
})
