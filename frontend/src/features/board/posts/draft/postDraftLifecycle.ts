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
  POST_POLL_OPTION_MAX_LENGTH,
  POST_POLL_QUESTION_MAX_LENGTH,
  POST_TAG_MAX_COUNT,
  POST_TAG_MAX_LENGTH,
  POST_TITLE_MAX_LENGTH,
  containsUnsafePostTitleHtml,
} from '@/utils/postForm'

export const DRAFT_LOCAL_RETENTION_DAYS = 90
export const DRAFT_SNAPSHOT_SCHEMA_VERSION = 1
export const MAX_LOCAL_DRAFT_BACKUP_BYTES = 3 * 1024 * 1024
const DRAFT_STORAGE_PREFIX = 'noviis:draft:'
const RETENTION_MS = DRAFT_LOCAL_RETENTION_DAYS * 24 * 60 * 60 * 1000
const MAX_FUTURE_CLOCK_SKEW_MS = 24 * 60 * 60 * 1000

type StoredDraftReadResult =
  | { status: 'valid', snapshot: DraftRecoverySnapshot, rawSize: number }
  | { status: 'preserved-unknown', rawSize: number, modifiedAt: number }
  | { status: 'missing' | 'invalid', rawSize: 0 }

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

function getUnparseableSnapshotModifiedAt(value: Record<string, unknown>): number | null {
  for (const field of ['clientModifiedAt', 'updatedAt', 'modifiedAt']) {
    const candidate = value[field]
    if (typeof candidate !== 'string') continue
    const parsed = Date.parse(candidate)
    if (Number.isFinite(parsed)) return parsed
  }
  return null
}

function shouldPreserveUnparseableSnapshot(value: unknown, now: number): boolean {
  if (!isRecord(value)) return false
  const hasFutureSchema = typeof value.schemaVersion === 'number'
    && Number.isInteger(value.schemaVersion)
    && value.schemaVersion > DRAFT_SNAPSHOT_SCHEMA_VERSION
  const hasUnknownBoardFormat = typeof value.boardUrl === 'string'
    && value.boardUrl.trim()
    && !isValidDraftBoardUrl(value.boardUrl)
  const hasFutureTimestamp = typeof value.clientModifiedAt === 'string'
    && Date.parse(value.clientModifiedAt) > now + MAX_FUTURE_CLOCK_SKEW_MS
  if (!hasFutureSchema && !hasUnknownBoardFormat && !hasFutureTimestamp) return false
  const modifiedAt = getUnparseableSnapshotModifiedAt(value)
  return modifiedAt == null || modifiedAt >= now - RETENTION_MS
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
  if (typeof value.title === 'string' && containsUnsafePostTitleHtml(value.title)) return true
  if (typeof value.contents === 'string' && value.contents.length > POST_CONTENT_MAX_LENGTH) return true
  if (Array.isArray(value.tags) && (value.tags.length > POST_TAG_MAX_COUNT
    || value.tags.some((tag) => typeof tag === 'string'
      && (!tag.trim() || tag.length > POST_TAG_MAX_LENGTH)))) return true
  if (Array.isArray(value.fileIds) && value.fileIds.length > POST_FILE_MAX_COUNT) return true
  if (!isRecord(value.poll)) return false
  if (typeof value.poll.question === 'string'
    && value.poll.question.length > POST_POLL_QUESTION_MAX_LENGTH) return true
  return Array.isArray(value.poll.options)
    && (value.poll.options.length > POST_POLL_MAX_OPTIONS
      || value.poll.options.some((option) => typeof option === 'string'
        && option.length > POST_POLL_OPTION_MAX_LENGTH))
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
  const draftFileIds = new Set(Array.isArray(value.fileIds) ? value.fileIds : [])
  const unassociatedUploadFileIds = Array.isArray(value.unassociatedUploadFileIds)
    ? [...new Set(value.unassociatedUploadFileIds
        .filter((id): id is number => Number.isInteger(id) && Number(id) > 0 && draftFileIds.has(id as number)))]
        .slice(0, POST_FILE_MAX_COUNT)
    : []
  return {
    ...value,
    unassociatedUploadFileIds: unassociatedUploadFileIds.length > 0
      ? unassociatedUploadFileIds
      : undefined,
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
      const modifiedAt = isRecord(parsed) ? getUnparseableSnapshotModifiedAt(parsed) : null
      return { status: 'preserved-unknown', rawSize, modifiedAt: Math.min(modifiedAt ?? now, now) }
    }
    Storage.remove(key)
    return { status: 'invalid', rawSize: 0 }
  }
  if (isExpiredDraftSnapshot(snapshot, now)) {
    Storage.remove(key)
    return { status: 'invalid', rawSize: 0 }
  }
  const normalizedOwnershipChanged = isRecord(parsed)
    && JSON.stringify(parsed.unassociatedUploadFileIds)
      !== JSON.stringify(snapshot.unassociatedUploadFileIds)
  let normalizedRawSize = rawSize
  if (!isRecord(parsed)
    || parsed.schemaVersion !== DRAFT_SNAPSHOT_SCHEMA_VERSION
    || parsed.clientModifiedAt !== snapshot.clientModifiedAt
    || parsed.clientDraftKey !== snapshot.clientDraftKey
    || parsed.clientInstanceId !== snapshot.clientInstanceId
    || parsed.contractValidationFailed !== snapshot.contractValidationFailed
    || normalizedOwnershipChanged) {
    if (Storage.set(key, snapshot)) {
      normalizedRawSize = JSON.stringify(snapshot).length * 2
    }
  }
  return { status: 'valid', snapshot, rawSize: normalizedRawSize }
}

