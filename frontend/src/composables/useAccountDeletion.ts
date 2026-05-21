import { ref } from 'vue'
import type { AxiosError } from 'axios'
import { extractErrorMessage, extractValidationErrors, getFieldError } from '@/utils/errorHandler'
import logger from '@/utils/logger'

interface UseAccountDeletionOptions {
  deleteAccount: (password: string) => Promise<unknown>
  logout: () => Promise<void>
  pushHome: () => unknown
  t: (key: string) => string
}

export function useAccountDeletion(options: UseAccountDeletionOptions) {
  const showDeleteModal = ref(false)
  const deletePassword = ref('')
  const deleteError = ref('')

  const handleDeleteAccount = async () => {
    deleteError.value = ''
    if (!deletePassword.value) {
      deleteError.value = options.t('auth.passwordRequired')
      return
    }

    try {
      await options.deleteAccount(deletePassword.value)
      showDeleteModal.value = false
      await options.logout()
      await options.pushHome()
    } catch (error: unknown) {
      logger.error('Failed to delete account:', error)
      const axiosError = error as AxiosError
      const validationErrors = extractValidationErrors(axiosError)

      if (validationErrors) {
        const passwordError = getFieldError(validationErrors, 'password')
        if (passwordError) {
          deleteError.value = passwordError
          return
        }
      }

      const errorMessage = extractErrorMessage(axiosError)
      deleteError.value = errorMessage || options.t('common.errorOccurred')
    }
  }

  return {
    showDeleteModal,
    deletePassword,
    deleteError,
    handleDeleteAccount
  }
}
