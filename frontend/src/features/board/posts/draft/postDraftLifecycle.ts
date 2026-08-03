import { Storage } from '@/utils/storage'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

export const DRAFT_LOCAL_RETENTION_DAYS = 90
export const MAX_LOCAL_DRAFT_SNAPSHOTS = 50
export const MAX_LOCAL_DRAFT_BYTES = 3 * 1024 * 1024
const DRAFT_STORAGE_PREFIX = 'noviis:draft:'
const RETENTION_MS = DRAFT_LOCAL_RETENTION_DAYS * 24 * 60 * 60 * 1000

type StoredDraftEntry = {
  key: string
  snapshot: DraftRecoverySnapshot
  rawSize: number
  modifiedAt: number
}

function getSnapshotModifiedAt(snapshot: DraftRecoverySnapshot): number | null {
  for (const value of [snapshot.clientModifiedAt, snapshot.updatedAt, snapshot.modifiedAt]) {
    if (!value) continue
    const parsed = Date.parse(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return null
}

function normalizeLegacySnapshot(
  key: string,
  snapshot: DraftRecoverySnapshot,
  now: number,
): DraftRecoverySnapshot {
  if (snapshot.clientModifiedAt) return snapshot
  const fallback = getSnapshotModifiedAt(snapshot) ?? now
  const normalized = { ...snapshot, clientModifiedAt: new Date(fallback).toISOString() }
  Storage.set(key, normalized)
  return normalized
}

export function isExpiredDraftSnapshot(snapshot: DraftRecoverySnapshot, now = Date.now()): boolean {
  const modifiedAt = getSnapshotModifiedAt(snapshot)
  return modifiedAt != null && modifiedAt < now - RETENTION_MS
}

export function loadStoredDraftSnapshot(key: string, now = Date.now()): DraftRecoverySnapshot | null {
  const raw = Storage.getString(key)
  if (raw == null) return null
  let snapshot: DraftRecoverySnapshot
  try {
    const parsed = JSON.parse(raw) as unknown
    if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      Storage.remove(key)
      return null
    }
    snapshot = parsed as DraftRecoverySnapshot
  } catch {
    Storage.remove(key)
    return null
  }
  const normalized = normalizeLegacySnapshot(key, snapshot, now)
  if (isExpiredDraftSnapshot(normalized, now)) {
    Storage.remove(key)
    return null
  }
  return normalized
}

function collectStoredDraftEntries(now = Date.now()): StoredDraftEntry[] {
  const entries: StoredDraftEntry[] = []
  for (const key of Storage.keys()) {
    if (!key.startsWith(DRAFT_STORAGE_PREFIX)) continue
    const snapshot = loadStoredDraftSnapshot(key, now)
    if (!snapshot) continue
    const raw = Storage.getString(key, '') ?? ''
    entries.push({
      key,
      snapshot,
      rawSize: raw.length * 2,
      modifiedAt: getSnapshotModifiedAt(snapshot) ?? now,
    })
  }
  return entries
}

export function enforceDraftSnapshotBudget(protectedKey?: string, now = Date.now()) {
  const entries = collectStoredDraftEntries(now)
    .sort((left, right) => left.modifiedAt - right.modifiedAt || left.key.localeCompare(right.key))
  let count = entries.length
  let totalBytes = entries.reduce((total, entry) => total + entry.rawSize, 0)
  let removed = 0

  for (const entry of entries) {
    if (count <= MAX_LOCAL_DRAFT_SNAPSHOTS && totalBytes <= MAX_LOCAL_DRAFT_BYTES) break
    if (entry.key === protectedKey) continue
    Storage.remove(entry.key)
    count--
    totalBytes -= entry.rawSize
    removed++
  }
  return removed
}

export function cleanupExpiredDraftSnapshots(now = Date.now()) {
  collectStoredDraftEntries(now)
  enforceDraftSnapshotBudget(undefined, now)
}

export function clearStoredDraftSnapshotsForUser(userId: string | number) {
  const userPrefix = `${DRAFT_STORAGE_PREFIX}${userId}:`
  let removed = 0
  for (const key of Storage.keys()) {
    if (!key.startsWith(userPrefix)) continue
    Storage.remove(key)
    removed++
  }
  return removed
}

export function storeDraftSnapshotWithBudget(key: string, snapshot: DraftRecoverySnapshot): boolean {
  let rawSize: number
  try {
    rawSize = JSON.stringify(snapshot).length * 2
  } catch {
    return false
  }
  if (rawSize > MAX_LOCAL_DRAFT_BYTES) return false

  enforceDraftSnapshotBudget(key)
  if (Storage.set(key, snapshot)) {
    enforceDraftSnapshotBudget(key)
    return true
  }

  const candidates = collectStoredDraftEntries()
    .filter((entry) => entry.key !== key)
    .sort((left, right) => left.modifiedAt - right.modifiedAt || left.key.localeCompare(right.key))
  for (const candidate of candidates) {
    Storage.remove(candidate.key)
    if (Storage.set(key, snapshot)) {
      enforceDraftSnapshotBudget(key)
      return true
    }
  }
  return false
}

export function migrateStoredDraftSnapshot(
  legacyKey: string,
  targetKey: string,
  expectedDraftId: number | null | undefined,
) {
  if (legacyKey === targetKey || expectedDraftId == null || Storage.has(targetKey)) return false
  const legacySnapshot = loadStoredDraftSnapshot(legacyKey)
  if (legacySnapshot?.draftId !== expectedDraftId) return false
  if (!storeDraftSnapshotWithBudget(targetKey, legacySnapshot)) return false
  Storage.remove(legacyKey)
  return true
}
