import { Storage } from '@/utils/storage'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

export const DRAFT_LOCAL_RETENTION_DAYS = 90
const DRAFT_STORAGE_PREFIX = 'noviis:draft:'
const RETENTION_MS = DRAFT_LOCAL_RETENTION_DAYS * 24 * 60 * 60 * 1000

export function isExpiredDraftSnapshot(snapshot: DraftRecoverySnapshot, now = Date.now()): boolean {
  if (!snapshot.clientModifiedAt) return false
  const modifiedAt = Date.parse(snapshot.clientModifiedAt)
  return Number.isFinite(modifiedAt) && modifiedAt < now - RETENTION_MS
}

export function loadStoredDraftSnapshot(key: string, now = Date.now()): DraftRecoverySnapshot | null {
  const snapshot = Storage.get<DraftRecoverySnapshot>(key, null)
  if (!snapshot || !isExpiredDraftSnapshot(snapshot, now)) return snapshot
  Storage.remove(key)
  return null
}

export function cleanupExpiredDraftSnapshots(now = Date.now()) {
  for (const key of Storage.keys()) {
    if (!key.startsWith(DRAFT_STORAGE_PREFIX)) continue
    loadStoredDraftSnapshot(key, now)
  }
}
