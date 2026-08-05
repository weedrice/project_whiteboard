import type { Ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import {
  hasSameDraftContent,
  toIsoTime,
  type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'

export type DraftCrossTabReconcileResult =
  | 'ignored'
  | 'server-acknowledged'
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

/**
 * Reconciles metadata from another tab without applying its editor payload.
 *
 * Cross-tab messages are notifications, not a second document transport. A
 * canonical server event may acknowledge the exact content already visible in
 * this tab. Every other valid change pauses autosave and lets the user choose
 * whether to reload the server draft or keep the current editor content.
 */
export function createDraftCrossTabReconciler(options: DraftCrossTabReconcilerOptions) {
  let remoteOrderKey: string | null = null
  let latestRemoteServerVersion: number | null = null
  let latestRemoteServerTimeMs = 0

  const reconcile = (incoming: DraftRecoverySnapshot): DraftCrossTabReconcileResult => {
    if (!incoming.clientInstanceId || incoming.clientInstanceId === options.clientInstanceId) {
      return 'ignored'
    }

    const currentPayload = options.buildPayload()
    const sameDraft = incoming.draftId != null && incoming.draftId === options.draftId.value
    const sameClientDraft = Boolean(incoming.clientDraftKey)
      && incoming.clientDraftKey === options.clientDraftKey.value
    const sameLogicalDraft = sameDraft || (options.draftId.value == null && sameClientDraft)
    if (!sameLogicalDraft) return 'ignored'

    const logicalDraftKey = incoming.draftId != null
      ? `draft:${incoming.draftId}`
      : `client:${incoming.clientDraftKey}`
    if (remoteOrderKey !== logicalDraftKey) {
      remoteOrderKey = logicalDraftKey
      latestRemoteServerVersion = null
      latestRemoteServerTimeMs = 0
    }

    const incomingServerTime = toIsoTime(incoming.updatedAt ?? incoming.modifiedAt)
    const incomingServerTimeMs = incomingServerTime ? Date.parse(incomingServerTime) : 0
    const currentServerTime = toIsoTime(options.updatedAt.value)
    const serverRevisionOrder = incoming.version != null && options.draftVersion.value != null
      ? Math.sign(incoming.version - options.draftVersion.value)
      : incomingServerTime && currentServerTime
        ? Math.sign(incomingServerTimeMs - Date.parse(currentServerTime))
        : null

    if (incoming.hasLocalChanges === false) {
      if (incoming.version != null
        && latestRemoteServerVersion != null
        && incoming.version < latestRemoteServerVersion) return 'ignored'
      if (incoming.version == null
        && incomingServerTimeMs > 0
        && latestRemoteServerTimeMs > 0
        && incomingServerTimeMs < latestRemoteServerTimeMs) return 'ignored'
      if (serverRevisionOrder != null) {
        if (serverRevisionOrder < 0) return 'ignored'
        if (serverRevisionOrder === 0
          && options.draftId.value != null
          && !hasSameDraftContent(incoming, currentPayload)) return 'ignored'
      }
      if (incoming.version != null) {
        latestRemoteServerVersion = Math.max(latestRemoteServerVersion ?? incoming.version, incoming.version)
      }
      latestRemoteServerTimeMs = Math.max(latestRemoteServerTimeMs, incomingServerTimeMs)
    }

    const incomingClientModifiedAt = toIsoTime(incoming.clientModifiedAt)
    const incomingClientModifiedAtMs = incomingClientModifiedAt
      ? Date.parse(incomingClientModifiedAt)
      : 0
    if (incoming.hasLocalChanges === true
      && options.getLastRemoteLocalChangeAt() > 0
      && incomingClientModifiedAtMs <= options.getLastRemoteLocalChangeAt()) return 'ignored'

    if (incoming.hasLocalChanges === false && hasSameDraftContent(incoming, currentPayload)) {
      options.draftId.value = incoming.draftId ?? options.draftId.value
      options.draftVersion.value = incoming.version ?? null
      options.clientDraftKey.value = incoming.clientDraftKey ?? options.clientDraftKey.value
      options.updatedAt.value = incoming.updatedAt ?? incoming.modifiedAt ?? null
      options.lastSavedAt.value = options.updatedAt.value
      options.lastSaveScope.value = 'server'
      if (options.getLocalRevision() !== options.getPersistedRevision()) {
        options.markCurrentRevisionPersisted()
      }
      options.setLastRemoteLocalChangeAt(0)
      options.draftConflict.value = false
      options.onSaved?.()
      return 'server-acknowledged'
    }

    if (incoming.hasLocalChanges === true && incomingClientModifiedAtMs > 0) {
      options.setLastRemoteLocalChangeAt(incomingClientModifiedAtMs)
    }
    options.draftConflict.value = true
    options.clearAutosaveTimer()
    return 'conflict'
  }

  return { reconcile }
}
