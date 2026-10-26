import { getCurrentScope, onScopeDispose, ref, watch, type Ref } from 'vue'
import { fileApi } from '@/api/file'
import logger from '@/utils/logger'

type UsePostComposerUploadOwnershipOptions = {
  identity: Ref<string>
  durableDraftFileIds: Ref<number[]>
}

// FileUploadDiscardRequest limits each request to 101 file IDs.
const UPLOAD_DISCARD_BATCH_SIZE = 101

/** Keep uploads available to the editor's undo history until this form ends. */
export function usePostComposerUploadOwnership(options: UsePostComposerUploadOwnershipOptions) {
  const ownedUploadedFileIds = ref<number[]>([])

  function recordUploadedFile(fileId: number) {
    if (!ownedUploadedFileIds.value.includes(fileId)) ownedUploadedFileIds.value.push(fileId)
  }

  function adoptUploadedFiles(fileIds: number[]) {
    fileIds.forEach(recordUploadedFile)
  }

  function releaseUploadedFiles(fileIds: number[]) {
    const releasedIds = new Set(fileIds)
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !releasedIds.has(fileId))
  }

  async function discardUploads(fileIds: number[]) {
    try {
      await fileApi.discardUploads(fileIds, { skipGlobalErrorHandler: true })
    } catch (error) {
      logger.warn('Failed to discard unowned post editor uploads; server expiry will clean them:', error)
    }
  }

  function handoffReferencedUploads() {
    const durableFileIds = new Set(options.durableDraftFileIds.value)
    // A newer local snapshot may have failed. Preserve every file in the last
    // successfully stored snapshot even if the current editor no longer uses it.
    const discardedIds = ownedUploadedFileIds.value.filter((fileId) => !durableFileIds.has(fileId))
    ownedUploadedFileIds.value = []
    // Browser close/offline failures still use the server's temporary-file expiry.
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return
    for (let offset = 0; offset < discardedIds.length; offset += UPLOAD_DISCARD_BATCH_SIZE) {
      void discardUploads(discardedIds.slice(offset, offset + UPLOAD_DISCARD_BATCH_SIZE))
    }
  }

  const stopIdentityWatch = watch(options.identity, handoffReferencedUploads, { flush: 'sync' })
  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopIdentityWatch()
      handoffReferencedUploads()
    })
  }

  return {
    ownedUploadedFileIds,
    recordUploadedFile,
    adoptUploadedFiles,
    releaseUploadedFiles,
  }
}
