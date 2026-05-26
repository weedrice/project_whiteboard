import type { ComputedRef, Ref } from 'vue'
import type { QueryClient } from '@tanstack/vue-query'
import { emoticonApi } from '@/api/emoticon'
import { fileApi } from '@/api/file'
import { extractErrorMessage } from '@/utils/errorHandler'
import {
  createUploadableEmoticonImageFile,
  type EmoticonImagePreview
} from '@/utils/emoticonImage'
import type { useEmoticonUploadSession } from '@/composables/useEmoticonUploadSession'

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
  const handleSubmit = async () => {
    if (!isFormValid.value || isSubmitting.value) return

    isSubmitting.value = true
    const currentRunId = uploadSession.startSubmitRun()

    try {
      const submitSnapshot = {
        emoticonId: emoticonId.value,
        thumbnail: thumbnailFile.value,
        imagesToDelete: [...imagesToDelete.value],
        newPreviews: [...newEmoticonPreviews.value],
        name: emoticonName.value.trim(),
        tags: [...tags.value],
      }
      const uploadFiles = submitSnapshot.newPreviews.length > 0
        ? await Promise.all(submitSnapshot.newPreviews.map((item) => createUploadableEmoticonImageFile(item)))
        : []
      uploadSession.assertSubmitActive(currentRunId)

      let thumbnailFileId: number | undefined
      if (submitSnapshot.thumbnail) {
        const controller = uploadSession.createUploadController()

        try {
          const thumbnailResponse = await fileApi.uploadFile(submitSnapshot.thumbnail, { signal: controller.signal })
          uploadSession.assertSubmitActive(currentRunId)
          thumbnailFileId = thumbnailResponse.data.data.fileId
        } finally {
          uploadSession.releaseUploadController(controller)
        }
      }

      await Promise.all(submitSnapshot.imagesToDelete.map(async (imageId) => {
        await emoticonApi.deleteImage(imageId)
        uploadSession.assertSubmitActive(currentRunId)
      }))

      if (uploadFiles.length > 0) {
        uploadSession.setUploadProgress(0, uploadFiles.length)

        let uploadFailed = false

        const imageFileIds = await (async () => {
          try {
            let completed = 0

            return await Promise.all(
              uploadFiles.map(async (uploadFile) => {
                if (uploadFailed) {
                  throw uploadSession.createUploadCancelledError()
                }

                uploadSession.assertSubmitActive(currentRunId)
                const controller = uploadSession.createUploadController()

                try {
                  const response = await fileApi.uploadFile(uploadFile, { signal: controller.signal })
                  uploadSession.assertSubmitActive(currentRunId)
                  completed += 1
                  if (uploadSession.isSubmitActive(currentRunId)) {
                    uploadSession.setUploadProgress(completed)
                  }
                  return response.data.data.fileId
                } catch (error) {
                  uploadFailed = true
                  uploadSession.abortPendingUploads()
                  throw error
                } finally {
                  uploadSession.releaseUploadController(controller)
                }
              })
            )
          } catch (error) {
            uploadFailed = true
            uploadSession.abortPendingUploads()
            throw error
          }
        })()

        await Promise.all(imageFileIds.map(async (fileId) => {
          uploadSession.assertSubmitActive(currentRunId)
          await emoticonApi.addImage(submitSnapshot.emoticonId, fileId)
          uploadSession.assertSubmitActive(currentRunId)
        }))
      }

      uploadSession.assertSubmitActive(currentRunId)
      await emoticonApi.updateEmoticon(submitSnapshot.emoticonId, {
        name: submitSnapshot.name,
        thumbnailFileId,
        tags: submitSnapshot.tags
      })
      uploadSession.assertSubmitActive(currentRunId)

      queryClient.invalidateQueries({ queryKey: ['emoticon', emoticonId] })
      queryClient.invalidateQueries({ queryKey: ['emoticons'] })

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
