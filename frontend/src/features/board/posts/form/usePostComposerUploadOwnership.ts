import { getCurrentScope, onScopeDispose, ref, watch, type Ref } from 'vue'
import { fileApi } from '@/api/file'
import { extractPostFileIdsFromContent } from '@/utils/postForm'
import logger from '@/utils/logger'
import { Storage } from '@/utils/storage'
import { loadStoredDraftSnapshot } from '@/features/board/posts/draft/postDraftLifecycle'

type UsePostComposerUploadOwnershipOptions = {
  identity: Ref<string>
  content: Ref<string>
  durableDraftFileIds: Ref<number[]>
  ownerId: Ref<string | number | undefined>
  cleanupReady: Ref<boolean>
}

export const POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS = 1_500
export const POST_COMPOSER_UPLOAD_RETRY_BASE_DELAY_MS = 1_000
export const POST_COMPOSER_UPLOAD_RETRY_MAX_ATTEMPTS = 3
export const POST_COMPOSER_UPLOAD_DISCARD_QUEUE_PREFIX = 'noviis:post-upload-discard:'
export const POST_COMPOSER_UPLOAD_DISCARD_BATCH_SIZE = 101

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
  const persistedCleanupRetryAttempts = new Map<string, number>()
  const persistedCleanupRetryTimers = new Map<string, ReturnType<typeof setTimeout>>()

  const isOnline = () => typeof navigator === 'undefined' || navigator.onLine !== false
  const ownerStorageKey = (ownerId: string) => (
    `${POST_COMPOSER_UPLOAD_DISCARD_QUEUE_PREFIX}${encodeURIComponent(ownerId)}`
  )
  const ownerStoragePrefix = (ownerId: string) => `${ownerStorageKey(ownerId)}:`
  const cleanupItemStorageKey = (ownerId: string, fileId: number) => (
    `${ownerStoragePrefix(ownerId)}${fileId}`
  )
  const currentOwnerId = () => options.ownerId.value == null ? null : String(options.ownerId.value)
  const readPersistedCleanupIds = (ownerId: string) => {
    const legacyStored = Storage.get<unknown>(ownerStorageKey(ownerId), [])
    const legacyIds = Array.isArray(legacyStored)
      ? legacyStored.filter((fileId): fileId is number => Number.isSafeInteger(fileId) && fileId > 0)
      : []
    const prefix = ownerStoragePrefix(ownerId)
    const itemIds = Storage.keys()
      .filter((key) => key.startsWith(prefix))
      .map((key) => key.slice(prefix.length))
      .filter((fileId) => /^[1-9]\d*$/.test(fileId))
      .map(Number)
      .filter((fileId) => Number.isSafeInteger(fileId))
    return [...new Set([...legacyIds, ...itemIds])]
  }
  const persistCleanupIds = (fileIds: number[]) => {
    const groupedIds = new Map<string, number[]>()
    fileIds.forEach((fileId) => {
      const ownerId = uploadOwnerIds.get(fileId) ?? currentOwnerId()
      if (!ownerId) return
      groupedIds.set(ownerId, [...(groupedIds.get(ownerId) ?? []), fileId])
    })
    groupedIds.forEach((ids, ownerId) => {
      ids.forEach((fileId) => {
        if (!Storage.set(cleanupItemStorageKey(ownerId, fileId), { queuedAt: Date.now() })) {
          logger.warn('Failed to persist post editor upload cleanup:', { ownerId, fileId })
        }
      })
    })
  }
  const removeOwnerCleanupIds = (ownerId: string, fileIds: Iterable<number>) => {
    const removedIds = new Set(fileIds)
    removedIds.forEach((fileId) => Storage.remove(cleanupItemStorageKey(ownerId, fileId)))
    const legacyStored = Storage.get<unknown>(ownerStorageKey(ownerId), [])
    if (!Array.isArray(legacyStored)) return
    const retainedLegacyIds = legacyStored.filter((fileId) => (
      typeof fileId !== 'number' || !removedIds.has(fileId)
    ))
    if (retainedLegacyIds.length > 0) Storage.set(ownerStorageKey(ownerId), retainedLegacyIds)
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

  const readLocallyReferencedFileIds = (ownerId: string) => {
    const referencedIds = new Set<number>()
    const ownerDraftPrefix = `noviis:draft:${ownerId}:`
    Storage.keys()
      .filter((key) => key.startsWith(ownerDraftPrefix))
      .forEach((key) => {
        const snapshot = loadStoredDraftSnapshot(key)
        if (!snapshot) return
        ;[
          ...(snapshot.fileIds ?? []),
          ...(snapshot.unassociatedUploadFileIds ?? []),
        ].forEach((fileId) => referencedIds.add(fileId))
      })
    return referencedIds
  }

  function cancelPersistedCleanupRetry(ownerId: string, resetAttempt = true) {
    const timer = persistedCleanupRetryTimers.get(ownerId)
    if (timer) clearTimeout(timer)
    persistedCleanupRetryTimers.delete(ownerId)
    if (resetAttempt) persistedCleanupRetryAttempts.delete(ownerId)
  }

  function schedulePersistedCleanupRetry(ownerId: string) {
    if (persistedCleanupRetryTimers.has(ownerId) || !isOnline()) return
    const attempt = (persistedCleanupRetryAttempts.get(ownerId) ?? 0) + 1
    if (attempt > POST_COMPOSER_UPLOAD_RETRY_MAX_ATTEMPTS) {
      logger.warn('Persisted post editor upload cleanup retries exhausted:', { ownerId })
      return
    }
    persistedCleanupRetryAttempts.set(ownerId, attempt)
    const delay = POST_COMPOSER_UPLOAD_RETRY_BASE_DELAY_MS * 2 ** (attempt - 1)
    persistedCleanupRetryTimers.set(ownerId, setTimeout(() => {
      persistedCleanupRetryTimers.delete(ownerId)
      void drainPersistedCleanup(ownerId)
    }, delay))
  }

  function drainPersistedCleanup(ownerId = currentOwnerId()) {
    if (!ownerId || !options.cleanupReady.value || !isOnline()) return Promise.resolve()
    const existingPromise = persistedCleanupPromises.get(ownerId)
    if (existingPromise) return existingPromise
    const cleanupPromise = (async () => {
      while (true) {
        const locallyReferencedIds = readLocallyReferencedFileIds(ownerId)
        if (locallyReferencedIds.size > 0) {
          removeOwnerCleanupIds(ownerId, locallyReferencedIds)
        }
        const batch = readPersistedCleanupIds(ownerId)
          .slice(0, POST_COMPOSER_UPLOAD_DISCARD_BATCH_SIZE)
        if (batch.length === 0) {
          cancelPersistedCleanupRetry(ownerId)
          return
        }
        try {
          await fileApi.discardUploads(batch, { skipGlobalErrorHandler: true })
          removeOwnerCleanupIds(ownerId, batch)
          releaseUploadedFiles(batch)
        } catch (error) {
          schedulePersistedCleanupRetry(ownerId)
          logger.warn('Failed to drain persisted post editor upload cleanup:', error)
          return
        }
      }
    })()
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
    if (ownerId) {
      uploadOwnerIds.set(fileId, ownerId)
      removeOwnerCleanupIds(ownerId, [fileId])
    }
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
    const ownerId = currentOwnerId()
    if (ownerId) cancelPersistedCleanupRetry(ownerId)
    if (options.cleanupReady.value) void drainPersistedCleanup(ownerId)
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
    () => [options.ownerId.value, options.cleanupReady.value] as const,
    ([currentOwner], previous) => {
      const previousOwner = previous?.[0]
      if (previousOwner != null && previousOwner !== currentOwner) {
        cancelPersistedCleanupRetry(String(previousOwner))
      }
      void drainPersistedCleanup()
    },
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
      persistedCleanupRetryTimers.forEach((timer) => clearTimeout(timer))
      persistedCleanupRetryTimers.clear()
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
