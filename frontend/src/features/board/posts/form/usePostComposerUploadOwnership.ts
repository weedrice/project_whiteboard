import { getCurrentScope, onScopeDispose, ref, watch, type Ref } from 'vue'
import { fileApi } from '@/api/file'
import { extractPostFileIdsFromContent } from '@/utils/postForm'
import logger from '@/utils/logger'
import { Storage } from '@/utils/storage'

type UsePostComposerUploadOwnershipOptions = {
  identity: Ref<string>
  content: Ref<string>
  durableDraftFileIds: Ref<number[]>
  ownerId: Ref<string | number | undefined>
}

export const POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS = 1_500
export const POST_COMPOSER_UPLOAD_RETRY_BASE_DELAY_MS = 1_000
export const POST_COMPOSER_UPLOAD_RETRY_MAX_ATTEMPTS = 3
export const POST_COMPOSER_UPLOAD_DISCARD_QUEUE_PREFIX = 'noviis:post-upload-discard:'

type DiscardRetry = {
  timer: ReturnType<typeof setTimeout>
  force: boolean
}

export function usePostComposerUploadOwnership(options: UsePostComposerUploadOwnershipOptions) {
  const ownedUploadedFileIds = ref<number[]>([])
  const pendingDiscardTimers = new Map<number, ReturnType<typeof setTimeout>>()
  const discardRetryAttempts = new Map<number, number>()
  const discardRetries = new Map<number, DiscardRetry>()
  const offlineDiscardRetries = new Map<number, boolean>()
  const inFlightDiscardFileIds = new Set<number>()
  const releasedInFlightFileIds = new Set<number>()
  const uploadOwnerIds = new Map<number, string>()
  let ownershipGeneration = 0
  let disposed = false
  const persistedCleanupPromises = new Map<string, Promise<void>>()

  const isOnline = () => typeof navigator === 'undefined' || navigator.onLine !== false
  const ownerStorageKey = (ownerId: string) => (
    `${POST_COMPOSER_UPLOAD_DISCARD_QUEUE_PREFIX}${encodeURIComponent(ownerId)}`
  )
  const currentOwnerId = () => options.ownerId.value == null ? null : String(options.ownerId.value)
  const readPersistedCleanupIds = (ownerId: string) => {
    const stored = Storage.get<unknown>(ownerStorageKey(ownerId), [])
    if (!Array.isArray(stored)) return []
    return [...new Set(stored
      .filter((fileId): fileId is number => Number.isSafeInteger(fileId) && fileId > 0))]
  }
  const persistCleanupIds = (fileIds: number[]) => {
    const groupedIds = new Map<string, number[]>()
    fileIds.forEach((fileId) => {
      const ownerId = uploadOwnerIds.get(fileId) ?? currentOwnerId()
      if (!ownerId) return
      groupedIds.set(ownerId, [...(groupedIds.get(ownerId) ?? []), fileId])
    })
    groupedIds.forEach((ids, ownerId) => {
      Storage.set(ownerStorageKey(ownerId), [...new Set([...readPersistedCleanupIds(ownerId), ...ids])])
    })
  }
  const removeOwnerCleanupIds = (ownerId: string, fileIds: Iterable<number>) => {
    const removedIds = new Set(fileIds)
    const retainedIds = readPersistedCleanupIds(ownerId).filter((fileId) => !removedIds.has(fileId))
    if (retainedIds.length > 0) Storage.set(ownerStorageKey(ownerId), retainedIds)
    else Storage.remove(ownerStorageKey(ownerId))
  }
  const removePersistedCleanupIds = (fileIds: number[]) => {
    const groupedIds = new Map<string, Set<number>>()
    fileIds.forEach((fileId) => {
      const ownerId = uploadOwnerIds.get(fileId) ?? currentOwnerId()
      if (!ownerId) return
      const ids = groupedIds.get(ownerId) ?? new Set<number>()
      ids.add(fileId)
      groupedIds.set(ownerId, ids)
    })
    groupedIds.forEach((removedIds, ownerId) => {
      removeOwnerCleanupIds(ownerId, removedIds)
    })
  }

  function drainPersistedCleanup(ownerId = currentOwnerId()) {
    if (!ownerId || !isOnline()) return Promise.resolve()
    const existingPromise = persistedCleanupPromises.get(ownerId)
    if (existingPromise) return existingPromise
    const fileIds = readPersistedCleanupIds(ownerId)
    if (fileIds.length === 0) return Promise.resolve()
    const cleanupPromise = fileApi.discardUploads(fileIds, { skipGlobalErrorHandler: true })
      .then(() => {
        removeOwnerCleanupIds(ownerId, fileIds)
        releaseUploadedFiles(fileIds)
      })
      .catch((error) => {
        logger.warn('Failed to drain persisted post editor upload cleanup:', error)
      })
      .finally(() => {
        persistedCleanupPromises.delete(ownerId)
      })
    persistedCleanupPromises.set(ownerId, cleanupPromise)
    return cleanupPromise
  }

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
    offlineDiscardRetries.delete(fileId)
    if (resetAttempt) discardRetryAttempts.delete(fileId)
  }

  function scheduleDiscardRetry(fileId: number, force: boolean) {
    if (discardRetries.has(fileId)) return
    if (!isOnline()) {
      offlineDiscardRetries.set(fileId, force || offlineDiscardRetries.get(fileId) === true)
      return
    }
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
    const ownerId = currentOwnerId()
    if (ownerId) uploadOwnerIds.set(fileId, ownerId)
  }

  function adoptUploadedFiles(fileIds: number[]) {
    fileIds.forEach(recordUploadedFile)
  }

  function releaseUploadedFiles(fileIds: number[]) {
    if (fileIds.length === 0) return
    fileIds.forEach(cancelScheduledDiscard)
    fileIds.forEach((fileId) => cancelDiscardRetry(fileId))
    removePersistedCleanupIds(fileIds)
    fileIds
      .filter((fileId) => inFlightDiscardFileIds.has(fileId))
      .forEach((fileId) => releasedInFlightFileIds.add(fileId))
    fileIds.forEach((fileId) => uploadOwnerIds.delete(fileId))
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
    if (force) persistCleanupIds(discardedIds)
    if (!isOnline()) {
      discardedIds.forEach((fileId) => scheduleDiscardRetry(fileId, force))
      return
    }

    const discardedIdSet = new Set(discardedIds)
    const requestGeneration = ownershipGeneration
    discardedIds.forEach((fileId) => inFlightDiscardFileIds.add(fileId))
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !discardedIdSet.has(fileId))
    try {
      await fileApi.discardUploads(discardedIds, { skipGlobalErrorHandler: true })
      discardedIds.forEach((fileId) => discardRetryAttempts.delete(fileId))
      removePersistedCleanupIds(discardedIds)
      discardedIds.forEach((fileId) => uploadOwnerIds.delete(fileId))
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

  const handleOnline = async () => {
    await drainPersistedCleanup()
    const retries = [...offlineDiscardRetries.entries()]
    retries.forEach(([fileId, force]) => {
      offlineDiscardRetries.delete(fileId)
      if (!ownedUploadedFileIds.value.includes(fileId)) return
      if (!force && extractPostFileIdsFromContent(options.content.value).includes(fileId)) {
        discardRetryAttempts.delete(fileId)
        return
      }
      void discardUploadedFiles([fileId], force, true)
    })
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
  const stopOwnerWatch = watch(
    options.ownerId,
    () => void drainPersistedCleanup(),
    { immediate: true },
  )

  if (typeof window !== 'undefined') window.addEventListener('online', handleOnline)

  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true
      ownershipGeneration++
      stopContentWatch()
      stopIdentityWatch()
      stopOwnerWatch()
      if (typeof window !== 'undefined') window.removeEventListener('online', handleOnline)
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
