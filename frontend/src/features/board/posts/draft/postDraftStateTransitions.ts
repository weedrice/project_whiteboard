import type { Ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
import {
  createDraftRecoverySnapshot,
  stripDraftServerIdentity,
} from '@/features/board/posts/draft/postDraftSnapshot'
import { markDraftDeletedLocally } from '@/features/board/posts/draft/postDraftTombstone'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'
import type { DraftContentAdapter, DraftLifecycleEvent } from '@/features/board/posts/draft/postDraftContracts'

interface DraftStateTransitionControllerOptions {
  session: {
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
  }
  content: Pick<DraftContentAdapter,
    'buildPayload' | 'selectDetachedUploadIds' | 'normalizeSnapshot' | 'applySnapshot'>
  localStore: {
    load: () => DraftRecoverySnapshot | null
    remove: () => boolean
    store: (snapshot: DraftRecoverySnapshot) => boolean
  }
  onEvent: (event: DraftLifecycleEvent) => void
}

export function createDraftStateTransitionController({
  session,
  content,
  localStore,
  onEvent,
}: DraftStateTransitionControllerOptions) {
  const prepareDetachedSnapshot = (
    payload: PostDraftData,
    contractValidationFailed?: boolean,
  ) => {
    const detachedSnapshot = stripDraftServerIdentity({
      ...createDraftRecoverySnapshot(payload, null, null),
      ...(contractValidationFailed != null ? { contractValidationFailed } : {}),
    }, content.selectDetachedUploadIds(payload))
    return content.normalizeSnapshot(detachedSnapshot)
  }

  const preserveDetachedSnapshot = (
    snapshot: DraftRecoverySnapshot,
  ) => {
    content.applySnapshot(snapshot)
    onEvent({ type: 'references-removed' })
    const storedLocally = localStore.store(snapshot)
    if (!storedLocally) session.clearAutosaveTimer()
  }

  const transitionToDeletedDraft = () => {
    const deletedDraftId = session.draftId.value
    if (deletedDraftId != null && session.ownerId?.value != null) {
      if (!markDraftDeletedLocally(session.ownerId.value, deletedDraftId)) {
        void reportDraftOperationalEvent('tombstone_write_failed')
      }
    }
    session.clearAutosaveTimer()
    session.clearSaveRetry()
    session.invalidatePendingSaves()
    session.resetDraftTracking()
    session.draftDeleted.value = true
    session.protectedDraftForkAvailable.value = false
    session.staleReferencesReset.value = true
    session.draftConflict.value = false
    session.draftProtected.value = false
    session.lastSaveFailed.value = false
    preserveDetachedSnapshot(prepareDetachedSnapshot(content.buildPayload()))
  }

  const transitionToProtectedDraft = (contractValidationFailed: boolean) => {
    const localSnapshot = localStore.load()
    const shouldPreserveLocalChanges = session.localRevision() !== session.persistedRevision()
      || localSnapshot?.hasLocalChanges === true
    session.clearAutosaveTimer()
    session.clearSaveRetry()
    session.invalidatePendingSaves()
    if (shouldPreserveLocalChanges) {
      localStore.remove()
      session.resetDraftTracking()
      preserveDetachedSnapshot(prepareDetachedSnapshot(
        content.buildPayload(),
        contractValidationFailed,
      ))
    } else {
      localStore.remove()
    }
    session.protectedDraftForkAvailable.value = shouldPreserveLocalChanges
    session.draftProtected.value = true
    session.draftConflict.value = false
    session.draftDeleted.value = false
    session.staleReferencesReset.value = shouldPreserveLocalChanges
    session.lastSaveFailed.value = false
  }

  return {
    transitionToDeletedDraft,
    transitionToProtectedDraft,
  }
}
