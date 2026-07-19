import { computed, onUnmounted, ref } from 'vue'
import { fileApi } from '@/api/file'
import { isCancellationError } from '@/utils/cancellationError'

export function useEmoticonUploadSession() {
  const uploadProgress = ref({ current: 0, total: 0 })
  const uploadControllers = new Set<AbortController>()
  const trackedUploads = ref<Array<{ fileId: number, runId: number }>>([])
  const isDisposed = ref(false)
  let submitRunId = 0
  const discardPromises = new Map<number, Promise<void>>()
  const finalRequestRuns = new Set<number>()

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

  const recordUploadedFile = (fileId: number, runId = submitRunId) => {
    if (!trackedUploads.value.some((upload) => upload.fileId === fileId)) {
      trackedUploads.value.push({ fileId, runId })
    }
  }

  const clearTrackedUploads = (runId = submitRunId) => {
    trackedUploads.value = trackedUploads.value.filter((upload) => upload.runId !== runId)
  }

  const discardTrackedUploads = async (runId = submitRunId) => {
    const pendingDiscard = discardPromises.get(runId)
    if (pendingDiscard) {
      return pendingDiscard
    }

    const discardPromise = (async () => {
      while (true) {
        const fileIds = trackedUploads.value
          .filter((upload) => upload.runId === runId)
          .map((upload) => upload.fileId)
        if (fileIds.length === 0) return
        try {
          await fileApi.discardUploads(fileIds, { skipGlobalErrorHandler: true })
        } catch {
          return
        }
        const discardedIds = new Set(fileIds)
        trackedUploads.value = trackedUploads.value.filter(
          (upload) => upload.runId !== runId || !discardedIds.has(upload.fileId)
        )
      }
    })().finally(() => {
      discardPromises.delete(runId)
    })
    discardPromises.set(runId, discardPromise)

    return discardPromise
  }

  const beginFinalRequest = (runId: number) => {
    assertSubmitActive(runId)
    finalRequestRuns.add(runId)
  }

  const finishFinalRequest = (runId: number) => {
    finalRequestRuns.delete(runId)
  }

  const cancelSubmitRun = () => {
    const cancelledRunId = submitRunId
    submitRunId += 1
    abortPendingUploads()
    if (!finalRequestRuns.has(cancelledRunId)) {
      void discardTrackedUploads(cancelledRunId)
    }
    return true
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
    trackedUploadedFileIds: computed(() => trackedUploads.value.map((upload) => upload.fileId)),
    isDisposed: computed(() => isDisposed.value),
    createUploadCancelledError,
    isUploadCancelledError,
    abortPendingUploads,
    createUploadController,
    releaseUploadController,
    startSubmitRun,
    beginFinalRequest,
    finishFinalRequest,
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
