import { computed, onUnmounted, ref } from 'vue'
import { fileApi } from '@/api/file'
import { isCancellationError } from '@/utils/cancellationError'
import type { AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { createAuthSessionScope, type AuthSessionScope } from '@/utils/authSessionScope'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'

export function useEmoticonUploadSession() {
  const authStore = useAuthStore()
  const runScopes = new Map<number, AuthSessionScope>()
  const scopeFor = (runId: number) => {
    let scope = runScopes.get(runId)
    if (!scope) { scope = createAuthSessionScope(authStore); runScopes.set(runId, scope) }
    return scope
  }
  const requestConfig = (runId: number, config?: AxiosRequestConfig) => scopeFor(runId).withRequestConfig(config)
  const finishSubmitRun = (runId: number) => { runScopes.get(runId)?.dispose(); runScopes.delete(runId) }
  const uploadProgress = ref({ current: 0, total: 0 })
  const uploadControllers = new Map<AbortController, number>()
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

  const abortPendingUploads = (runId?: number) => {
    uploadControllers.forEach((ownerRunId, controller) => {
      if (runId !== undefined && ownerRunId !== runId) return
      controller.abort()
      uploadControllers.delete(controller)
    })
  }

  const createUploadController = (runId = submitRunId) => {
    const controller = new AbortController()
    uploadControllers.set(controller, runId)
    return controller
  }

  const releaseUploadController = (controller: AbortController) => {
    uploadControllers.delete(controller)
  }

  const startSubmitRun = () => {
    submitRunId += 1
    scopeFor(submitRunId)
    return submitRunId
  }

  const recordUploadedFile = (fileId: number, runId = submitRunId) => {
    scopeFor(runId)
    if (!trackedUploads.value.some((upload) => upload.fileId === fileId)) {
      trackedUploads.value.push({ fileId, runId })
    }
  }

  const clearTrackedUploads = (runId = submitRunId) => {
    trackedUploads.value = trackedUploads.value.filter((upload) => upload.runId !== runId)
  }

  const discardTrackedUploads = async (runId = submitRunId) => {
    const scope = runScopes.get(runId)
    if (!scope?.isCurrent()) { clearTrackedUploads(runId); return }
    const pendingDiscard = discardPromises.get(runId)
    if (pendingDiscard) {
      return pendingDiscard
    }

    const discardPromise = (async () => {
      while (scope.isCurrent()) {
        const fileIds = trackedUploads.value
          .filter((upload) => upload.runId === runId)
          .map((upload) => upload.fileId)
        if (fileIds.length === 0) return
        try {
          await fileApi.discardUploads(fileIds, scope.withRequestConfig({ skipGlobalErrorHandler: true }))
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
    abortPendingUploads(cancelledRunId)
    if (!finalRequestRuns.has(cancelledRunId)) {
      void discardTrackedUploads(cancelledRunId)
    }
    return true
  }

  const isSubmitActive = (runId?: number) => (
    !isDisposed.value && (runId == null || submitRunId === runId) && (runScopes.get(runId ?? submitRunId)?.isCurrent() ?? true)
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

  useAuthSessionReset(() => {
    runScopes.forEach(scope => scope.cancel())
    abortPendingUploads()
    resetUploadProgress()
  })

  onUnmounted(() => {
    isDisposed.value = true
    cancelSubmitRun()
    // A final association can finish after unmount; its submit guard releases it.
    runScopes.forEach((scope, runId) => {
      if (!finalRequestRuns.has(runId)) scope.dispose()
    })
  })

  return {
    requestConfig,
    finishSubmitRun,
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
