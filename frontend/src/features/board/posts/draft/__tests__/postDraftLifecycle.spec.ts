import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Storage } from '@/utils/storage'
import {
  cleanupExpiredDraftSnapshots,
  clearStoredDraftSnapshotsForUser,
  countUnsyncedStoredDraftSnapshotsForUser,
  loadStoredDraftSnapshot,
  migrateStoredDraftSnapshot,
  storeDraftSnapshot,
} from '@/features/board/posts/draft/postDraftLifecycle'
import {
  isDraftDeletedLocally,
  markDraftDeletedLocally,
  clearDraftTombstonesForUser,
} from '@/features/board/posts/draft/postDraftTombstone'

const CURRENT_SCHEMA_VERSION = 1
const OVERSIZED_BACKUP_CONTENT_LENGTH = 3 * 1024 * 1024

describe('draft browser lifecycle', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-03T00:00:00.000Z'))
    Storage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    Storage.clear()
    vi.useRealTimers()
  })

  it('removes local draft snapshots older than 90 days', () => {
    Storage.set('noviis:draft:1:create:free:new', {
      boardUrl: 'free',
      title: 'expired',
      clientModifiedAt: '2026-04-01T00:00:00.000Z',
    })

    cleanupExpiredDraftSnapshots()

    expect(loadStoredDraftSnapshot('noviis:draft:1:create:free:new')).toBeNull()
    expect(Storage.has('noviis:draft:1:create:free:new')).toBe(false)
  })

  it('keeps recent local drafts and expires deletion tombstones', () => {
    Storage.set('noviis:draft:1:create:free:new', {
      boardUrl: 'free',
      title: 'recent',
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })
    markDraftDeletedLocally(1, 91)

    expect(loadStoredDraftSnapshot('noviis:draft:1:create:free:new')).toEqual(expect.objectContaining({
      title: 'recent',
    }))
    expect(isDraftDeletedLocally(1, 91)).toBe(true)

    vi.setSystemTime(new Date('2026-11-02T00:00:00.000Z'))
    expect(isDraftDeletedLocally(1, 91)).toBe(false)
  })

  it('keeps valid unassociated upload ownership metadata', () => {
    const key = 'noviis:draft:1:create:free:new'
    Storage.set(key, {
      boardUrl: 'free',
      fileIds: [7, 8],
      unassociatedUploadFileIds: [8],
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toEqual(expect.objectContaining({
      fileIds: [7, 8],
      unassociatedUploadFileIds: [8],
    }))
  })

  it('drops invalid unassociated upload metadata without deleting draft content', () => {
    const key = 'noviis:draft:1:create:free:new'
    Storage.set(key, {
      schemaVersion: CURRENT_SCHEMA_VERSION,
      boardUrl: 'free',
      title: 'recover me',
      contents: '<p>important draft</p>',
      fileIds: [7, 8],
      unassociatedUploadFileIds: [8, 9, -1, 'invalid', 8],
      contractValidationFailed: false,
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toEqual(expect.objectContaining({
      title: 'recover me',
      contents: '<p>important draft</p>',
      unassociatedUploadFileIds: [8],
    }))
    expect(Storage.get(key)).toEqual(expect.objectContaining({
      unassociatedUploadFileIds: [8],
    }))
  })

  it('ignores malformed unassociated upload metadata without deleting draft content', () => {
    const key = 'noviis:draft:1:create:free:new'
    Storage.set(key, {
      schemaVersion: CURRENT_SCHEMA_VERSION,
      boardUrl: 'free',
      title: 'recover me',
      fileIds: [7],
      unassociatedUploadFileIds: 'invalid',
      contractValidationFailed: false,
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toEqual(expect.objectContaining({
      title: 'recover me',
      unassociatedUploadFileIds: undefined,
    }))
    expect(Storage.get(key)).not.toHaveProperty('unassociatedUploadFileIds')
  })

  it('migrates legacy boolean tombstones to timestamped records', () => {
    Storage.set('noviis:draft-deleted:1:91', true)

    expect(isDraftDeletedLocally(1, 91)).toBe(true)
    expect(Storage.get('noviis:draft-deleted:1:91')).toEqual({
      deletedAt: '2026-08-03T00:00:00.000Z',
    })
  })

  it('clears only the explicitly logged-out user draft state', () => {
    Storage.set('noviis:draft:1:create:free:new', { title: 'user one' })
    Storage.set('noviis:draft:2:create:free:new', { title: 'user two' })
    markDraftDeletedLocally(1, 91)
    markDraftDeletedLocally(2, 92)

    expect(clearStoredDraftSnapshotsForUser(1)).toBe(1)
    expect(clearDraftTombstonesForUser(1)).toBe(1)

    expect(Storage.has('noviis:draft:1:create:free:new')).toBe(false)
    expect(Storage.has('noviis:draft-deleted:1:91')).toBe(false)
    expect(Storage.has('noviis:draft:2:create:free:new')).toBe(true)
    expect(Storage.has('noviis:draft-deleted:2:92')).toBe(true)
  })

  it('counts only unsynced snapshots owned by the requested user', () => {
    Storage.set('noviis:draft:1:create:free:first', {
      boardUrl: 'free',
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })
    Storage.set('noviis:draft:1:create:free:synced', {
      boardUrl: 'free',
      hasLocalChanges: false,
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })
    Storage.set('noviis:draft:2:create:free:other', {
      boardUrl: 'free',
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })

    expect(countUnsyncedStoredDraftSnapshotsForUser(1)).toBe(1)
  })

  it('counts preserved unknown snapshots conservatively before logout', () => {
    Storage.set('noviis:draft:1:create:free:future-schema', {
      schemaVersion: CURRENT_SCHEMA_VERSION + 1,
      boardUrl: 'free',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })
    Storage.set('noviis:draft:1:create:free:future-clock', {
      boardUrl: 'free',
      clientModifiedAt: '2026-08-05T00:00:01.000Z',
    })

    expect(countUnsyncedStoredDraftSnapshotsForUser(1)).toBe(2)
  })

  it('moves a matching legacy snapshot into a draft-specific storage key', () => {
    const legacyKey = 'noviis:draft:1:create:free:new'
    const targetKey = `${legacyKey}:draft-91`
    Storage.set(legacyKey, {
      boardUrl: 'free',
      draftId: 91,
      title: 'legacy draft',
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })

    expect(migrateStoredDraftSnapshot(legacyKey, targetKey, 91)).toBe(true)
    expect(Storage.get(targetKey)).toEqual(expect.objectContaining({ draftId: 91 }))
    expect(Storage.has(legacyKey)).toBe(false)
  })

  it('does not move a legacy snapshot belonging to another draft', () => {
    const legacyKey = 'noviis:draft:1:create:free:new'
    const targetKey = `${legacyKey}:draft-92`
    Storage.set(legacyKey, {
      boardUrl: 'free',
      draftId: 91,
      title: 'different draft',
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })

    expect(migrateStoredDraftSnapshot(legacyKey, targetKey, 92)).toBe(false)
    expect(Storage.has(legacyKey)).toBe(true)
    expect(Storage.has(targetKey)).toBe(false)
  })

  it('keeps the newer unsynced snapshot when both draft storage keys exist', () => {
    const legacyKey = 'noviis:draft:1:create:free:new'
    const targetKey = `${legacyKey}:draft-91`
    Storage.set(legacyKey, {
      boardUrl: 'free',
      draftId: 91,
      title: 'new local content',
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-03T00:02:00.000Z',
    })
    Storage.set(targetKey, {
      boardUrl: 'free',
      draftId: 91,
      title: 'old server content',
      hasLocalChanges: false,
      clientModifiedAt: '2026-08-03T00:01:00.000Z',
    })

    expect(migrateStoredDraftSnapshot(legacyKey, targetKey, 91)).toBe(true)
    expect(Storage.get(targetKey)).toEqual(expect.objectContaining({
      title: 'new local content',
      hasLocalChanges: true,
    }))
    expect(Storage.has(legacyKey)).toBe(false)
  })

  it('does not replace a newer unsynced draft-specific snapshot during migration', () => {
    const legacyKey = 'noviis:draft:1:create:free:new'
    const targetKey = `${legacyKey}:draft-91`
    Storage.set(legacyKey, {
      boardUrl: 'free',
      draftId: 91,
      title: 'old server content',
      hasLocalChanges: false,
      clientModifiedAt: '2026-08-03T00:01:00.000Z',
    })
    Storage.set(targetKey, {
      boardUrl: 'free',
      draftId: 91,
      title: 'new local content',
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-03T00:02:00.000Z',
    })

    expect(migrateStoredDraftSnapshot(legacyKey, targetKey, 91)).toBe(true)
    expect(Storage.get(targetKey)).toEqual(expect.objectContaining({
      title: 'new local content',
      hasLocalChanges: true,
    }))
    expect(Storage.has(legacyKey)).toBe(false)
  })

  it('expires legacy snapshots using the server timestamp when the client timestamp is missing', () => {
    const key = 'noviis:draft:1:create:free:legacy'
    Storage.set(key, {
      boardUrl: 'free',
      title: 'old legacy draft',
      modifiedAt: '2026-04-01T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toBeNull()
    expect(Storage.has(key)).toBe(false)
  })

  it('timestamps an undated legacy snapshot when it is first loaded', () => {
    const key = 'noviis:draft:1:create:free:undated'
    Storage.set(key, { boardUrl: 'free', title: 'undated draft' })

    expect(loadStoredDraftSnapshot(key)).toEqual(expect.objectContaining({
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    }))
    expect(Storage.get(key)).toEqual(expect.objectContaining({
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
      schemaVersion: CURRENT_SCHEMA_VERSION,
    }))
  })

  it('preserves snapshots written by a future schema version', () => {
    const key = 'noviis:draft:1:create:free:future-schema'
    Storage.set(key, {
      schemaVersion: CURRENT_SCHEMA_VERSION + 1,
      boardUrl: 'free',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toBeNull()
    expect(Storage.has(key)).toBe(true)
  })

  it('expires future schema snapshots after the local retention period', () => {
    const key = 'noviis:draft:1:create:free:expired-future-schema'
    Storage.set(key, {
      schemaVersion: CURRENT_SCHEMA_VERSION + 1,
      boardUrl: 'free',
      clientModifiedAt: '2026-04-01T00:00:00.000Z',
    })

    cleanupExpiredDraftSnapshots()

    expect(Storage.has(key)).toBe(false)
  })

  it('does not overwrite a snapshot written by a future schema version', () => {
    const key = 'noviis:draft:1:create:free:future-schema'
    Storage.set(key, {
      schemaVersion: CURRENT_SCHEMA_VERSION + 1,
      boardUrl: 'free',
      title: 'newer app draft',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(storeDraftSnapshot(key, {
      boardUrl: 'free',
      title: 'older app draft',
      clientModifiedAt: '2026-08-03T00:00:01.000Z',
    })).toBe(false)
    expect(Storage.get(key)).toEqual(expect.objectContaining({
      schemaVersion: CURRENT_SCHEMA_VERSION + 1,
      title: 'newer app draft',
    }))
  })

  it('removes malformed snapshots but preserves implausible future timestamps', () => {
    const malformedKey = 'noviis:draft:1:create:free:malformed'
    const futureKey = 'noviis:draft:1:create:free:future'
    Storage.set(malformedKey, {
      boardUrl: 'free',
      fileIds: [1, 'invalid'],
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })
    Storage.set(futureKey, {
      boardUrl: 'free',
      clientModifiedAt: '2026-08-05T00:00:01.000Z',
    })

    expect(loadStoredDraftSnapshot(malformedKey)).toBeNull()
    expect(loadStoredDraftSnapshot(futureKey)).toBeNull()
    expect(Storage.has(malformedKey)).toBe(false)
    expect(Storage.has(futureKey)).toBe(true)
  })

  it('normalizes invalid client identifiers without discarding draft content', () => {
    const key = 'noviis:draft:1:create:free:invalid-identifiers'
    Storage.set(key, {
      schemaVersion: CURRENT_SCHEMA_VERSION,
      boardUrl: 'free',
      title: 'keep this content',
      clientDraftKey: 'short',
      clientInstanceId: 'contains spaces',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toEqual(expect.objectContaining({
      title: 'keep this content',
      clientDraftKey: undefined,
      clientInstanceId: undefined,
    }))
    expect(Storage.get(key)).not.toHaveProperty('clientDraftKey')
    expect(Storage.get(key)).not.toHaveProperty('clientInstanceId')
  })

  it('restores content outside current limits as requiring correction', () => {
    const key = 'noviis:draft:1:create:free:oversized-content'
    Storage.set(key, {
      boardUrl: 'free',
      title: 'x'.repeat(201),
      poll: {
        question: 'Pick one',
        options: ['only one'],
      },
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toEqual(expect.objectContaining({
      title: 'x'.repeat(201),
      contractValidationFailed: true,
    }))
    expect(Storage.has(key)).toBe(true)
  })

  it('does not mark an incomplete draft poll as a contract violation', () => {
    const key = 'noviis:draft:1:create:free:incomplete-poll'
    Storage.set(key, {
      boardUrl: 'free',
      poll: { question: '', options: [''] },
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toEqual(expect.objectContaining({
      contractValidationFailed: false,
    }))
  })

  it('quarantines snapshots with an invalid board identity instead of deleting them', () => {
    const key = 'noviis:draft:1:create:free:invalid-board'
    Storage.set(key, {
      boardUrl: 'Free Board',
      title: 'recoverable content',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toBeNull()
    expect(Storage.has(key)).toBe(true)
    expect(countUnsyncedStoredDraftSnapshotsForUser(1)).toBe(1)
  })

  it('removes object snapshots without a board identity', () => {
    const key = 'noviis:draft:1:create:free:missing-board'
    Storage.set(key, { title: 'orphaned draft' })

    expect(loadStoredDraftSnapshot(key)).toBeNull()
    expect(Storage.has(key)).toBe(false)
  })

  it('removes a corrupted draft snapshot when it is loaded', () => {
    const key = 'noviis:draft:1:create:free:corrupted'
    localStorage.setItem(key, '{not-json')

    expect(loadStoredDraftSnapshot(key)).toBeNull()
    expect(Storage.has(key)).toBe(false)
  })

  it('removes a non-object draft snapshot when it is loaded', () => {
    const key = 'noviis:draft:1:create:free:invalid'
    localStorage.setItem(key, JSON.stringify('not-a-draft'))

    expect(loadStoredDraftSnapshot(key)).toBeNull()
    expect(Storage.has(key)).toBe(false)
  })

  it('stores one latest backup per editor key without evicting another draft', () => {
    const firstKey = 'noviis:draft:1:create:free:first'
    const secondKey = 'noviis:draft:1:create:free:second'
    Storage.set(firstKey, {
      boardUrl: 'free',
      title: 'first draft',
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(storeDraftSnapshot(secondKey, {
      boardUrl: 'free',
      title: 'second draft',
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-03T00:01:00.000Z',
    })).toBe(true)
    expect(Storage.get(firstKey)).toEqual(expect.objectContaining({ title: 'first draft' }))
    expect(Storage.get(secondKey)).toEqual(expect.objectContaining({ title: 'second draft' }))
  })

  it('rejects an oversized backup without deleting another draft', () => {
    const existingKey = 'noviis:draft:1:create:free:existing'
    Storage.set(existingKey, {
      boardUrl: 'free',
      title: 'keep me',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(storeDraftSnapshot('noviis:draft:1:create:free:oversized', {
      boardUrl: 'free',
      contents: 'x'.repeat(OVERSIZED_BACKUP_CONTENT_LENGTH),
      clientModifiedAt: '2026-08-03T00:01:00.000Z',
    })).toBe(false)
    expect(Storage.get(existingKey)).toEqual(expect.objectContaining({ title: 'keep me' }))
  })

  it('does not evict or retry other backups when the active write fails', () => {
    const existingKey = 'noviis:draft:1:create:free:existing'
    Storage.set(existingKey, {
      boardUrl: 'free',
      title: 'keep me',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })
    const setWithResult = vi.spyOn(Storage, 'setWithResult').mockReturnValue({
      ok: false,
      reason: 'quota-exceeded',
    })

    expect(storeDraftSnapshot('noviis:draft:1:create:free:active', {
      boardUrl: 'free',
      title: 'cannot write',
      clientModifiedAt: '2026-08-03T00:01:00.000Z',
    })).toBe(false)
    expect(setWithResult).toHaveBeenCalledTimes(1)
    expect(Storage.get(existingKey)).toEqual(expect.objectContaining({ title: 'keep me' }))
  })

  it('cleanup removes only malformed or expired backups and keeps valid backups', () => {
    const recentKey = 'noviis:draft:1:create:free:recent'
    const expiredKey = 'noviis:draft:1:create:free:expired'
    const malformedKey = 'noviis:draft:1:create:free:malformed-cleanup'
    Storage.set(recentKey, {
      boardUrl: 'free',
      title: 'recent',
      clientModifiedAt: '2026-08-02T00:00:00.000Z',
    })
    Storage.set(expiredKey, {
      boardUrl: 'free',
      title: 'expired',
      clientModifiedAt: '2026-04-01T00:00:00.000Z',
    })
    localStorage.setItem(malformedKey, '{invalid')

    cleanupExpiredDraftSnapshots()

    expect(Storage.has(recentKey)).toBe(true)
    expect(Storage.has(expiredKey)).toBe(false)
    expect(Storage.has(malformedKey)).toBe(false)
  })
})
