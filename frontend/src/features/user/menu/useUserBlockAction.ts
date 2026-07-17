import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import logger from '@/utils/logger'
import { getCurrentSessionGeneration } from '@/queryAuthScope'

interface UserBlockActionOptions<TResult> {
  confirmMessage: string
  failureMessage: string
  logMessage: string
  action: () => Promise<TResult>
  successMessage?: string
  isSuccess?: (result: TResult) => boolean
  isIntentCurrent?: () => boolean
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
    isIntentCurrent = () => true,
    logMessage,
    onSuccess,
    successMessage,
  }: UserBlockActionOptions<TResult>) {
    const sessionGeneration = getCurrentSessionGeneration()
    const isCurrent = () => (
      sessionGeneration === getCurrentSessionGeneration()
      && isIntentCurrent()
    )
    const isConfirmed = await confirm(confirmMessage)
    if (!isConfirmed || !isCurrent()) return false

    try {
      const result = await action()
      if (!isCurrent() || !isSuccess(result)) return false

      if (successMessage) {
        toastStore.addToast(successMessage, 'success')
      }
      await onSuccess?.(result)
      return true
    } catch (error: unknown) {
      if (!isCurrent()) return false
      logger.error(logMessage, error)
      toastStore.addToast(failureMessage, 'error')
      return false
    }
  }

  return {
    runUserBlockAction,
  }
}
