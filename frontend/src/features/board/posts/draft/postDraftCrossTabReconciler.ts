import type { Ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import {
  createDraftContentFingerprint,
  hasSameDraftContent,
  toIsoTime,
  type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'

export type DraftCrossTabReconcileResult =
  | 'ignored'
  | 'server-acknowledged'
  | 'conflict'

export interface DraftCrossTabChange {
  sourceId?: string
  clientInstanceId?: string
  draftId?: number
  clientDraftKey?: string
  version?: number | null
  updatedAt?: string
  modifiedAt?: string
  clientModifiedAt?: string
  hasLocalChanges?: boolean
  contentFingerprint?: string
  boardUrl?: string
  title?: string
  contents?: string
  categoryId?: number | null
  tags?: string[]
  isNotice?: boolean
  isNsfw?: boolean
  isSpoiler?: boolean
  isSecret?: boolean
  fileIds?: number[]
  poll?: PostDraftData['poll']
  seriesId?: number | null
}

interface DraftCrossTabReconcilerOptions {
  clientInstanceId: string
  draftId: Ref<number | null>
  draftVersion: Ref<number | null>
  clientDraftKey: Ref<string>
  updatedAt: Ref<string | null>
  lastSavedAt: Ref<string | null>
  lastSaveScope: Ref<'server' | 'browser' | null>
  draftConflict: Ref<boolean>
  buildPayload: () => PostDraftData
  onSaved?: () => void
  clearAutosaveTimer: () => void
  getLocalRevision: () => number
  getPersistedRevision: () => number
  markCurrentRevisionPersisted: () => void
  getLastRemoteLocalChangeAt: () => number
  setLastRemoteLocalChangeAt: (value: number) => void
}

function hasFullDraftContent(change: DraftCrossTabChange): change is DraftRecoverySnapshot {
  return typeof change.boardUrl === 'string'
}

export function createDraftCrossTabReconciler(options: DraftCrossTabReconcilerOptions) {
  let remoteOrderKey: string | null = null
  let latestRemoteServerVersion: number | null = null
  let latestRemoteServerTimeMs = 0

  const reconcile = (incoming: DraftCrossTabChange): DraftCrossTabReconcileResult => {
    const sourceId = incoming.clientInstanceId ?? incoming.sourceId
    if (!sourceId || sourceId === options.clientInstanceId) return 'ignored'

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
      if (serverRevisionOrder != null && serverRevisionOrder < 0) return 'ignored'
      if (incoming.version != null) {
        latestRemoteServerVersion = Math.max(latestRemoteServerVersion ?? incoming.version, incoming.version)
      }
      latestRemoteServerTimeMs = Math.max(latestRemoteServerTimeMs, incomingServerTimeMs)
    }

    const incomingClientModifiedAt = toIsoTime(incoming.clientModifiedAt)
    const incomingClientModifiedAtMs = incomingClientModifiedAt ? Date.parse(incomingClientModifiedAt) : 0
    if (incoming.hasLocalChanges === true
      && options.getLastRemoteLocalChangeAt() > 0
      && incomingClientModifiedAtMs <= options.getLastRemoteLocalChangeAt()) return 'ignored'

    const currentPayload = options.buildPayload()
    const sameContent = incoming.contentFingerprint != null
      ? incoming.contentFingerprint === createDraftContentFingerprint(currentPayload)
      : hasFullDraftContent(incoming) && hasSameDraftContent(incoming, currentPayload)

    if (incoming.hasLocalChanges === false && sameContent) {
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
