import { fileApi } from '@/api/file'
import { unwrapAxiosApiData } from '@/api/response'
import {
  createUploadableEmoticonImageFile,
  type EmoticonImagePreview
} from '@/utils/emoticonImage'
import type { useEmoticonUploadSession } from '@/features/emoticon/form/useEmoticonUploadSession'

type EmoticonUploadSession = ReturnType<typeof useEmoticonUploadSession>

type UploadOptions = {
  skipGlobalErrorHandler?: boolean
}

const EMOTICON_IMAGE_CONCURRENCY_LIMIT = 3

async function mapWithConcurrency<T, R>(
  items: T[],
  limit: number,
  task: (item: T, index: number) => Promise<R>
): Promise<R[]> {
  if (items.length === 0) {
    return []
  }

  const results: R[] = new Array(items.length)
  let activeCount = 0
  let completedCount = 0
  let nextIndex = 0
  let stopped = false

  return new Promise<R[]>((resolve, reject) => {
    const runNext = () => {
      if (stopped) {
        return
      }

      if (completedCount === items.length) {
        resolve(results)
        return
      }

      while (activeCount < limit && nextIndex < items.length) {
        const currentIndex = nextIndex
        nextIndex += 1
        activeCount += 1

        Promise.resolve()
          .then(() => task(items[currentIndex], currentIndex))
          .then((result) => {
            activeCount -= 1
            completedCount += 1
            results[currentIndex] = result
            runNext()
          })
          .catch((error) => {
            activeCount -= 1
            stopped = true
            reject(error)
          })
      }
    }

    runNext()
  })
}

export function useEmoticonImageUploader(uploadSession: EmoticonUploadSession) {
  async function preparePreviewFiles(previews: EmoticonImagePreview[]) {
    return mapWithConcurrency(
      previews,
      EMOTICON_IMAGE_CONCURRENCY_LIMIT,
      (preview) => createUploadableEmoticonImageFile(preview)
    )
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

    return mapWithConcurrency(files, EMOTICON_IMAGE_CONCURRENCY_LIMIT, async (file) => {
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
    })
  }

  return {
    preparePreviewFiles,
    uploadFile,
    uploadFiles,
    uploadPreviews
  }
}
