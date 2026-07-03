import { computed, onUnmounted, ref } from 'vue'
import { isCancellationError } from '@/utils/cancellationError'

export function useEmoticonUploadSession() {
  const uploadProgress = ref({ current: 0, total: 0 })
  const uploadControllers = new Set<AbortController>()
  const isDisposed = ref(false)
  let submitRunId = 0

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

  const cancelSubmitRun = () => {
    submitRunId += 1
    abortPendingUploads()
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
    isDisposed: computed(() => isDisposed.value),
    createUploadCancelledError,
    isUploadCancelledError,
    abortPendingUploads,
    createUploadController,
    releaseUploadController,
    startSubmitRun,
    cancelSubmitRun,
    isSubmitActive,
    assertSubmitActive,
    setUploadProgress,
    resetUploadProgress,
  }
}
