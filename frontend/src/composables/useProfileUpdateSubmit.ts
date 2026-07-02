import { reactive, ref, type Ref } from 'vue'
import type { AxiosError } from 'axios'
import { fileApi } from '@/api/file'
import { unwrapAxiosApiData } from '@/api/response'
import type { UserUpdatePayload } from '@/api/user'
import { extractErrorMessage, extractValidationErrors, getFieldError } from '@/utils/errorHandler'
import logger from '@/utils/logger'

interface UseProfileUpdateSubmitOptions {
  selectedFile: Ref<File | null>
  getDisplayName: () => string
  updateProfile: (payload: UserUpdatePayload) => Promise<unknown>
  refreshUser: () => Promise<unknown>
  addToast: (message: string, type: 'success' | 'error') => void
  t: (key: string) => string
  onRefreshed: () => void
  onClose: () => void
}

export function useProfileUpdateSubmit(options: UseProfileUpdateSubmitOptions) {
  const loading = ref(false)
  const errors = reactive<Record<string, string>>({})

  const updateProfile = async () => {
    loading.value = true
    errors.displayName = ''

    try {
      let profileImageId: number | null = null

      if (options.selectedFile.value) {
        const uploadRes = await fileApi.uploadFile(options.selectedFile.value)
        const uploadedFile = unwrapAxiosApiData(uploadRes)

        if (!uploadRes.data.success || !uploadedFile?.fileId) {
          options.addToast(options.t('common.messages.uploadFailed'), 'error')
          return
        }

        profileImageId = uploadedFile.fileId
      }

      await options.updateProfile({
        displayName: options.getDisplayName().trim(),
        profileImageId,
      })
      await options.refreshUser()
      options.addToast(options.t('common.messages.profileUpdated'), 'success')
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

  return {
    loading,
    errors,
    updateProfile,
  }
}
