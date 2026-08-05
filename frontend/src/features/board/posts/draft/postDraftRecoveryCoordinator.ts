import type { Ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import {
  getDraftUpdatedAt,
  hasSameDraftContent,
  isMatchingLoadedDraft,
  loadDraftById,
  resolveDraftRecoverySnapshot,
  type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'
import { createDraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftSnapshot'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'
import {
  cleanupExpiredDraftTombstones,
  isDraftDeletedLocally,
} from '@/features/board/posts/draft/postDraftTombstone'
import { cleanupExpiredDraftSnapshots } from '@/features/board/posts/draft/postDraftLifecycle'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'

interface DraftRecoveryCoordinatorOptions {
  enabled: Ref<boolean>
  ownerId?: Ref<string | number | null | undefined>
  preferredDraftId?: Ref<number | null>
  draftId: Ref<number | null>
  draftVersion: Ref<number | null>
  clientDraftKey: Ref<string>
  updatedAt: Ref<string | null>
  lastSavedAt: Ref<string | null>
  lastSaveScope: Ref<'server' | 'browser' | null>
  lastSaveFailed: Ref<boolean>
  restoreFailed: Ref<boolean>
  multipleDraftsFound: Ref<boolean>
  isRestoringDraft: Ref<boolean>
  draftConflict: Ref<boolean>
  draftProtected: Ref<boolean>
  draftDeleted: Ref<boolean>
  staleReferencesReset: Ref<boolean>
  contractValidationFailed: Ref<boolean>
  restoreSource: Ref<'idle' | 'local' | 'server'>
  hasRestoredDraft: Ref<boolean>
  getSessionGeneration: () => number
  getLocalRevision: () => number
  incrementLocalRevision: () => void
  markCurrentRevisionPersisted: () => void
  startRecoveryRequest: () => AbortController
  finishRecoveryRequest: (controller: AbortController) => boolean
  isRecoveryRequestCurrent: (controller: AbortController) => boolean
  buildPayload: () => PostDraftData
  applyDraft: (snapshot: DraftRecoverySnapshot) => void
  prepareRecoveredSnapshot?: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
  prepareStaleSnapshot?: (snapshot: DraftRecoverySnapshot) => DraftRecoverySnapshot
  onSaved?: () => void
  onStaleReferencesReset?: () => void
  onLocalSnapshotAvailable?: (snapshot: DraftRecoverySnapshot) => void
  loadLocalSnapshot: () => DraftRecoverySnapshot | null
  removeLocalSnapshot: () => boolean
  storeLocalSnapshot: (snapshot: DraftRecoverySnapshot) => boolean
  resetDraftTracking: () => void
  transitionToProtectedDraft: () => void
  scheduleAutosave: () => void
  saveNow: () => Promise<DraftPost | null>
}

export function createDraftRecoveryCoordinator({
  enabled,
  ownerId,
  preferredDraftId,
  draftId,
  draftVersion,
  clientDraftKey,
  updatedAt,
  lastSavedAt,
  lastSaveScope,
  lastSaveFailed,
  restoreFailed,
  multipleDraftsFound,
  isRestoringDraft,
  draftConflict,
  draftProtected,
  draftDeleted,
  staleReferencesReset,
  contractValidationFailed,
  restoreSource,
  hasRestoredDraft,
  getSessionGeneration,
  getLocalRevision,
  incrementLocalRevision,
  markCurrentRevisionPersisted,
  startRecoveryRequest,
  finishRecoveryRequest,
  isRecoveryRequestCurrent,
  buildPayload,
  applyDraft,
  prepareRecoveredSnapshot,
  prepareStaleSnapshot,
  onSaved,
  onStaleReferencesReset,
  onLocalSnapshotAvailable,
  loadLocalSnapshot,
  removeLocalSnapshot,
  storeLocalSnapshot,
  resetDraftTracking,
  transitionToProtectedDraft,
  scheduleAutosave,
  saveNow,
}: DraftRecoveryCoordinatorOptions) {
  const requestIsCurrent = (generation: number, controller: AbortController) => (
    generation === getSessionGeneration() && isRecoveryRequestCurrent(controller)
  )

  const finishRequest = (generation: number, controller: AbortController) => {
    if (finishRecoveryRequest(controller) && generation === getSessionGeneration()) {
      isRestoringDraft.value = false
    }
  }

  const reloadServerDraft = async () => {
    const generation = getSessionGeneration()
    const revision = getLocalRevision()
    const currentDraftId = draftId.value
    if (currentDraftId == null) return false
    const controller = startRecoveryRequest()
    isRestoringDraft.value = true
    try {
      const latestDraft = await loadDraftById(currentDraftId, {
        signal: controller.signal,
        skipGlobalErrorHandler: true,
      })
      if (!requestIsCurrent(generation, controller) || draftId.value !== currentDraftId) return false
      if (revision !== getLocalRevision()) {
        draftConflict.value = true
        return false
      }
      if (!isMatchingLoadedDraft(latestDraft, buildPayload())) return false
      draftId.value = latestDraft.draftId
      draftVersion.value = latestDraft.version ?? null
      clientDraftKey.value = latestDraft.clientDraftKey ?? clientDraftKey.value
      updatedAt.value = getDraftUpdatedAt(latestDraft)
      lastSavedAt.value = updatedAt.value
      lastSaveScope.value = 'server'
      draftConflict.value = false
      draftProtected.value = false
      draftDeleted.value = false
      staleReferencesReset.value = false
      lastSaveFailed.value = false
      restoreFailed.value = false
      const serverSnapshot = latestDraft as unknown as DraftRecoverySnapshot
      const latestSnapshot = prepareRecoveredSnapshot?.(serverSnapshot) ?? serverSnapshot
      const preparedSnapshotChanged = !hasSameDraftContent(serverSnapshot, latestSnapshot)
      staleReferencesReset.value = Boolean(latestSnapshot.staleReferencesReset) || preparedSnapshotChanged
      applyDraft(latestSnapshot)
      storeLocalSnapshot({
        ...latestSnapshot,
        draftId: latestDraft.draftId,
        updatedAt: updatedAt.value ?? undefined,
        clientModifiedAt: new Date().toISOString(),
        hasLocalChanges: preparedSnapshotChanged,
      })
      if (preparedSnapshotChanged) {
        incrementLocalRevision()
        onStaleReferencesReset?.()
        scheduleAutosave()
      } else {
        markCurrentRevisionPersisted()
        onSaved?.()
      }
      return true
    } finally {
      finishRequest(generation, controller)
    }
  }

  const keepLocalDraft = async () => {
    const generation = getSessionGeneration()
    const currentDraftId = draftId.value
    if (currentDraftId == null) return false
    const controller = startRecoveryRequest()
    isRestoringDraft.value = true
    try {
      const latestDraft = await loadDraftById(currentDraftId, {
        signal: controller.signal,
        skipGlobalErrorHandler: true,
      })
      if (!requestIsCurrent(generation, controller) || draftId.value !== currentDraftId) return false
      if (!isMatchingLoadedDraft(latestDraft, buildPayload())) return false
      updatedAt.value = getDraftUpdatedAt(latestDraft)
      draftVersion.value = latestDraft.version ?? null
      clientDraftKey.value = latestDraft.clientDraftKey ?? clientDraftKey.value
      draftConflict.value = false
      draftProtected.value = false
      draftDeleted.value = false
      staleReferencesReset.value = false
      await saveNow()
      return true
    } finally {
      finishRequest(generation, controller)
    }
  }

  const restoreDraft = async () => {
    if (hasRestoredDraft.value || !enabled.value) return
    const generation = getSessionGeneration()
    const revision = getLocalRevision()
    const controller = startRecoveryRequest()
    hasRestoredDraft.value = true
    isRestoringDraft.value = true
    restoreFailed.value = false
    multipleDraftsFound.value = false

    try {
      cleanupExpiredDraftSnapshots()
      cleanupExpiredDraftTombstones()
      let localSnapshot = loadLocalSnapshot()
      if (isDraftDeletedLocally(ownerId?.value, localSnapshot?.draftId)) {
        removeLocalSnapshot()
        localSnapshot = null
      }
      const preferredId = isDraftDeletedLocally(ownerId?.value, preferredDraftId?.value)
        ? null
        : preferredDraftId?.value ?? null
      const payload = buildPayload()
      const resolved = await resolveServerDraftForRecovery({
        payload,
        localSnapshot,
        preferredDraftId: preferredId,
        signal: controller.signal,
        generationIsCurrent: () => requestIsCurrent(generation, controller),
        onStaleLocalSnapshot: (snapshot) => {
          const preparedSnapshot = prepareStaleSnapshot?.(snapshot) ?? snapshot
          resetDraftTracking()
          staleReferencesReset.value = true
          storeLocalSnapshot(preparedSnapshot)
          return preparedSnapshot
        },
      })
      if (!requestIsCurrent(generation, controller)) return
      if (resolved.draftProtected) {
        transitionToProtectedDraft()
        return
      }

      const recovery = resolveDraftRecoverySnapshot(resolved.localSnapshot, resolved.serverDraft)
      if (resolved.localSnapshot) onLocalSnapshotAvailable?.(resolved.localSnapshot)
      const recoveredSnapshot = recovery.snapshot
      const chosen = recoveredSnapshot
        ? prepareRecoveredSnapshot?.(recoveredSnapshot) ?? recoveredSnapshot
        : null
      const preparedServerSnapshotChanged = recovery.source === 'server'
        && resolved.serverDraft != null
        && chosen != null
        && !hasSameDraftContent(chosen, resolved.serverDraft as unknown as PostDraftData)
      restoreFailed.value = resolved.recoveryFailed
      multipleDraftsFound.value = resolved.multipleMatchesFound
      if (multipleDraftsFound.value) void reportDraftOperationalEvent('multiple_recovery_candidates')
      if (!chosen) return

      if (revision !== getLocalRevision()) {
        if (resolved.serverDraft) {
          draftId.value = resolved.serverDraft.draftId
          draftVersion.value = resolved.serverDraft.version ?? null
          clientDraftKey.value = resolved.serverDraft.clientDraftKey ?? clientDraftKey.value
          draftConflict.value = true
          updatedAt.value = resolved.localSnapshot?.updatedAt
            ?? resolved.localSnapshot?.modifiedAt
            ?? null
        }
        restoreSource.value = 'local'
        storeLocalSnapshot(createDraftRecoverySnapshot(
          buildPayload(),
          draftId.value,
          updatedAt.value,
        ))
        return
      }

      draftId.value = recovery.conflict && resolved.serverDraft
        ? resolved.serverDraft.draftId
        : chosen.draftId ?? null
      draftVersion.value = recovery.conflict && resolved.serverDraft
        ? resolved.serverDraft.version ?? null
        : chosen.version ?? null
      clientDraftKey.value = chosen.clientDraftKey
        ?? resolved.serverDraft?.clientDraftKey
        ?? clientDraftKey.value
      // 충돌 중에는 로컬 변경이 갈라져 나온 기준 버전을 보존한다. 최신 서버
      // 버전은 사용자가 로컬본 덮어쓰기를 선택한 순간에만 다시 조회한다.
      updatedAt.value = chosen.updatedAt ?? chosen.modifiedAt ?? null
      draftConflict.value = recovery.conflict
      draftProtected.value = false
      draftDeleted.value = false
      staleReferencesReset.value = Boolean(chosen.staleReferencesReset)
      contractValidationFailed.value = Boolean(chosen.contractValidationFailed)
      restoreSource.value = recovery.source
      applyDraft(chosen)
      if (staleReferencesReset.value) onStaleReferencesReset?.()
      if (recovery.source === 'server') {
        storeLocalSnapshot({
          ...chosen,
          clientModifiedAt: new Date().toISOString(),
          hasLocalChanges: preparedServerSnapshotChanged,
        })
        if (preparedServerSnapshotChanged) {
          incrementLocalRevision()
          scheduleAutosave()
        }
      } else {
        storeLocalSnapshot({
          ...chosen,
          draftId: draftId.value ?? undefined,
          clientModifiedAt: chosen.clientModifiedAt ?? new Date().toISOString(),
          hasLocalChanges: chosen.hasLocalChanges ?? true,
        })
      }
    } finally {
      finishRequest(generation, controller)
    }
  }

  const retryRestore = async () => {
    hasRestoredDraft.value = false
    restoreFailed.value = false
    multipleDraftsFound.value = false
    await restoreDraft()
  }

  return {
    reloadServerDraft,
    keepLocalDraft,
    restoreDraft,
    retryRestore,
  }
}
