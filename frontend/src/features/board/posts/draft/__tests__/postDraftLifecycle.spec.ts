import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Storage } from '@/utils/storage'
import {
  cleanupExpiredDraftSnapshots,
  clearStoredDraftSnapshotsForUser,
  countUnsyncedStoredDraftSnapshotsForUser,
  enforceDraftSnapshotBudget,
  loadStoredDraftSnapshot,
  MAX_LOCAL_DRAFT_BYTES,
  MAX_LOCAL_DRAFT_SNAPSHOTS,
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
    }))
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

  it('reclaims an older snapshot and retries when the first storage write fails', () => {
    const oldKey = 'noviis:draft:1:create:free:old'
    const targetKey = 'noviis:draft:1:create:free:current'
    Storage.set(oldKey, {
      boardUrl: 'free',
      title: 'old draft',
      clientModifiedAt: '2026-08-01T00:00:00.000Z',
    })
    const originalSet = Storage.set.bind(Storage)
    let targetAttempts = 0
    const setSpy = vi.spyOn(Storage, 'set').mockImplementation((key, value) => {
      if (key === targetKey && targetAttempts++ === 0) return false
      return originalSet(key, value)
    })

    expect(storeDraftSnapshotWithBudget(targetKey, {
      boardUrl: 'free',
      title: 'current draft',
      clientModifiedAt: '2026-08-03T00:00:00.000Z',
    })).toBe(true)
    expect(Storage.has(oldKey)).toBe(false)
    expect(Storage.has(targetKey)).toBe(true)
    setSpy.mockRestore()
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
