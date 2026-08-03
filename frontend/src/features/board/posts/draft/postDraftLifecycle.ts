import { Storage } from '@/utils/storage'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

export const DRAFT_LOCAL_RETENTION_DAYS = 90
export const DRAFT_SNAPSHOT_SCHEMA_VERSION = 1
export const MAX_LOCAL_DRAFT_SNAPSHOTS = 50
export const MAX_LOCAL_DRAFT_BYTES = 3 * 1024 * 1024
export const MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN = 3
const DRAFT_STORAGE_PREFIX = 'noviis:draft:'
const RETENTION_MS = DRAFT_LOCAL_RETENTION_DAYS * 24 * 60 * 60 * 1000
const MAX_FUTURE_CLOCK_SKEW_MS = 24 * 60 * 60 * 1000

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

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function isOptionalString(value: unknown): value is string | undefined {
  return value === undefined || typeof value === 'string'
}

function isOptionalBoolean(value: unknown): value is boolean | undefined {
  return value === undefined || typeof value === 'boolean'
}

function isOptionalPositiveInteger(value: unknown, nullable = false): boolean {
  return value === undefined || (nullable && value === null)
    || (typeof value === 'number' && Number.isInteger(value) && value > 0)
}

function isOptionalDate(value: unknown): boolean {
  return value === undefined || (typeof value === 'string' && Number.isFinite(Date.parse(value)))
}

function isValidPoll(value: unknown): boolean {
  if (value === undefined || value === null) return true
  if (!isRecord(value)) return false
  return typeof value.question === 'string'
    && Array.isArray(value.options)
    && value.options.every((option) => typeof option === 'string')
    && isOptionalBoolean(value.multipleChoiceEnabled)
    && isOptionalBoolean(value.anonymousEnabled)
    && (value.closesAt === null || isOptionalDate(value.closesAt))
}

export function parseDraftRecoverySnapshot(
  value: unknown,
  now = Date.now(),
): DraftRecoverySnapshot | null {
  if (!isRecord(value)) return null
  if (value.schemaVersion !== undefined && value.schemaVersion !== DRAFT_SNAPSHOT_SCHEMA_VERSION) return null
  if (typeof value.boardUrl !== 'string' || !value.boardUrl.trim() || value.boardUrl.length > 255) return null
  if (!isOptionalString(value.title) || !isOptionalString(value.contents)
    || !isOptionalString(value.clientDraftKey) || !isOptionalString(value.clientInstanceId)) return null
  if (!isOptionalPositiveInteger(value.draftId) || !isOptionalPositiveInteger(value.categoryId, true)
    || !isOptionalPositiveInteger(value.seriesId, true) || !isOptionalPositiveInteger(value.originalPostId)) return null
  if (value.version !== undefined
    && (typeof value.version !== 'number' || !Number.isInteger(value.version) || value.version < 0)) return null
  if (value.tags !== undefined
    && (!Array.isArray(value.tags) || !value.tags.every((tag) => typeof tag === 'string'))) return null
  if (value.fileIds !== undefined
    && (!Array.isArray(value.fileIds) || !value.fileIds.every((id) => Number.isInteger(id) && id > 0))) return null
  if (!isOptionalBoolean(value.isNotice) || !isOptionalBoolean(value.isNsfw)
    || !isOptionalBoolean(value.isSpoiler) || !isOptionalBoolean(value.isSecret)
    || !isOptionalBoolean(value.hasLocalChanges) || !isValidPoll(value.poll)) return null
  if (!isOptionalDate(value.updatedAt) || !isOptionalDate(value.modifiedAt)
    || !isOptionalDate(value.clientModifiedAt)) return null
  if (typeof value.clientModifiedAt === 'string'
    && Date.parse(value.clientModifiedAt) > now + MAX_FUTURE_CLOCK_SKEW_MS) return null

  const fallbackModifiedAt = getSnapshotModifiedAt(value as unknown as DraftRecoverySnapshot) ?? now
  return {
    ...value,
    schemaVersion: DRAFT_SNAPSHOT_SCHEMA_VERSION,
    clientModifiedAt: typeof value.clientModifiedAt === 'string'
      ? value.clientModifiedAt
      : new Date(fallbackModifiedAt).toISOString(),
  } as DraftRecoverySnapshot
}

