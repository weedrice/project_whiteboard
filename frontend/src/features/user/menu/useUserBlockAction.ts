import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'

interface UserBlockActionOptions<TResult> {
  confirmMessage: string
  failureMessage: string
  logMessage: string
  action: () => Promise<TResult>
  successMessage?: string
  isSuccess?: (result: TResult) => boolean
  onSuccess?: (result: TResult) => void | Promise<void>
}

export function useUserBlockAction() {
  const toastStore = useToastStore()
  const { confirm } = useConfirm()

  async function runUserBlockAction<TResult>({
    action,
    confirmMessage,
    failureMessage,
    isSuccess = () => true,
    logMessage,
    onSuccess,
    successMessage,
  }: UserBlockActionOptions<TResult>) {
    const isConfirmed = await confirm(confirmMessage)
    if (!isConfirmed) return false

    try {
      const result = await action()
      if (!isSuccess(result)) return false

      if (successMessage) {
        toastStore.addToast(successMessage, 'success')
      }
      await onSuccess?.(result)
      return true
    } catch (error: unknown) {
      logger.error(logMessage, error)
      toastStore.addToast(failureMessage, 'error')
      return false
    }
  }

  return {
    runUserBlockAction,
  }
}
