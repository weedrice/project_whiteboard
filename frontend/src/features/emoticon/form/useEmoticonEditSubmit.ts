import type { ComputedRef, Ref } from 'vue'
import type { QueryClient } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { createUploadableEmoticonThumbnailFile } from '@/utils/emoticonImage'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'
import { useEmoticonImageUploader } from '@/features/emoticon/form/useEmoticonImageUploader'
import { useEmoticonSubmitGuard } from '@/features/emoticon/form/useEmoticonSubmitGuard'
import type { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'
import { emoticonQueryKeys } from '@/features/emoticon/emoticonQueryKeys'

type EmoticonUploadSession = ReturnType<typeof useEmoticonUploadSession>

interface UseEmoticonEditSubmitOptions {
  emoticonId: ComputedRef<number>
  isFormValid: ComputedRef<boolean>
  isSubmitting: Ref<boolean>
  thumbnailFile: Ref<File | null>
  imagesToDelete: Ref<number[]>
  newEmoticonPreviews: Ref<EmoticonImagePreview[]>
  emoticonName: Ref<string>
  tags: Ref<string[]>
  uploadSession: EmoticonUploadSession
  queryClient: QueryClient
  fallbackErrorMessage: string
  onSuccess: (emoticonId: number) => void
  onError: (message: string) => void
  onLimitExceeded?: () => void
}

export function useEmoticonEditSubmit({
  emoticonId,
  isFormValid,
  isSubmitting,
  thumbnailFile,
  imagesToDelete,
  newEmoticonPreviews,
  emoticonName,
  tags,
  uploadSession,
  queryClient,
  fallbackErrorMessage,
  onSuccess,
  onError,
  onLimitExceeded,
}: UseEmoticonEditSubmitOptions) {
  const imageUploader = useEmoticonImageUploader(uploadSession)
  const skipGlobalErrorHandler = { skipGlobalErrorHandler: true }
  const { runSubmit } = useEmoticonSubmitGuard({
    isFormValid,
    isSubmitting,
    uploadSession,
    fallbackErrorMessage,
    onError,
    onLimitExceeded,
  })

  const handleSubmit = () => runSubmit(async (currentRunId) => {
    const submitSnapshot = {
      emoticonId: emoticonId.value,
      thumbnail: thumbnailFile.value,
      imagesToDelete: [...imagesToDelete.value],
      newPreviews: [...newEmoticonPreviews.value],
      name: emoticonName.value.trim(),
      tags: [...tags.value],
    }
    const uploadFiles = await imageUploader.preparePreviewFiles(submitSnapshot.newPreviews)
    uploadSession.assertSubmitActive(currentRunId)

    let thumbnailFileId: number | undefined
    if (submitSnapshot.thumbnail) {
      const uploadableThumbnail = await createUploadableEmoticonThumbnailFile(submitSnapshot.thumbnail)
      uploadSession.assertSubmitActive(currentRunId)
      thumbnailFileId = await imageUploader.uploadFile(
        uploadableThumbnail,
        currentRunId,
        skipGlobalErrorHandler
      )
    }

    let imageFileIds: number[] = []
    if (uploadFiles.length > 0) {
      imageFileIds = await imageUploader.uploadFiles(
        uploadFiles,
        currentRunId,
        skipGlobalErrorHandler
      )
    }

    uploadSession.assertSubmitActive(currentRunId)
    await emoticonApi.updateEmoticon(submitSnapshot.emoticonId, {
      name: submitSnapshot.name,
      thumbnailFileId,
      tags: submitSnapshot.tags,
      addImageFileIds: imageFileIds,
      deleteImageIds: submitSnapshot.imagesToDelete,
    }, {
      skipGlobalErrorHandler: true
    })
    uploadSession.assertSubmitActive(currentRunId)
    uploadSession.clearTrackedUploads(currentRunId)

    queryClient.invalidateQueries({ queryKey: emoticonQueryKeys.detail(submitSnapshot.emoticonId) })
    queryClient.invalidateQueries({ queryKey: emoticonQueryKeys.listRoot })

    onSuccess(submitSnapshot.emoticonId)
  })

  return {
    handleSubmit,
  }
}