export function loadStoredDraftSnapshot(key: string, now = Date.now()): DraftRecoverySnapshot | null {
  const result = readStoredDraftSnapshot(key, now)
  return result.status === 'valid' ? result.snapshot : null
}

export function cleanupExpiredDraftSnapshots(now = Date.now()) {
  for (const key of Storage.keys()) {
    if (!key.startsWith(DRAFT_STORAGE_PREFIX)) continue
    readStoredDraftSnapshot(key, now)
  }
}

export function clearStoredDraftSnapshotsForUser(userId: string | number) {
  const userPrefix = `${DRAFT_STORAGE_PREFIX}${userId}:`
  let removed = 0
  for (const key of Storage.keys()) {
    if (!key.startsWith(userPrefix)) continue
    if (Storage.remove(key)) removed++
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

/**
 * Stores only the latest emergency backup for the supplied editor key.
 * Other drafts are never inspected, evicted, or rewritten to make space.
 */
export function storeDraftSnapshot(key: string, snapshot: DraftRecoverySnapshot): boolean {
  if (readStoredDraftSnapshot(key).status === 'preserved-unknown') return false

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
  if (rawSize > MAX_LOCAL_DRAFT_BACKUP_BYTES) return false
  return Storage.setWithResult(key, versionedSnapshot).ok
}

export function migrateStoredDraftSnapshot(
  legacyKey: string,
  targetKey: string,
  expectedDraftId: number | null | undefined,
  expectedClientDraftKey?: string | null,
) {
  if (legacyKey === targetKey || expectedDraftId == null) return false
  const legacySnapshot = loadStoredDraftSnapshot(legacyKey)
  const matchesDraftId = legacySnapshot?.draftId === expectedDraftId
  const matchesClientKey = legacySnapshot?.draftId == null
    && expectedClientDraftKey != null
    && legacySnapshot?.clientDraftKey === expectedClientDraftKey
  if (!legacySnapshot || (!matchesDraftId && !matchesClientKey)) return false

  const targetSnapshot = loadStoredDraftSnapshot(targetKey)
  if (targetSnapshot?.draftId != null && targetSnapshot.draftId !== expectedDraftId) return false

  if (targetSnapshot) {
    if (targetSnapshot.hasLocalChanges === true) return Storage.remove(legacyKey)
    if (legacySnapshot.hasLocalChanges !== true) {
      const legacyModifiedAt = getSnapshotModifiedAt(legacySnapshot) ?? 0
      const targetModifiedAt = getSnapshotModifiedAt(targetSnapshot) ?? 0
      if (targetModifiedAt >= legacyModifiedAt) return Storage.remove(legacyKey)
    }
  }

  if (!storeDraftSnapshot(targetKey, {
    ...legacySnapshot,
    draftId: expectedDraftId,
  })) return false
  return Storage.remove(legacyKey)
}
