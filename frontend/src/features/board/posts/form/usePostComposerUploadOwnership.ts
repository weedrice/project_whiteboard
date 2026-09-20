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
// FileUploadDiscardRequest limits each request to 101 file IDs.
const UPLOAD_DISCARD_BATCH_SIZE = 101

/**
 * Tracks uploads created by the current editor session.
 *
 * Immediate removal is best-effort only. Files that survive a browser close,
 * an offline transition, or a failed discard request are cleaned by the server
 * expiry policy instead of a durable browser retry queue.
 */
export function usePostComposerUploadOwnership(options: UsePostComposerUploadOwnershipOptions) {
  const ownedUploadedFileIds = ref<number[]>([])
  const pendingDiscardDeadlines = new Map<number, number>()
  let discardTimer: ReturnType<typeof setTimeout> | undefined
  let scheduledDiscardDeadline: number | undefined
  const inFlightDiscardFileIds = new Set<number>()
  const releasedInFlightFileIds = new Set<number>()
  let ownershipGeneration = 0
  let disposed = false

  const isOnline = () => typeof navigator === 'undefined' || navigator.onLine !== false

  function scheduleNextDiscard() {
    const nextDeadline = pendingDiscardDeadlines.size > 0
      ? Math.min(...pendingDiscardDeadlines.values())
      : undefined
    if (nextDeadline === scheduledDiscardDeadline) return
    if (discardTimer !== undefined) clearTimeout(discardTimer)
    discardTimer = undefined
    scheduledDiscardDeadline = nextDeadline
    if (nextDeadline === undefined) return

    discardTimer = setTimeout(() => {
      discardTimer = undefined
      scheduledDiscardDeadline = undefined
      const now = performance.now()
      const dueFileIds = [...pendingDiscardDeadlines]
        .filter(([, deadline]) => deadline <= now)
        .map(([fileId]) => fileId)
      dueFileIds.forEach((fileId) => pendingDiscardDeadlines.delete(fileId))
      scheduleNextDiscard()
      if (dueFileIds.length === 0) return

      const referencedIds = new Set(extractPostFileIdsFromContent(options.content.value))
      const discardedIds = dueFileIds.filter((fileId) => !referencedIds.has(fileId))
      for (let offset = 0; offset < discardedIds.length; offset += UPLOAD_DISCARD_BATCH_SIZE) {
        void discardUploadedFiles(discardedIds.slice(offset, offset + UPLOAD_DISCARD_BATCH_SIZE))
      }
    }, Math.max(0, nextDeadline - performance.now()))
  }

  function cancelScheduledDiscard(fileId: number) {
    if (!pendingDiscardDeadlines.delete(fileId)) return
    scheduleNextDiscard()
  }

  function recordUploadedFile(fileId: number) {
    cancelScheduledDiscard(fileId)
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
    fileIds
      .filter((fileId) => inFlightDiscardFileIds.has(fileId))
      .forEach((fileId) => releasedInFlightFileIds.add(fileId))
    const releasedIds = new Set(fileIds)
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !releasedIds.has(fileId))
  }

  async function discardUploadedFiles(fileIds: number[], terminal = false) {
    if (fileIds.length === 0 || ownedUploadedFileIds.value.length === 0) return
    fileIds.forEach(cancelScheduledDiscard)
    const requestedIds = new Set(fileIds)
    const discardedIds = ownedUploadedFileIds.value.filter((fileId) => requestedIds.has(fileId))
    if (discardedIds.length === 0) return

    const discardedIdSet = new Set(discardedIds)
    const requestGeneration = ownershipGeneration
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !discardedIdSet.has(fileId))
    if (!isOnline()) {
      if (!terminal && !disposed && requestGeneration === ownershipGeneration) {
        ownedUploadedFileIds.value.push(...discardedIds)
      }
      return
    }

    discardedIds.forEach((fileId) => inFlightDiscardFileIds.add(fileId))
    try {
      await fileApi.discardUploads(discardedIds, { skipGlobalErrorHandler: true })
    } catch (error) {
      if (!terminal && !disposed && requestGeneration === ownershipGeneration) {
        discardedIds.forEach((fileId) => {
          if (releasedInFlightFileIds.has(fileId)) return
          if (!ownedUploadedFileIds.value.includes(fileId)) ownedUploadedFileIds.value.push(fileId)
        })
      }
      logger.warn('Failed to discard unowned post editor uploads; server expiry will clean them:', error)
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
      .forEach(cancelScheduledDiscard)
    const deadline = performance.now() + POST_COMPOSER_UPLOAD_DISCARD_DELAY_MS
    ownedUploadedFileIds.value
      .filter((fileId) => !referencedIds.has(fileId))
      .forEach((fileId) => {
        if (!pendingDiscardDeadlines.has(fileId)) pendingDiscardDeadlines.set(fileId, deadline)
      })
    scheduleNextDiscard()
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
