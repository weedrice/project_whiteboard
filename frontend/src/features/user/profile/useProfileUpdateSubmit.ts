import { reactive, ref, type Ref } from 'vue'
import type { AxiosError } from 'axios'
import { fileApi } from '@/api/file'
import { unwrapAxiosApiData } from '@/api/response'
import type { UserUpdatePayload } from '@/api/user'
import { extractErrorMessage, extractValidationErrors, getFieldError } from '@/utils/errorHandler'
import logger from '@/utils/logger'

interface UseProfileUpdateSubmitOptions {
  selectedFile: Ref<File | null>
  removeProfileImage?: Ref<boolean>
  getDisplayName: () => string
  updateProfile: (payload: UserUpdatePayload) => Promise<unknown>
  refreshUser: () => Promise<unknown>
  addToast: (message: string, type: 'success' | 'error') => void
  t: (key: string, params?: Record<string, unknown>) => string
  confirm?: (message: string) => Promise<boolean>
  getProfileImageChangeCost?: () => number
  isProfileImageChangeFree?: () => boolean
  getCurrentPoints?: () => number
  onRefreshed: () => void
  onClose: () => void
}

export function useProfileUpdateSubmit(options: UseProfileUpdateSubmitOptions) {
  const loading = ref(false)
  const errors = reactive<Record<string, string>>({})

  const updateProfile = async () => {
    loading.value = true
    errors.displayName = ''
    errors.profileImage = ''

    try {
      let profileImageId: number | null = null

      if (options.selectedFile.value) {
        if (!await confirmPaidProfileImageChangeIfNeeded()) {
          return
        }

        const uploadRes = await fileApi.uploadFile(options.selectedFile.value)
        const uploadedFile = unwrapAxiosApiData(uploadRes)

        if (!uploadRes.data.success || !uploadedFile?.fileId) {
          options.addToast(options.t('common.messages.uploadFailed'), 'error')
          return
        }

        profileImageId = uploadedFile.fileId
      }

      const payload: UserUpdatePayload = {
        displayName: options.getDisplayName().trim(),
        profileImageId,
      }
      if (options.removeProfileImage?.value) {
        payload.removeProfileImage = true
      }

      const response = await options.updateProfile(payload)
      await options.refreshUser()
      const spentPoints = extractSpentPoints(response)
      if (spentPoints && spentPoints > 0) {
        options.addToast(options.t('user.profile.profileImageCostSpent', { points: spentPoints }), 'success')
      } else {
        options.addToast(options.t('common.messages.profileUpdated'), 'success')
      }
      options.onRefreshed()
      options.onClose()
    } catch (error) {
      const axiosError = error as AxiosError
      logger.error('Failed to update profile:', error)

      const validationErrors = extractValidationErrors(axiosError)
      if (validationErrors) {
        const displayNameError = getFieldError(validationErrors, 'displayName')
        if (displayNameError) {
          errors.displayName = displayNameError
        }

        const otherErrors = Object.entries(validationErrors)
          .filter(([key]) => key !== 'displayName')
          .flatMap(([, messages]) => messages)

        if (otherErrors.length > 0) {
          options.addToast(otherErrors[0], 'error')
        }
      } else {
        const errorMessage = extractErrorMessage(axiosError)
        errors.displayName = errorMessage
        options.addToast(errorMessage, 'error')
      }
    } finally {
      loading.value = false
    }
  }

  const confirmPaidProfileImageChangeIfNeeded = async () => {
    if (options.isProfileImageChangeFree?.()) {
      return true
    }

    const cost = options.getProfileImageChangeCost?.() ?? 0
    const currentPoints = options.getCurrentPoints?.() ?? 0
    if (cost > 0 && currentPoints < cost) {
      errors.profileImage = options.t('user.profile.profileImageInsufficientPoints', {
        current: currentPoints,
        cost,
      })
      options.addToast(errors.profileImage, 'error')
      return false
    }

    if (cost > 0 && options.confirm) {
      return options.confirm(options.t('user.profile.profileImageCostConfirm', { cost }))
    }

    return true
  }

  const extractSpentPoints = (response: unknown): number | null => {
    const apiData = response as { data?: { spentPoints?: unknown }, spentPoints?: unknown }
    const value = apiData?.data?.spentPoints ?? apiData?.spentPoints
    return typeof value === 'number' ? value : null
  }

  return {
    loading,
    errors,
    updateProfile,
  }
}