export function isExpiredDraftSnapshot(snapshot: DraftRecoverySnapshot, now = Date.now()): boolean {
  const modifiedAt = getSnapshotModifiedAt(snapshot)
  return modifiedAt != null && modifiedAt < now - RETENTION_MS
}

export function loadStoredDraftSnapshot(key: string, now = Date.now()): DraftRecoverySnapshot | null {
  const raw = Storage.getString(key)
  if (raw == null) return null
  let parsed: unknown
  try {
    parsed = JSON.parse(raw) as unknown
  } catch {
    Storage.remove(key)
    return null
  }
  const snapshot = parseDraftRecoverySnapshot(parsed, now)
  if (!snapshot) {
    Storage.remove(key)
    return null
  }
  if (isExpiredDraftSnapshot(snapshot, now)) {
    Storage.remove(key)
    return null
  }
  if (!isRecord(parsed)
    || parsed.schemaVersion !== DRAFT_SNAPSHOT_SCHEMA_VERSION
    || parsed.clientModifiedAt !== snapshot.clientModifiedAt) {
    Storage.set(key, snapshot)
  }
  return snapshot
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

export function countUnsyncedStoredDraftSnapshotsForUser(userId: string | number) {
  const userPrefix = `${DRAFT_STORAGE_PREFIX}${userId}:`
  let count = 0
  for (const key of Storage.keys()) {
    if (!key.startsWith(userPrefix)) continue
    const snapshot = loadStoredDraftSnapshot(key)
    if (snapshot && snapshot.hasLocalChanges !== false) count++
  }
  return count
}

export function storeDraftSnapshotWithBudget(key: string, snapshot: DraftRecoverySnapshot): boolean {
  const versionedSnapshot = {
    ...snapshot,
    schemaVersion: DRAFT_SNAPSHOT_SCHEMA_VERSION,
  }
  let rawSize: number
  try {
    rawSize = JSON.stringify(versionedSnapshot).length * 2
  } catch {
    return false
  }
  if (rawSize > MAX_LOCAL_DRAFT_BYTES) return false

  const candidates = collectStoredDraftEntries()
    .filter((entry) => entry.key !== key)
    .sort((left, right) => left.modifiedAt - right.modifiedAt || left.key.localeCompare(right.key))
  const protectedKeys = new Set(candidates
    .slice(-MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN)
    .map((entry) => entry.key))
  const retainedBytes = candidates
    .filter((entry) => protectedKeys.has(entry.key))
    .reduce((total, entry) => total + entry.rawSize, 0)
  if (rawSize + retainedBytes > MAX_LOCAL_DRAFT_BYTES) return false

  let projectedCount = candidates.length + 1
  let projectedBytes = candidates.reduce((total, entry) => total + entry.rawSize, rawSize)
  const removed: StoredDraftEntry[] = []
  const removable = candidates.filter((entry) => !protectedKeys.has(entry.key))

  while (removable.length > 0
    && (projectedCount > MAX_LOCAL_DRAFT_SNAPSHOTS || projectedBytes > MAX_LOCAL_DRAFT_BYTES)) {
    const candidate = removable.shift()!
    Storage.remove(candidate.key)
    removed.push(candidate)
    projectedCount--
    projectedBytes -= candidate.rawSize
  }
  if (projectedCount > MAX_LOCAL_DRAFT_SNAPSHOTS || projectedBytes > MAX_LOCAL_DRAFT_BYTES) {
    restoreRemovedDraftEntries(removed)
    return false
  }

  let result = Storage.setWithResult(key, versionedSnapshot)
  while (!result.ok && result.reason === 'quota-exceeded' && removable.length > 0) {
    const candidate = removable.shift()!
    Storage.remove(candidate.key)
    removed.push(candidate)
    result = Storage.setWithResult(key, versionedSnapshot)
  }
  if (result.ok) return true

  restoreRemovedDraftEntries(removed)
  return false
}

function restoreRemovedDraftEntries(entries: StoredDraftEntry[]) {
  for (const entry of entries) Storage.set(entry.key, entry.snapshot)
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
