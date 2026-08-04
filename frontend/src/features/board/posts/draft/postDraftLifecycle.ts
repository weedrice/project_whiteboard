import { Storage } from '@/utils/storage'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
import {
  isValidDraftBoardUrl,
  normalizeDraftClientIdentifier,
} from '@/features/board/posts/draft/postDraftContract'
import {
  POST_CONTENT_MAX_LENGTH,
  POST_FILE_MAX_COUNT,
  POST_POLL_MAX_OPTIONS,
  POST_POLL_MIN_OPTIONS,
  POST_POLL_OPTION_MAX_LENGTH,
  POST_POLL_QUESTION_MAX_LENGTH,
  POST_TAG_MAX_COUNT,
  POST_TAG_MAX_LENGTH,
  POST_TITLE_MAX_LENGTH,
} from '@/utils/postForm'

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
  ownerId: string | null
  snapshot: DraftRecoverySnapshot
  rawSize: number
  modifiedAt: number
}

type StoredDraftReadResult =
  | { status: 'valid', snapshot: DraftRecoverySnapshot, rawSize: number }
  | { status: 'preserved-unknown', rawSize: number }
  | { status: 'missing' | 'invalid', rawSize: 0 }

type StoredDraftInventory = {
  entries: StoredDraftEntry[]
  preservedUnknownCount: number
  preservedUnknownBytes: number
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

function getDraftOwnerId(key: string): string | null {
  if (!key.startsWith(DRAFT_STORAGE_PREFIX)) return null
  const ownerId = key.slice(DRAFT_STORAGE_PREFIX.length).split(':', 1)[0]
  return ownerId || null
}

function shouldPreserveUnparseableSnapshot(value: unknown, now: number): boolean {
  if (!isRecord(value)) return false
  if (typeof value.schemaVersion === 'number'
    && Number.isInteger(value.schemaVersion)
    && value.schemaVersion > DRAFT_SNAPSHOT_SCHEMA_VERSION) return true
  if (typeof value.boardUrl === 'string'
    && value.boardUrl.trim()
    && !isValidDraftBoardUrl(value.boardUrl)) return true
  return typeof value.clientModifiedAt === 'string'
    && Date.parse(value.clientModifiedAt) > now + MAX_FUTURE_CLOCK_SKEW_MS
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

function hasDraftPayloadContractViolation(value: Record<string, unknown>): boolean {
  if (typeof value.title === 'string' && value.title.length > POST_TITLE_MAX_LENGTH) return true
  if (typeof value.contents === 'string' && value.contents.length > POST_CONTENT_MAX_LENGTH) return true
  if (Array.isArray(value.tags) && (value.tags.length > POST_TAG_MAX_COUNT
    || value.tags.some((tag) => typeof tag === 'string'
      && (!tag.trim() || tag.length > POST_TAG_MAX_LENGTH)))) return true
  if (Array.isArray(value.fileIds) && value.fileIds.length > POST_FILE_MAX_COUNT) return true
  if (!isRecord(value.poll)) return false
  if (typeof value.poll.question === 'string'
    && (!value.poll.question.trim() || value.poll.question.length > POST_POLL_QUESTION_MAX_LENGTH)) return true
  return Array.isArray(value.poll.options)
    && (value.poll.options.length < POST_POLL_MIN_OPTIONS
      || value.poll.options.length > POST_POLL_MAX_OPTIONS
      || value.poll.options.some((option) => typeof option === 'string'
        && (!option.trim() || option.length > POST_POLL_OPTION_MAX_LENGTH)))
}

export function parseDraftRecoverySnapshot(
  value: unknown,
  now = Date.now(),
): DraftRecoverySnapshot | null {
  if (!isRecord(value)) return null
  if (value.schemaVersion !== undefined && value.schemaVersion !== DRAFT_SNAPSHOT_SCHEMA_VERSION) return null
  if (typeof value.boardUrl !== 'string' || !value.boardUrl.trim()
    || !isValidDraftBoardUrl(value.boardUrl)) return null
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
    || !isOptionalBoolean(value.hasLocalChanges) || !isOptionalBoolean(value.staleReferencesReset)
    || !isOptionalBoolean(value.contractValidationFailed) || !isValidPoll(value.poll)) return null
  if (!isOptionalDate(value.updatedAt) || !isOptionalDate(value.modifiedAt)
    || !isOptionalDate(value.clientModifiedAt)) return null
  if (typeof value.clientModifiedAt === 'string'
    && Date.parse(value.clientModifiedAt) > now + MAX_FUTURE_CLOCK_SKEW_MS) return null

  const fallbackModifiedAt = getSnapshotModifiedAt(value as unknown as DraftRecoverySnapshot) ?? now
  return {
    ...value,
    clientDraftKey: normalizeDraftClientIdentifier(value.clientDraftKey),
    clientInstanceId: normalizeDraftClientIdentifier(value.clientInstanceId),
    contractValidationFailed: value.contractValidationFailed === true
      || hasDraftPayloadContractViolation(value),
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

function readStoredDraftSnapshot(key: string, now = Date.now()): StoredDraftReadResult {
  const raw = Storage.getString(key)
  if (raw == null) return { status: 'missing', rawSize: 0 }
  const rawSize = raw.length * 2
  let parsed: unknown
  try {
    parsed = JSON.parse(raw) as unknown
  } catch {
    Storage.remove(key)
    return { status: 'invalid', rawSize: 0 }
  }
  const snapshot = parseDraftRecoverySnapshot(parsed, now)
  if (!snapshot) {
    if (shouldPreserveUnparseableSnapshot(parsed, now)) {
      return { status: 'preserved-unknown', rawSize }
    }
    Storage.remove(key)
    return { status: 'invalid', rawSize: 0 }
  }
  if (isExpiredDraftSnapshot(snapshot, now)) {
    Storage.remove(key)
    return { status: 'invalid', rawSize: 0 }
  }
  if (!isRecord(parsed)
    || parsed.schemaVersion !== DRAFT_SNAPSHOT_SCHEMA_VERSION
    || parsed.clientModifiedAt !== snapshot.clientModifiedAt
    || parsed.clientDraftKey !== snapshot.clientDraftKey
    || parsed.clientInstanceId !== snapshot.clientInstanceId
    || parsed.contractValidationFailed !== snapshot.contractValidationFailed) {
    Storage.set(key, snapshot)
  }
  return { status: 'valid', snapshot, rawSize }
}

export function loadStoredDraftSnapshot(key: string, now = Date.now()): DraftRecoverySnapshot | null {
  const result = readStoredDraftSnapshot(key, now)
  return result.status === 'valid' ? result.snapshot : null
}

function collectStoredDraftInventory(now = Date.now()): StoredDraftInventory {
  const entries: StoredDraftEntry[] = []
  let preservedUnknownCount = 0
  let preservedUnknownBytes = 0
  for (const key of Storage.keys()) {
    if (!key.startsWith(DRAFT_STORAGE_PREFIX)) continue
    const result = readStoredDraftSnapshot(key, now)
    if (result.status === 'preserved-unknown') {
      preservedUnknownCount++
      preservedUnknownBytes += result.rawSize
      continue
    }
    if (result.status !== 'valid') continue
    entries.push({
      key,
      ownerId: getDraftOwnerId(key),
      snapshot: result.snapshot,
      rawSize: result.rawSize,
      modifiedAt: getSnapshotModifiedAt(result.snapshot) ?? now,
    })
  }
  return { entries, preservedUnknownCount, preservedUnknownBytes }
}

function getProtectedSnapshotKeys(entries: StoredDraftEntry[]): Set<string> {
  const entriesByOwner = new Map<string, StoredDraftEntry[]>()
  for (const entry of entries) {
    const ownerKey = entry.ownerId ?? `unknown:${entry.key}`
    const ownerEntries = entriesByOwner.get(ownerKey) ?? []
    ownerEntries.push(entry)
    entriesByOwner.set(ownerKey, ownerEntries)
  }

  const protectedKeys = new Set<string>()
  for (const ownerEntries of entriesByOwner.values()) {
    ownerEntries
      .sort((left, right) => right.modifiedAt - left.modifiedAt || right.key.localeCompare(left.key))
      .slice(0, MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN)
      .forEach((entry) => protectedKeys.add(entry.key))
  }
  return protectedKeys
}

function getRemovableSyncedEntries(entries: StoredDraftEntry[], protectedKey?: string): StoredDraftEntry[] {
  const protectedKeys = getProtectedSnapshotKeys(entries)
  if (protectedKey) protectedKeys.add(protectedKey)
  return entries
    .filter((entry) => entry.snapshot.hasLocalChanges === false && !protectedKeys.has(entry.key))
    .sort((left, right) => left.modifiedAt - right.modifiedAt || left.key.localeCompare(right.key))
}

export function enforceDraftSnapshotBudget(protectedKey?: string, now = Date.now()) {
  const inventory = collectStoredDraftInventory(now)
  const { entries } = inventory
  let count = entries.length + inventory.preservedUnknownCount
  let totalBytes = inventory.preservedUnknownBytes
    + entries.reduce((total, entry) => total + entry.rawSize, 0)
  let removed = 0

  for (const entry of getRemovableSyncedEntries(entries, protectedKey)) {
    if (count <= MAX_LOCAL_DRAFT_SNAPSHOTS && totalBytes <= MAX_LOCAL_DRAFT_BYTES) break
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
    const result = readStoredDraftSnapshot(key)
    if (result.status === 'preserved-unknown'
      || (result.status === 'valid' && result.snapshot.hasLocalChanges !== false)) count++
  }
  return count
}

export interface DraftSnapshotStoreResult {
  stored: boolean
  rollbackFailedCount: number
}

export function storeDraftSnapshotWithBudgetResult(
  key: string,
  snapshot: DraftRecoverySnapshot,
): DraftSnapshotStoreResult {
  if (readStoredDraftSnapshot(key).status === 'preserved-unknown') {
    return { stored: false, rollbackFailedCount: 0 }
  }
  const versionedSnapshot = {
    ...snapshot,
    schemaVersion: DRAFT_SNAPSHOT_SCHEMA_VERSION,
  }
  let rawSize: number
  try {
    rawSize = JSON.stringify(versionedSnapshot).length * 2
  } catch {
    return { stored: false, rollbackFailedCount: 0 }
  }
  if (rawSize > MAX_LOCAL_DRAFT_BYTES) return { stored: false, rollbackFailedCount: 0 }

  const inventory = collectStoredDraftInventory()
  const candidates = inventory.entries
    .filter((entry) => entry.key !== key)
  const protectedKeys = getProtectedSnapshotKeys(candidates)
  const retainedBytes = candidates
    .filter((entry) => protectedKeys.has(entry.key))
    .reduce((total, entry) => total + entry.rawSize, 0)
  if (rawSize + retainedBytes + inventory.preservedUnknownBytes > MAX_LOCAL_DRAFT_BYTES) {
    return { stored: false, rollbackFailedCount: 0 }
  }

  let projectedCount = candidates.length + inventory.preservedUnknownCount + 1
  let projectedBytes = candidates.reduce(
    (total, entry) => total + entry.rawSize,
    rawSize + inventory.preservedUnknownBytes,
  )
  const removed: StoredDraftEntry[] = []
  const removable = getRemovableSyncedEntries(candidates)

  while (removable.length > 0
    && (projectedCount > MAX_LOCAL_DRAFT_SNAPSHOTS || projectedBytes > MAX_LOCAL_DRAFT_BYTES)) {
    const candidate = removable.shift()!
    Storage.remove(candidate.key)
    removed.push(candidate)
    projectedCount--
    projectedBytes -= candidate.rawSize
  }
  if (projectedCount > MAX_LOCAL_DRAFT_SNAPSHOTS || projectedBytes > MAX_LOCAL_DRAFT_BYTES) {
    return { stored: false, rollbackFailedCount: restoreRemovedDraftEntries(removed) }
  }

  let result = Storage.setWithResult(key, versionedSnapshot)
  while (!result.ok && result.reason === 'quota-exceeded' && removable.length > 0) {
    const candidate = removable.shift()!
    Storage.remove(candidate.key)
    removed.push(candidate)
    result = Storage.setWithResult(key, versionedSnapshot)
  }
  if (result.ok) return { stored: true, rollbackFailedCount: 0 }

  return { stored: false, rollbackFailedCount: restoreRemovedDraftEntries(removed) }
}

export function storeDraftSnapshotWithBudget(key: string, snapshot: DraftRecoverySnapshot): boolean {
  return storeDraftSnapshotWithBudgetResult(key, snapshot).stored
}

function restoreRemovedDraftEntries(entries: StoredDraftEntry[]) {
  let failedCount = 0
  for (const entry of entries) {
    if (!Storage.setWithResult(entry.key, entry.snapshot).ok) failedCount++
  }
  return failedCount
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
