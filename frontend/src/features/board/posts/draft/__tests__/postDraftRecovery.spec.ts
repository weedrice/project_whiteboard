import { describe, expect, it } from 'vitest'
import type { DraftPost } from '@/types'
import {
  resolveDraftRecoverySnapshot,
  type DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'

const serverDraft = (overrides: Partial<DraftPost> = {}): DraftPost => ({
  draftId: 91,
  clientDraftKey: 'server-key',
  version: 3,
  boardId: 1,
  boardUrl: 'general',
  boardName: 'General',
  title: 'Server title',
  contents: '<p>Server body</p>',
  tags: [],
  fileIds: [],
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
  updatedAt: '2026-08-05T12:00:00.000Z',
  ...overrides,
} as DraftPost)

const localBackup = (overrides: Partial<DraftRecoverySnapshot> = {}): DraftRecoverySnapshot => ({
  boardUrl: 'general',
  title: 'Local title',
  contents: '<p>Local body</p>',
  tags: [],
  fileIds: [],
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
  hasLocalChanges: true,
  updatedAt: '2026-08-06T12:00:00.000Z',
  ...overrides,
})

describe('draft recovery source policy', () => {
  it('uses the server draft when no local backup exists', () => {
    expect(resolveDraftRecoverySnapshot(null, serverDraft())).toEqual({
      snapshot: expect.objectContaining({ title: 'Server title' }),
      source: 'server',
      conflict: false,
    })
  })

  it('uses the server draft when the local backup is clean even if its timestamp is newer', () => {
    expect(resolveDraftRecoverySnapshot(localBackup({
      boardUrl: 'general',
      title: 'Old local copy',
      hasLocalChanges: false,
      updatedAt: '2026-08-06T12:00:00.000Z',
    }), serverDraft()).source).toBe('server')
  })

  it('uses the server draft when an unsaved backup already has identical content', () => {
    const server = serverDraft()
    const resolution = resolveDraftRecoverySnapshot(localBackup({
      draftId: server.draftId,
      clientDraftKey: server.clientDraftKey ?? undefined,
      version: server.version,
      title: server.title,
      contents: server.contents,
      tags: server.tags,
      fileIds: server.fileIds,
      isNotice: server.isNotice,
      isNsfw: server.isNsfw,
      isSpoiler: server.isSpoiler,
      isSecret: server.isSecret,
      hasLocalChanges: true,
      updatedAt: '2026-08-06T12:00:00.000Z',
    }), server)

    expect(resolution.source).toBe('server')
    expect(resolution.conflict).toBe(false)
  })

  it('preserves a different unsaved backup only as an explicit conflict', () => {
    const local = localBackup()
    expect(resolveDraftRecoverySnapshot(local, serverDraft())).toEqual({
      snapshot: local,
      source: 'local',
      conflict: true,
    })
  })

  it('uses the emergency backup when the server is unavailable', () => {
    const local = localBackup()
    expect(resolveDraftRecoverySnapshot(local, null)).toEqual({
      snapshot: local,
      source: 'local',
      conflict: false,
    })
  })
})
