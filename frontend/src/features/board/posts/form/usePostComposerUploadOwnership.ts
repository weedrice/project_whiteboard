import { getCurrentScope, onScopeDispose, ref, watch, type Ref } from 'vue'
import { fileApi } from '@/api/file'
import { extractPostFileIdsFromContent } from '@/utils/postForm'
import logger from '@/utils/logger'

type UsePostComposerUploadOwnershipOptions = {
  identity: Ref<string>
  content: Ref<string>
  durableDraftFileIds: Ref<number[]>
}

export const POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS = 1_500
export const POST_COMPOSER_UPLOAD_RETRY_BASE_DELAY_MS = 1_000
export const POST_COMPOSER_UPLOAD_RETRY_MAX_ATTEMPTS = 3

type DiscardRetry = {
  timer: ReturnType<typeof setTimeout>
  force: boolean
}

export function usePostComposerUploadOwnership(options: UsePostComposerUploadOwnershipOptions) {
  const ownedUploadedFileIds = ref<number[]>([])
  const pendingDiscardTimers = new Map<number, ReturnType<typeof setTimeout>>()
  const discardRetryAttempts = new Map<number, number>()
  const discardRetries = new Map<number, DiscardRetry>()
  const inFlightDiscardFileIds = new Set<number>()
  const releasedInFlightFileIds = new Set<number>()
  let ownershipGeneration = 0
  let disposed = false

  function cancelScheduledDiscard(fileId: number) {
    const timer = pendingDiscardTimers.get(fileId)
    if (!timer) return
    clearTimeout(timer)
    pendingDiscardTimers.delete(fileId)
  }

  function scheduleDiscard(fileId: number) {
    if (pendingDiscardTimers.has(fileId)) return
    pendingDiscardTimers.set(fileId, setTimeout(() => {
      pendingDiscardTimers.delete(fileId)
      if (extractPostFileIdsFromContent(options.content.value).includes(fileId)) return
      void discardUploadedFiles([fileId])
    }, POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS))
  }

  function cancelDiscardRetry(fileId: number, resetAttempt = true) {
    const retry = discardRetries.get(fileId)
    if (retry) clearTimeout(retry.timer)
    discardRetries.delete(fileId)
    if (resetAttempt) discardRetryAttempts.delete(fileId)
  }

  function scheduleDiscardRetry(fileId: number, force: boolean) {
    if (discardRetries.has(fileId)) return
    const attempt = (discardRetryAttempts.get(fileId) ?? 0) + 1
    if (attempt > POST_COMPOSER_UPLOAD_RETRY_MAX_ATTEMPTS) {
      logger.warn('Post editor upload discard retries exhausted:', { fileId })
      return
    }
    discardRetryAttempts.set(fileId, attempt)
    const delay = POST_COMPOSER_UPLOAD_RETRY_BASE_DELAY_MS * 2 ** (attempt - 1)
    const timer = setTimeout(() => {
      discardRetries.delete(fileId)
      if (!ownedUploadedFileIds.value.includes(fileId)) {
        discardRetryAttempts.delete(fileId)
        return
      }
      if (!force && extractPostFileIdsFromContent(options.content.value).includes(fileId)) {
        discardRetryAttempts.delete(fileId)
        return
      }
      void discardUploadedFiles([fileId], force, true)
    }, delay)
    discardRetries.set(fileId, { timer, force })
  }

  function recordUploadedFile(fileId: number) {
    cancelScheduledDiscard(fileId)
    cancelDiscardRetry(fileId)
    if (!ownedUploadedFileIds.value.includes(fileId)) {
      ownedUploadedFileIds.value.push(fileId)
    }
  }

  function adoptUploadedFiles(fileIds: number[]) {
    fileIds.forEach(recordUploadedFile)
  }

  function releaseUploadedFiles(fileIds: number[]) {
    if (fileIds.length === 0) return
    fileIds.forEach(cancelScheduledDiscard)
    fileIds.forEach((fileId) => cancelDiscardRetry(fileId))
    fileIds
      .filter((fileId) => inFlightDiscardFileIds.has(fileId))
      .forEach((fileId) => releasedInFlightFileIds.add(fileId))
    if (ownedUploadedFileIds.value.length === 0) return
    const releasedIds = new Set(fileIds)
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !releasedIds.has(fileId))
  }

  async function discardUploadedFiles(fileIds: number[], force = false, retrying = false) {
    if (fileIds.length === 0 || ownedUploadedFileIds.value.length === 0) return
    fileIds.forEach(cancelScheduledDiscard)
    fileIds.forEach((fileId) => cancelDiscardRetry(fileId, !retrying))
    const requestedIds = new Set(fileIds)
    const discardedIds = ownedUploadedFileIds.value.filter((fileId) => requestedIds.has(fileId))
    if (discardedIds.length === 0) return

    const discardedIdSet = new Set(discardedIds)
    const requestGeneration = ownershipGeneration
    discardedIds.forEach((fileId) => inFlightDiscardFileIds.add(fileId))
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !discardedIdSet.has(fileId))
    try {
      await fileApi.discardUploads(discardedIds, { skipGlobalErrorHandler: true })
      discardedIds.forEach((fileId) => discardRetryAttempts.delete(fileId))
    } catch (error) {
      discardedIds.forEach((fileId) => {
        if (releasedInFlightFileIds.has(fileId)) return
        if (!ownedUploadedFileIds.value.includes(fileId)) ownedUploadedFileIds.value.push(fileId)
        scheduleDiscardRetry(fileId, force || disposed || requestGeneration !== ownershipGeneration)
      })
      logger.warn('Failed to discard unowned post editor uploads:', error)
    } finally {
      discardedIds.forEach((fileId) => {
        inFlightDiscardFileIds.delete(fileId)
        releasedInFlightFileIds.delete(fileId)
      })
    }
  }

  function discardUnreferencedUploads(content = options.content.value) {
    if (ownedUploadedFileIds.value.length === 0) return
    const referencedIds = new Set(extractPostFileIdsFromContent(content))
    ownedUploadedFileIds.value
      .filter((fileId) => referencedIds.has(fileId))
      .forEach((fileId) => {
        cancelScheduledDiscard(fileId)
        const retry = discardRetries.get(fileId)
        if (!retry?.force) cancelDiscardRetry(fileId)
      })
    const unreferencedIds = ownedUploadedFileIds.value.filter((fileId) => !referencedIds.has(fileId))
    unreferencedIds.forEach(scheduleDiscard)
  }

  function discardAllOwnedUploads() {
    void discardUploadedFiles([...ownedUploadedFileIds.value], true)
  }

  function handoffReferencedUploads() {
    if (ownedUploadedFileIds.value.length === 0) return
    const durableFileIds = new Set(options.durableDraftFileIds.value)
    const handedOffIds = ownedUploadedFileIds.value.filter((fileId) => durableFileIds.has(fileId))
    releaseUploadedFiles(handedOffIds)
    discardAllOwnedUploads()
  }

  const stopContentWatch = watch(
    options.content,
    (content) => discardUnreferencedUploads(content),
    { flush: 'post' },
  )
  const stopIdentityWatch = watch(
    options.identity,
    (_current, previous) => {
      if (previous !== undefined) {
        ownershipGeneration++
        handoffReferencedUploads()
      }
    },
    { flush: 'sync' },
  )

  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true
      ownershipGeneration++
      stopContentWatch()
      stopIdentityWatch()
      handoffReferencedUploads()
    })
  }

  return {
    ownedUploadedFileIds,
    recordUploadedFile,
    adoptUploadedFiles,
    releaseUploadedFiles,
    discardUnreferencedUploads,
    discardAllOwnedUploads,
    handoffReferencedUploads,
  }
}
