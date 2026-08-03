import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Storage } from '@/utils/storage'
import {
  cleanupExpiredDraftSnapshots,
  loadStoredDraftSnapshot,
  migrateStoredDraftSnapshot,
} from '@/features/board/posts/draft/postDraftLifecycle'
import {
  isDraftDeletedLocally,
  markDraftDeletedLocally,
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
})
