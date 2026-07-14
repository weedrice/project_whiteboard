import { computed, onUnmounted, ref } from 'vue'
import { fileApi } from '@/api/file'
import { isCancellationError } from '@/utils/cancellationError'

export function useEmoticonUploadSession() {
  const uploadProgress = ref({ current: 0, total: 0 })
  const uploadControllers = new Set<AbortController>()
  const trackedUploadedFileIds = ref<number[]>([])
  const isDisposed = ref(false)
  let submitRunId = 0
  let discardPromise: Promise<void> | null = null

  const createUploadCancelledError = () => new DOMException('Upload has been cancelled', 'AbortError')

  const isUploadCancelledError = (error: unknown) => {
    return isCancellationError(error, {
      names: ['AbortError'],
      codes: ['ERR_CANCELED'],
    })
  }

  const abortPendingUploads = () => {
    uploadControllers.forEach((controller) => controller.abort())
    uploadControllers.clear()
  }

  const createUploadController = () => {
    const controller = new AbortController()
    uploadControllers.add(controller)
    return controller
  }

  const releaseUploadController = (controller: AbortController) => {
    uploadControllers.delete(controller)
  }

  const startSubmitRun = () => {
    submitRunId += 1
    return submitRunId
  }

  const recordUploadedFile = (fileId: number) => {
    if (!trackedUploadedFileIds.value.includes(fileId)) {
      trackedUploadedFileIds.value.push(fileId)
    }
  }

  const clearTrackedUploads = () => {
    trackedUploadedFileIds.value = []
  }

  const discardTrackedUploads = async () => {
    if (discardPromise) {
      return discardPromise
    }

    discardPromise = (async () => {
      while (trackedUploadedFileIds.value.length > 0) {
        const fileIds = [...trackedUploadedFileIds.value]
        try {
          await fileApi.discardUploads(fileIds, { skipGlobalErrorHandler: true })
        } catch {
          return
        }
        const discardedIds = new Set(fileIds)
        trackedUploadedFileIds.value = trackedUploadedFileIds.value.filter(
          (fileId) => !discardedIds.has(fileId)
        )
      }
    })().finally(() => {
      discardPromise = null
    })

    return discardPromise
  }

  const cancelSubmitRun = () => {
    submitRunId += 1
    abortPendingUploads()
    void discardTrackedUploads()
  }

  const isSubmitActive = (runId?: number) => (
    !isDisposed.value && (runId == null || submitRunId === runId)
  )

  const assertSubmitActive = (runId?: number) => {
    if (!isSubmitActive(runId)) {
      throw createUploadCancelledError()
    }
  }

  const setUploadProgress = (current: number, total = uploadProgress.value.total) => {
    uploadProgress.value = { current, total }
  }

  const resetUploadProgress = () => {
    uploadProgress.value = { current: 0, total: 0 }
  }

  onUnmounted(() => {
    isDisposed.value = true
    cancelSubmitRun()
  })

  return {
    uploadProgress,
    trackedUploadedFileIds: computed(() => trackedUploadedFileIds.value),
    isDisposed: computed(() => isDisposed.value),
    createUploadCancelledError,
    isUploadCancelledError,
    abortPendingUploads,
    createUploadController,
    releaseUploadController,
    startSubmitRun,
    cancelSubmitRun,
    recordUploadedFile,
    clearTrackedUploads,
    discardTrackedUploads,
    isSubmitActive,
    assertSubmitActive,
    setUploadProgress,
    resetUploadProgress,
  }
}
