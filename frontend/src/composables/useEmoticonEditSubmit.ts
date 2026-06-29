import type { ComputedRef, Ref } from 'vue'
import type { QueryClient } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { createUploadableEmoticonThumbnailFile } from '@/utils/emoticonImage'
import type { EmoticonImagePreview } from '@/utils/emoticonImage'
import { useEmoticonImageUploader } from '@/composables/useEmoticonImageUploader'
import { useEmoticonSubmitGuard } from '@/composables/useEmoticonSubmitGuard'
import type { useEmoticonUploadSession } from '@/composables/useEmoticonUploadSession'
import { emoticonDetailQueryKey, emoticonListQueryKey } from '@/composables/useEmoticonEditResource'

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
  onSuccess: () => void
  onError: (message: string) => void
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
}: UseEmoticonEditSubmitOptions) {
  const imageUploader = useEmoticonImageUploader(uploadSession)
  const skipGlobalErrorHandler = { skipGlobalErrorHandler: true }
  const { runSubmit } = useEmoticonSubmitGuard({
    isFormValid,
    isSubmitting,
    uploadSession,
    fallbackErrorMessage,
    onError,
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

    await Promise.all(submitSnapshot.imagesToDelete.map(async (imageId) => {
      await emoticonApi.deleteImage(imageId, skipGlobalErrorHandler)
      uploadSession.assertSubmitActive(currentRunId)
    }))

    if (uploadFiles.length > 0) {
      const imageFileIds = await imageUploader.uploadFiles(
        uploadFiles,
        currentRunId,
        skipGlobalErrorHandler
      )
      await Promise.all(imageFileIds.map(async (fileId) => {
        uploadSession.assertSubmitActive(currentRunId)
        await emoticonApi.addImage(submitSnapshot.emoticonId, fileId, skipGlobalErrorHandler)
        uploadSession.assertSubmitActive(currentRunId)
      }))
    }

    uploadSession.assertSubmitActive(currentRunId)
    await emoticonApi.updateEmoticon(submitSnapshot.emoticonId, {
      name: submitSnapshot.name,
      thumbnailFileId,
      tags: submitSnapshot.tags
    }, {
      skipGlobalErrorHandler: true
    })
    uploadSession.assertSubmitActive(currentRunId)

    queryClient.invalidateQueries({ queryKey: emoticonDetailQueryKey(emoticonId) })
    queryClient.invalidateQueries({ queryKey: emoticonListQueryKey })

    onSuccess()
  })

  return {
    handleSubmit,
  }
}
