import type { Ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
import {
  createDraftRecoverySnapshot,
  stripDraftServerIdentity,
} from '@/features/board/posts/draft/postDraftSnapshot'
import { markDraftDeletedLocally } from '@/features/board/posts/draft/postDraftTombstone'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'

interface DraftStateTransitionControllerOptions {
  draftId: Ref<number | null>
  ownerId?: Ref<string | number | null | undefined>
  draftDeleted: Ref<boolean>
  draftProtected: Ref<boolean>
  protectedDraftForkAvailable: Ref<boolean>
  staleReferencesReset: Ref<boolean>
  draftConflict: Ref<boolean>
  lastSaveFailed: Ref<boolean>
  localRevision: () => number
  persistedRevision: () => number
  clearAutosaveTimer: () => void
  clearSaveRetry: () => void
  invalidatePendingSaves: () => void
  resetDraftTracking: () => void
  buildPayload: () => PostDraftData
  getDetachedDraftFileIdsToPreserve?: (payload: PostDraftData) => number[]
  prepareStaleSnapshot?: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
  applyDraft: (snapshot: DraftRecoverySnapshot) => void
  onStaleReferencesReset?: () => void
  loadLocalSnapshot: () => DraftRecoverySnapshot | null
  removeLocalSnapshot: () => boolean
  storeLocalSnapshot: (snapshot: DraftRecoverySnapshot) => boolean
}

export function createDraftStateTransitionController({
  draftId,
  ownerId,
  draftDeleted,
  draftProtected,
  protectedDraftForkAvailable,
  staleReferencesReset,
  draftConflict,
  lastSaveFailed,
  localRevision,
  persistedRevision,
  clearAutosaveTimer,
  clearSaveRetry,
  invalidatePendingSaves,
  resetDraftTracking,
  buildPayload,
  getDetachedDraftFileIdsToPreserve,
  prepareStaleSnapshot,
  applyDraft,
  onStaleReferencesReset,
  loadLocalSnapshot,
  removeLocalSnapshot,
  storeLocalSnapshot,
}: DraftStateTransitionControllerOptions) {
  const prepareDetachedSnapshot = (
    payload: PostDraftData,
    contractValidationFailed?: boolean,
  ) => {
    const detachedSnapshot = stripDraftServerIdentity({
      ...createDraftRecoverySnapshot(payload, null, null),
      ...(contractValidationFailed != null ? { contractValidationFailed } : {}),
    }, getDetachedDraftFileIdsToPreserve?.(payload))
    return prepareStaleSnapshot?.(detachedSnapshot) ?? detachedSnapshot
  }

  const preserveDetachedSnapshot = (
    snapshot: DraftRecoverySnapshot,
  ) => {
    applyDraft(snapshot)
    onStaleReferencesReset?.()
    const storedLocally = storeLocalSnapshot(snapshot)
    if (!storedLocally) clearAutosaveTimer()
  }

  const transitionToDeletedDraft = () => {
    const deletedDraftId = draftId.value
    if (deletedDraftId != null && ownerId?.value != null) {
      if (!markDraftDeletedLocally(ownerId.value, deletedDraftId)) {
        void reportDraftOperationalEvent('tombstone_write_failed')
      }
    }
    clearAutosaveTimer()
    clearSaveRetry()
    invalidatePendingSaves()
    resetDraftTracking()
    draftDeleted.value = true
    protectedDraftForkAvailable.value = false
    staleReferencesReset.value = true
    draftConflict.value = false
    draftProtected.value = false
    lastSaveFailed.value = false
    preserveDetachedSnapshot(prepareDetachedSnapshot(buildPayload()))
  }

  const transitionToProtectedDraft = (contractValidationFailed: boolean) => {
    const localSnapshot = loadLocalSnapshot()
    const shouldPreserveLocalChanges = localRevision() !== persistedRevision()
      || localSnapshot?.hasLocalChanges === true
    clearAutosaveTimer()
    clearSaveRetry()
    invalidatePendingSaves()
    if (shouldPreserveLocalChanges) {
      removeLocalSnapshot()
      resetDraftTracking()
      preserveDetachedSnapshot(prepareDetachedSnapshot(
        buildPayload(),
        contractValidationFailed,
      ))
    } else {
      removeLocalSnapshot()
    }
    protectedDraftForkAvailable.value = shouldPreserveLocalChanges
    draftProtected.value = true
    draftConflict.value = false
    draftDeleted.value = false
    staleReferencesReset.value = shouldPreserveLocalChanges
    lastSaveFailed.value = false
  }

  return {
    transitionToDeletedDraft,
    transitionToProtectedDraft,
  }
}
