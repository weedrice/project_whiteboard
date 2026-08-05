import type { Ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import {
  hasSameDraftContent,
  isMatchingLoadedDraft,
  toIsoTime,
  type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'

export type DraftCrossTabReconcileResult =
  | 'ignored'
  | 'local-applied'
  | 'server-acknowledged'
  | 'server-applied'
  | 'conflict'

interface DraftCrossTabReconcilerOptions {
  clientInstanceId: string
  draftId: Ref<number | null>
  draftVersion: Ref<number | null>
  clientDraftKey: Ref<string>
  updatedAt: Ref<string | null>
  lastSavedAt: Ref<string | null>
  lastSaveScope: Ref<'server' | 'browser' | null>
  draftConflict: Ref<boolean>
  restoreSource: Ref<'idle' | 'local' | 'server'>
  buildPayload: () => PostDraftData
  applyDraft: (snapshot: DraftRecoverySnapshot) => void
  onSaved?: () => void
  clearAutosaveTimer: () => void
  getLocalRevision: () => number
  getPersistedRevision: () => number
  incrementLocalRevision: () => void
  markCurrentRevisionPersisted: () => void
  getLastRemoteLocalChangeAt: () => number
  setLastRemoteLocalChangeAt: (value: number) => void
}

export function createDraftCrossTabReconciler({
  clientInstanceId,
  draftId,
  draftVersion,
  clientDraftKey,
  updatedAt,
  lastSavedAt,
  lastSaveScope,
  draftConflict,
  restoreSource,
  buildPayload,
  applyDraft,
  onSaved,
  clearAutosaveTimer,
  getLocalRevision,
  getPersistedRevision,
  incrementLocalRevision,
  markCurrentRevisionPersisted,
  getLastRemoteLocalChangeAt,
  setLastRemoteLocalChangeAt,
}: DraftCrossTabReconcilerOptions) {
  const reconcile = (incoming: DraftRecoverySnapshot): DraftCrossTabReconcileResult => {
    if (!incoming.clientInstanceId || incoming.clientInstanceId === clientInstanceId) return 'ignored'

    const currentPayload = buildPayload()
    const sameDraft = incoming.draftId != null && incoming.draftId === draftId.value
    const sameClientDraft = Boolean(incoming.clientDraftKey)
      && incoming.clientDraftKey === clientDraftKey.value
    const sameLogicalDraft = sameDraft || (draftId.value == null && sameClientDraft)
    const incomingServerTime = toIsoTime(incoming.updatedAt ?? incoming.modifiedAt)
    const currentServerTime = toIsoTime(updatedAt.value)
    const serverRevisionOrder = incoming.version != null && draftVersion.value != null
      ? Math.sign(incoming.version - draftVersion.value)
      : incomingServerTime && currentServerTime
        ? Math.sign(Date.parse(incomingServerTime) - Date.parse(currentServerTime))
        : null

    if (sameLogicalDraft && incoming.hasLocalChanges === false && serverRevisionOrder != null) {
      if (serverRevisionOrder < 0) return 'ignored'
      if (serverRevisionOrder === 0
        && draftId.value != null
        && !hasSameDraftContent(incoming, currentPayload)) return 'ignored'
    }

    const incomingClientModifiedAt = toIsoTime(incoming.clientModifiedAt)
    const incomingClientModifiedAtMs = incomingClientModifiedAt
      ? Date.parse(incomingClientModifiedAt)
      : 0
    if (sameLogicalDraft
      && incoming.hasLocalChanges === true
      && getLastRemoteLocalChangeAt() > 0
      && incomingClientModifiedAtMs <= getLastRemoteLocalChangeAt()) return 'ignored'

    const serverAdvanced = sameLogicalDraft && serverRevisionOrder != null && serverRevisionOrder > 0
    const matchingComposer = isMatchingLoadedDraft(incoming as DraftPost, currentPayload)
    const hasUnsavedLocalChanges = getLocalRevision() !== getPersistedRevision()

    if (!hasUnsavedLocalChanges
      && sameLogicalDraft
      && incoming.hasLocalChanges === true
      && matchingComposer) {
      draftId.value = incoming.draftId ?? null
      draftVersion.value = incoming.version ?? null
      clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
      updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
      incrementLocalRevision()
      setLastRemoteLocalChangeAt(incomingClientModifiedAtMs)
      restoreSource.value = 'local'
      applyDraft(incoming)
      return 'local-applied'
    }

    if (hasUnsavedLocalChanges
      && sameLogicalDraft
      && incoming.hasLocalChanges === false
      && matchingComposer
      && hasSameDraftContent(incoming, currentPayload)) {
      draftId.value = incoming.draftId ?? draftId.value
      draftVersion.value = incoming.version ?? null
      clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
      updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
      lastSavedAt.value = updatedAt.value
      lastSaveScope.value = 'server'
      markCurrentRevisionPersisted()
      setLastRemoteLocalChangeAt(0)
      applyDraft(incoming)
      onSaved?.()
      return 'server-acknowledged'
    }

    if (!hasUnsavedLocalChanges
      && sameLogicalDraft
      && incoming.hasLocalChanges === false
      && matchingComposer
      && incoming.draftId != null) {
      draftId.value = incoming.draftId
      draftVersion.value = incoming.version ?? null
      clientDraftKey.value = incoming.clientDraftKey ?? clientDraftKey.value
      updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
      lastSavedAt.value = updatedAt.value
      lastSaveScope.value = 'server'
      setLastRemoteLocalChangeAt(0)
      applyDraft(incoming)
      onSaved?.()
      return 'server-applied'
    }

    if (hasUnsavedLocalChanges
      && sameLogicalDraft
      && (incoming.hasLocalChanges || serverAdvanced)) {
      draftConflict.value = true
      clearAutosaveTimer()
      return 'conflict'
    }

    return 'ignored'
  }

  return { reconcile }
}
