import type { ComputedRef, Ref } from 'vue'
import { emoticonApi } from '@/api/emoticon'
import { useEmoticonImageUploader } from '@/composables/useEmoticonImageUploader'
import type { useEmoticonUploadSession } from '@/composables/useEmoticonUploadSession'
import { extractErrorMessage } from '@/utils/errorHandler'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'

type EmoticonUploadSession = ReturnType<typeof useEmoticonUploadSession>

interface UseEmoticonRegisterSubmitOptions {
  isFormValid: ComputedRef<boolean>
  isSubmitting: Ref<boolean>
  thumbnailFile: Ref<File | null>
  emoticonPreviews: Ref<EmoticonImagePreview[]>
  emoticonName: Ref<string>
  tags: Ref<string[]>
  uploadSession: EmoticonUploadSession
  fallbackErrorMessage: string
  onSuccess: () => void
  onError: (message: string) => void
}

export function useEmoticonRegisterSubmit({
  isFormValid,
  isSubmitting,
  thumbnailFile,
  emoticonPreviews,
  emoticonName,
  tags,
  uploadSession,
  fallbackErrorMessage,
  onSuccess,
  onError,
}: UseEmoticonRegisterSubmitOptions) {
  const imageUploader = useEmoticonImageUploader(uploadSession)

  const handleSubmit = async () => {
    if (!isFormValid.value || isSubmitting.value) return

    isSubmitting.value = true
    const currentRunId = uploadSession.startSubmitRun()

    try {
      const submitSnapshot = {
        thumbnail: thumbnailFile.value!,
        previews: [...emoticonPreviews.value],
        name: emoticonName.value.trim(),
        tags: [...tags.value],
      }

      const [thumbnailFileId, imageFileIds] = await Promise.all([
        imageUploader.uploadFile(submitSnapshot.thumbnail, currentRunId, {
          skipGlobalErrorHandler: true
        }),
        imageUploader.uploadPreviews(submitSnapshot.previews, currentRunId, {
          skipGlobalErrorHandler: true
        })
      ])
      uploadSession.assertSubmitActive(currentRunId)

      await emoticonApi.createEmoticon({
        name: submitSnapshot.name,
        thumbnailFileId,
        tags: submitSnapshot.tags,
        imageFileIds
      })
      uploadSession.assertSubmitActive(currentRunId)

      onSuccess()
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
    handleSubmit,
  }
}
