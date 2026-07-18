import { ref, watch } from 'vue'
import type { AxiosError } from 'axios'
import { extractErrorMessage, extractValidationErrors, getFieldError } from '@/utils/errorHandler'
import logger from '@/utils/logger'

interface UseAccountDeletionOptions {
  deleteAccount: (password: string) => Promise<unknown>
  logout: () => Promise<void>
  pushHome: () => unknown
  getSessionGeneration: () => number
  t: (key: string) => string
}

export function useAccountDeletion(options: UseAccountDeletionOptions) {
  const showDeleteModal = ref(false)
  const deletePassword = ref('')
  const deleteError = ref('')
  const isDeleting = ref(false)
  let modalRevision = 0

  watch(showDeleteModal, (isOpen) => {
    modalRevision += 1
    if (!isOpen) {
      deletePassword.value = ''
      deleteError.value = ''
      isDeleting.value = false
    }
  }, { flush: 'sync' })

  const handleDeleteAccount = async () => {
    if (isDeleting.value || !showDeleteModal.value) return
    deleteError.value = ''
    if (!deletePassword.value) {
      deleteError.value = options.t('auth.passwordRequired')
      return
    }

    const revision = modalRevision
    const sessionGeneration = options.getSessionGeneration()
    const password = deletePassword.value
    const isCurrentIntent = () => (
      showDeleteModal.value
      && modalRevision === revision
      && options.getSessionGeneration() === sessionGeneration
    )
    isDeleting.value = true

    try {
      await options.deleteAccount(password)
      if (!isCurrentIntent()) return
      showDeleteModal.value = false
      await options.logout()
      await options.pushHome()
    } catch (error: unknown) {
      if (!isCurrentIntent()) return
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
      deleteError.value = errorMessage || options.t('common.messages.error')
    } finally {
      if (isCurrentIntent()) isDeleting.value = false
    }
  }

  return {
    showDeleteModal,
    deletePassword,
    deleteError,
    isDeleting,
    handleDeleteAccount
  }
}
