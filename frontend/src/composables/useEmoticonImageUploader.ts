import { fileApi } from '@/api/file'
import { unwrapAxiosApiData } from '@/api/response'
import {
  createUploadableEmoticonImageFile,
  type EmoticonImagePreview
} from '@/utils/emoticonImage'
import type { useEmoticonUploadSession } from '@/composables/useEmoticonUploadSession'

type EmoticonUploadSession = ReturnType<typeof useEmoticonUploadSession>

type UploadOptions = {
  skipGlobalErrorHandler?: boolean
}

export function useEmoticonImageUploader(uploadSession: EmoticonUploadSession) {
  async function preparePreviewFiles(previews: EmoticonImagePreview[]) {
    return Promise.all(previews.map((preview) => createUploadableEmoticonImageFile(preview)))
  }

  async function uploadFile(file: File, runId: number, options: UploadOptions = {}) {
    uploadSession.assertSubmitActive(runId)
    const controller = uploadSession.createUploadController()

    try {
      const response = await fileApi.uploadFile(file, {
        signal: controller.signal,
        skipGlobalErrorHandler: options.skipGlobalErrorHandler
      })
      uploadSession.assertSubmitActive(runId)
      return unwrapAxiosApiData(response).fileId
    } catch (error) {
      uploadSession.abortPendingUploads()
      throw error
    } finally {
      uploadSession.releaseUploadController(controller)
    }
  }

  async function uploadPreviews(
    previews: EmoticonImagePreview[],
    runId: number,
    options: UploadOptions = {}
  ) {
    const files = await preparePreviewFiles(previews)
    uploadSession.assertSubmitActive(runId)
    return uploadFiles(files, runId, options)
  }

  async function uploadFiles(
    files: File[],
    runId: number,
    options: UploadOptions = {}
  ) {
    if (files.length === 0) {
      uploadSession.resetUploadProgress()
      return []
    }

    uploadSession.setUploadProgress(0, files.length)
    let uploadFailed = false
    let completed = 0

    return Promise.all(files.map(async (file) => {
      if (uploadFailed) {
        throw uploadSession.createUploadCancelledError()
      }

      uploadSession.assertSubmitActive(runId)

      try {
        const fileId = await uploadFile(file, runId, options)
        completed += 1
        if (uploadSession.isSubmitActive(runId)) {
          uploadSession.setUploadProgress(completed)
        }
        return fileId
      } catch (error) {
        uploadFailed = true
        uploadSession.abortPendingUploads()
        throw error
      }
    }))
  }

  return {
    preparePreviewFiles,
    uploadFile,
    uploadFiles,
    uploadPreviews
  }
}
