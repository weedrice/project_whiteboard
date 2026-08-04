import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Storage } from '@/utils/storage'
import {
  cleanupExpiredDraftSnapshots,
  clearStoredDraftSnapshotsForUser,
  countUnsyncedStoredDraftSnapshotsForUser,
  DRAFT_SNAPSHOT_SCHEMA_VERSION,
  enforceDraftSnapshotBudget,
  loadStoredDraftSnapshot,
  MAX_LOCAL_DRAFT_BYTES,
  MAX_LOCAL_DRAFT_SNAPSHOTS,
  MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN,
  migrateStoredDraftSnapshot,
  storeDraftSnapshotWithBudget,
} from '@/features/board/posts/draft/postDraftLifecycle'
import {
  isDraftDeletedLocally,
  markDraftDeletedLocally,
  clearDraftTombstonesForUser,
} from '@/features/board/posts/draft/postDraftTombstone'

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
      schemaVersion: DRAFT_SNAPSHOT_SCHEMA_VERSION,
    }))
  })

  it('preserves snapshots written by a future schema version', () => {
    const key = 'noviis:draft:1:create:free:future-schema'
    Storage.set(key, {
      schemaVersion: DRAFT_SNAPSHOT_SCHEMA_VERSION + 1,
      boardUrl: 'free',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })

    expect(loadStoredDraftSnapshot(key)).toBeNull()
    expect(Storage.has(key)).toBe(true)
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

  it('evicts the oldest snapshots over the local count limit while protecting the active key', () => {
    for (let index = 0; index < MAX_LOCAL_DRAFT_SNAPSHOTS + 2; index++) {
      Storage.set(`noviis:draft:1:create:free:${index}`, {
        boardUrl: 'free',
        title: `draft ${index}`,
        hasLocalChanges: false,
        clientModifiedAt: new Date(Date.UTC(2026, 6, 1, 0, index)).toISOString(),
      })
    }
    const protectedKey = 'noviis:draft:1:create:free:0'

    expect(enforceDraftSnapshotBudget(protectedKey)).toBe(2)

    const remainingKeys = Storage.keys().filter((key) => key.startsWith('noviis:draft:'))
    expect(remainingKeys).toHaveLength(MAX_LOCAL_DRAFT_SNAPSHOTS)
    expect(Storage.has(protectedKey)).toBe(true)
    expect(Storage.has('noviis:draft:1:create:free:1')).toBe(false)
    expect(Storage.has('noviis:draft:1:create:free:2')).toBe(false)
  })

  it('never evicts unsynced snapshots to satisfy the local budget', () => {
    for (let index = 0; index < MAX_LOCAL_DRAFT_SNAPSHOTS + 1; index++) {
      Storage.set(`noviis:draft:1:create:free:${index}`, {
        boardUrl: 'free',
        title: `unsynced ${index}`,
        hasLocalChanges: true,
        clientModifiedAt: new Date(Date.UTC(2026, 6, 1, 0, index)).toISOString(),
      })
    }

    expect(enforceDraftSnapshotBudget()).toBe(0)
    expect(Storage.keys().filter((key) => key.startsWith('noviis:draft:')))
      .toHaveLength(MAX_LOCAL_DRAFT_SNAPSHOTS + 1)
  })

  it('retains the newest synced backups separately for each user', () => {
    for (const ownerId of [1, 2]) {
      for (let index = 0; index < MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN + 1; index++) {
        Storage.set(`noviis:draft:${ownerId}:create:free:${index}`, {
          boardUrl: 'free',
          title: `user ${ownerId} draft ${index}`,
          hasLocalChanges: false,
          clientModifiedAt: new Date(Date.UTC(2026, 6, ownerId, 0, index)).toISOString(),
        })
      }
    }
    const originalSet = Storage.setWithResult.bind(Storage)
    let targetAttempts = 0
    vi.spyOn(Storage, 'setWithResult').mockImplementation((key, value) => {
      if (key === 'noviis:draft:1:create:free:current' && targetAttempts++ === 0) {
        return { ok: false, reason: 'quota-exceeded' }
      }
      return originalSet(key, value)
    })

    expect(storeDraftSnapshotWithBudget('noviis:draft:1:create:free:current', {
      boardUrl: 'free',
      title: 'current draft',
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })).toBe(true)
    expect(Storage.has('noviis:draft:1:create:free:0')).toBe(false)
    expect(Storage.has('noviis:draft:2:create:free:0')).toBe(true)
    expect(Storage.keys().filter((key) => key.startsWith('noviis:draft:2:')))
      .toHaveLength(MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN + 1)
  })

  it('reclaims old snapshots on quota errors while retaining recent backups', () => {
    const targetKey = 'noviis:draft:1:create:free:current'
    for (let index = 0; index < MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN + 1; index++) {
      Storage.set(`noviis:draft:1:create:free:${index}`, {
        boardUrl: 'free',
        title: `draft ${index}`,
        hasLocalChanges: false,
        clientModifiedAt: new Date(Date.UTC(2026, 7, 3, 0, index)).toISOString(),
      })
    }
    const originalSet = Storage.setWithResult.bind(Storage)
    let targetAttempts = 0
    const setSpy = vi.spyOn(Storage, 'setWithResult').mockImplementation((key, value) => {
      if (key === targetKey && targetAttempts++ === 0) {
        return { ok: false, reason: 'quota-exceeded' }
      }
      return originalSet(key, value)
    })

    expect(storeDraftSnapshotWithBudget(targetKey, {
      boardUrl: 'free',
      title: 'current draft',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })).toBe(true)
    expect(Storage.has('noviis:draft:1:create:free:0')).toBe(false)
    expect(Storage.keys().filter((key) => key.startsWith('noviis:draft:1:create:free:')))
      .toHaveLength(MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN + 1)
    expect(Storage.has(targetKey)).toBe(true)
    setSpy.mockRestore()
  })

  it('does not evict drafts for non-quota storage failures', () => {
    const existingKey = 'noviis:draft:1:create:free:existing'
    const targetKey = 'noviis:draft:1:create:free:current'
    Storage.set(existingKey, {
      boardUrl: 'free',
      title: 'keep me',
      clientModifiedAt: '2026-08-01T00:00:00.000Z',
    })
    vi.spyOn(Storage, 'setWithResult').mockReturnValue({ ok: false, reason: 'unavailable' })

    expect(storeDraftSnapshotWithBudget(targetKey, {
      boardUrl: 'free',
      title: 'current draft',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })).toBe(false)
    expect(Storage.has(existingKey)).toBe(true)
  })

  it('restores evicted drafts when quota retries never succeed', () => {
    const targetKey = 'noviis:draft:1:create:free:current'
    for (let index = 0; index < MIN_LOCAL_DRAFT_SNAPSHOTS_TO_RETAIN + 2; index++) {
      Storage.set(`noviis:draft:1:create:free:${index}`, {
        boardUrl: 'free',
        title: `draft ${index}`,
        hasLocalChanges: false,
        clientModifiedAt: new Date(Date.UTC(2026, 7, 3, 0, index)).toISOString(),
      })
    }
    const originalSet = Storage.setWithResult.bind(Storage)
    vi.spyOn(Storage, 'setWithResult').mockImplementation((key, value) => key === targetKey
      ? { ok: false, reason: 'quota-exceeded' }
      : originalSet(key, value))

    expect(storeDraftSnapshotWithBudget(targetKey, {
      boardUrl: 'free',
      title: 'current draft',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })).toBe(false)
    expect(Storage.keys().filter((key) => key.startsWith('noviis:draft:1:create:free:'))).toHaveLength(5)
    expect(Storage.has(targetKey)).toBe(false)
  })

  it('rejects an oversized snapshot without evicting existing drafts', () => {
    const existingKey = 'noviis:draft:1:create:free:existing'
    const targetKey = 'noviis:draft:1:create:free:oversized'
    Storage.set(existingKey, {
      boardUrl: 'free',
      title: 'keep me',
      clientModifiedAt: '2026-08-01T00:00:00.000Z',
    })

    expect(storeDraftSnapshotWithBudget(targetKey, {
      boardUrl: 'free',
      title: 'x'.repeat(MAX_LOCAL_DRAFT_BYTES),
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })).toBe(false)

    expect(Storage.get(existingKey)).toEqual(expect.objectContaining({ title: 'keep me' }))
    expect(Storage.has(targetKey)).toBe(false)
  })
})
