import type { ComputedRef, Ref } from 'vue'
import type { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'
import { extractErrorMessage } from '@/utils/errorHandler'

type EmoticonUploadSession = ReturnType<typeof useEmoticonUploadSession>

interface UseEmoticonSubmitGuardOptions {
  isFormValid: ComputedRef<boolean>
  isSubmitting: Ref<boolean>
  uploadSession: EmoticonUploadSession
  fallbackErrorMessage: string
  onError: (message: string) => void
}

export function useEmoticonSubmitGuard({
  isFormValid,
  isSubmitting,
  uploadSession,
  fallbackErrorMessage,
  onError,
}: UseEmoticonSubmitGuardOptions) {
  const runSubmit = async (submit: (runId: number) => Promise<void>) => {
    if (!isFormValid.value || isSubmitting.value) return

    isSubmitting.value = true
    const currentRunId = uploadSession.startSubmitRun()

    try {
      await submit(currentRunId)
    } catch (error: unknown) {
      const isStaleCancellation = !uploadSession.isSubmitActive(currentRunId)
        && uploadSession.isUploadCancelledError(error)
      if (!uploadSession.isDisposed.value && !isStaleCancellation) {
        onError(extractErrorMessage(error) || fallbackErrorMessage)
      }
    } finally {
      if (uploadSession.isSubmitActive(currentRunId)) {
        isSubmitting.value = false
        uploadSession.cancelSubmitRun()
        uploadSession.resetUploadProgress()
      }
    }
  }

  return {
    runSubmit,
  }
}
