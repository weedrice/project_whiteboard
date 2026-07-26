import type { ComputedRef, Ref } from 'vue'
import type { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'
import { extractErrorCode, extractErrorMessage } from '@/utils/errorHandler'
import { API_ERROR_CODES } from '@/api/errorCodes'

type EmoticonUploadSession = ReturnType<typeof useEmoticonUploadSession>

interface UseEmoticonSubmitGuardOptions {
  isFormValid: ComputedRef<boolean>
  isSubmitting: Ref<boolean>
  uploadSession: EmoticonUploadSession
  fallbackErrorMessage: string
  onError: (message: string) => void
  onLimitExceeded?: () => void
}

export function useEmoticonSubmitGuard({
  isFormValid,
  isSubmitting,
  uploadSession,
  fallbackErrorMessage,
  onError,
  onLimitExceeded,
}: UseEmoticonSubmitGuardOptions) {
  const runSubmit = async (submit: (runId: number) => Promise<void>) => {
    if (!isFormValid.value || isSubmitting.value) return

    isSubmitting.value = true
    const currentRunId = uploadSession.startSubmitRun()

    try {
      await submit(currentRunId)
    } catch (error: unknown) {
      await uploadSession.discardTrackedUploads(currentRunId)
      const isCurrentSubmit = uploadSession.isSubmitActive(currentRunId)
      if (!uploadSession.isDisposed.value && isCurrentSubmit) {
        if (extractErrorCode(error) === API_ERROR_CODES.EMOTICON_IMAGE_LIMIT_EXCEEDED) {
          onLimitExceeded?.()
        }
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
