import type { ComputedRef, Ref } from 'vue'
import { emoticonApi } from '@/api/emoticon'
import { useEmoticonImageUploader } from '@/composables/useEmoticonImageUploader'
import { useEmoticonSubmitGuard } from '@/composables/useEmoticonSubmitGuard'
import type { useEmoticonUploadSession } from '@/composables/useEmoticonUploadSession'
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
  const { runSubmit } = useEmoticonSubmitGuard({
    isFormValid,
    isSubmitting,
    uploadSession,
    fallbackErrorMessage,
    onError,
  })

  const handleSubmit = () => runSubmit(async (currentRunId) => {
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
    }, {
      skipGlobalErrorHandler: true
    })
    uploadSession.assertSubmitActive(currentRunId)

    onSuccess()
  })

  return {
    handleSubmit,
  }
}
