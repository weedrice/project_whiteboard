import { computed, ref, watch, type Ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
import {
  loadStoredDraftSnapshot,
  migrateStoredDraftSnapshot,
  storeDraftSnapshot,
} from '@/features/board/posts/draft/postDraftLifecycle'
import { Storage } from '@/utils/storage'
import logger from '@/utils/logger'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'

interface DraftLocalSnapshotControllerOptions {
  storageKey: Ref<string>
  resolveStorageKey?: (draftId: number) => string
  draftId: Ref<number | null>
  draftVersion: Ref<number | null>
  clientDraftKey: Ref<string>
  clientInstanceId: string
  getDetachedDraftFileIdsToPreserve?: (payload: PostDraftData) => number[]
  onStored?: (snapshot: DraftRecoverySnapshot) => void
  onRemoved?: () => void
}

export function createDraftLocalSnapshotController({
  storageKey,
  resolveStorageKey,
  draftId,
  draftVersion,
  clientDraftKey,
  clientInstanceId,
  getDetachedDraftFileIdsToPreserve,
  onStored,
  onRemoved,
}: DraftLocalSnapshotControllerOptions) {
  const lastSaveFailed = ref(false)
  const activeStorageKey = computed(() => draftId.value != null
    ? resolveStorageKey?.(draftId.value) ?? storageKey.value
    : storageKey.value)

  watch(activeStorageKey, (nextKey, previousKey) => {
    if (draftId.value == null || nextKey === previousKey) return
    migrateStoredDraftSnapshot(
      previousKey,
      nextKey,
      draftId.value,
      clientDraftKey.value,
    )
  }, { flush: 'sync' })

  const store = (snapshot: DraftRecoverySnapshot) => {
    const snapshotFileIds = new Set(snapshot.fileIds ?? [])
    const requestedUnassociatedFileIds = snapshot.unassociatedUploadFileIds
      ?? getDetachedDraftFileIdsToPreserve?.(snapshot)
      ?? []
    const unassociatedUploadFileIds = [...new Set(requestedUnassociatedFileIds)]
      .filter((fileId) => snapshotFileIds.has(fileId))
    const storedSnapshot = {
      ...snapshot,
      unassociatedUploadFileIds: unassociatedUploadFileIds.length > 0
        ? unassociatedUploadFileIds
        : undefined,
      clientDraftKey: snapshot.clientDraftKey ?? clientDraftKey.value,
      version: snapshot.version ?? draftVersion.value ?? undefined,
      clientInstanceId,
    }
    const stored = storeDraftSnapshot(activeStorageKey.value, storedSnapshot)
    if (stored) onStored?.(storedSnapshot)
    if (!stored && !lastSaveFailed.value) {
      logger.error('Draft local snapshot storage failed.', {
        event: 'draft_local_snapshot_write_failed',
      })
      void reportDraftOperationalEvent('local_storage_write_failed')
    }
    lastSaveFailed.value = !stored
    return stored
  }

  const load = () => loadStoredDraftSnapshot(activeStorageKey.value)

  const removeKey = (key: string, notify = false) => {
    const removed = Storage.remove(key)
    if (!removed) void reportDraftOperationalEvent('local_storage_remove_failed')
    if (removed && notify) onRemoved?.()
    return removed
  }

  const remove = () => removeKey(activeStorageKey.value, true)

  const resetStatus = () => {
    lastSaveFailed.value = false
  }

  return {
    activeStorageKey,
    lastSaveFailed,
    store,
    load,
    remove,
    removeKey,
    resetStatus,
  }
}
