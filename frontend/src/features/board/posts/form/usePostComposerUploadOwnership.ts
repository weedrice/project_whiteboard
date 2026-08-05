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

export function usePostComposerUploadOwnership(options: UsePostComposerUploadOwnershipOptions) {
  const ownedUploadedFileIds = ref<number[]>([])
  const pendingDiscardTimers = new Map<number, ReturnType<typeof setTimeout>>()

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
    if (fileIds.length === 0 || ownedUploadedFileIds.value.length === 0) return
    fileIds.forEach(cancelScheduledDiscard)
    const releasedIds = new Set(fileIds)
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !releasedIds.has(fileId))
  }

  async function discardUploadedFiles(fileIds: number[]) {
    if (fileIds.length === 0 || ownedUploadedFileIds.value.length === 0) return
    fileIds.forEach(cancelScheduledDiscard)
    const requestedIds = new Set(fileIds)
    const discardedIds = ownedUploadedFileIds.value.filter((fileId) => requestedIds.has(fileId))
    if (discardedIds.length === 0) return

    const discardedIdSet = new Set(discardedIds)
    ownedUploadedFileIds.value = ownedUploadedFileIds.value.filter((fileId) => !discardedIdSet.has(fileId))
    try {
      await fileApi.discardUploads(discardedIds, { skipGlobalErrorHandler: true })
    } catch (error) {
      adoptUploadedFiles(discardedIds)
      logger.warn('Failed to discard unowned post editor uploads:', error)
    }
  }

  function discardUnreferencedUploads(content = options.content.value) {
    if (ownedUploadedFileIds.value.length === 0) return
    const referencedIds = new Set(extractPostFileIdsFromContent(content))
    ownedUploadedFileIds.value
      .filter((fileId) => referencedIds.has(fileId))
      .forEach(cancelScheduledDiscard)
    const unreferencedIds = ownedUploadedFileIds.value.filter((fileId) => !referencedIds.has(fileId))
    unreferencedIds.forEach(scheduleDiscard)
  }

  function discardAllOwnedUploads() {
    void discardUploadedFiles([...ownedUploadedFileIds.value])
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
      if (previous !== undefined) handoffReferencedUploads()
    },
    { flush: 'sync' },
  )

  if (getCurrentScope()) {
    onScopeDispose(() => {
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
