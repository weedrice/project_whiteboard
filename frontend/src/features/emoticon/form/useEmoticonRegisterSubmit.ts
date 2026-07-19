import type { ComputedRef, Ref } from 'vue'
import { emoticonApi } from '@/api/emoticon'
import { useEmoticonImageUploader } from '@/features/emoticon/form/useEmoticonImageUploader'
import { useEmoticonSubmitGuard } from '@/features/emoticon/form/useEmoticonSubmitGuard'
import type { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'
import { createUploadableEmoticonThumbnailFile } from '@/utils/emoticonImage'
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
  onLimitExceeded?: () => void
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
  onLimitExceeded,
}: UseEmoticonRegisterSubmitOptions) {
  const imageUploader = useEmoticonImageUploader(uploadSession)
  const { runSubmit } = useEmoticonSubmitGuard({
    isFormValid,
    isSubmitting,
    uploadSession,
    fallbackErrorMessage,
    onError,
    onLimitExceeded,
  })

  const handleSubmit = () => runSubmit(async (currentRunId) => {
    const uploadableThumbnail = await createUploadableEmoticonThumbnailFile(thumbnailFile.value!)
    uploadSession.assertSubmitActive(currentRunId)

    const submitSnapshot = {
      thumbnail: uploadableThumbnail,
      previews: [...emoticonPreviews.value],
      name: emoticonName.value.trim(),
      tags: [...tags.value],
    }

    const [thumbnailUpload, imageUploads] = await Promise.allSettled([
      imageUploader.uploadFile(submitSnapshot.thumbnail, currentRunId, {
        skipGlobalErrorHandler: true
      }),
      imageUploader.uploadPreviews(submitSnapshot.previews, currentRunId, {
        skipGlobalErrorHandler: true
      })
    ])
    if (thumbnailUpload.status === 'rejected') {
      throw thumbnailUpload.reason
    }
    if (imageUploads.status === 'rejected') {
      throw imageUploads.reason
    }
    const thumbnailFileId = thumbnailUpload.value
    const imageFileIds = imageUploads.value
    uploadSession.assertSubmitActive(currentRunId)

    uploadSession.beginFinalRequest(currentRunId)
    try {
      await emoticonApi.createEmoticon({
        name: submitSnapshot.name,
        thumbnailFileId,
        tags: submitSnapshot.tags,
        imageFileIds
      }, {
        skipGlobalErrorHandler: true
      })
      uploadSession.clearTrackedUploads(currentRunId)
      if (uploadSession.isSubmitActive(currentRunId)) onSuccess()
    } finally {
      uploadSession.finishFinalRequest(currentRunId)
    }
  })

  return {
    handleSubmit,
  }
}
