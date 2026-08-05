import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { Storage } from '@/utils/storage'
import logger from '@/utils/logger'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'
import { createDraftLocalSnapshotController } from '@/features/board/posts/draft/postDraftLocalSnapshot'
import { loadStoredDraftSnapshot } from '@/features/board/posts/draft/postDraftLifecycle'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn(),
    warn: vi.fn(),
  },
}))

vi.mock('@/utils/clientErrorReporter', () => ({
  reportDraftOperationalEvent: vi.fn().mockResolvedValue(undefined),
}))

const baseKey = 'noviis:draft:7:create:general:new'
const draftKey = 'noviis:draft:7:create:general:91'

function createController(overrides: {
  onStored?: (snapshot: DraftRecoverySnapshot) => void
  onRemoved?: () => void
} = {}) {
  const storageKey = ref(baseKey)
  const draftId = ref<number | null>(null)
  const draftVersion = ref<number | null>(3)
  const clientDraftKey = ref('client-key')
  const controller = createDraftLocalSnapshotController({
    storageKey,
    resolveStorageKey: (id) => `noviis:draft:7:create:general:${id}`,
    draftId,
    draftVersion,
    clientDraftKey,
    clientInstanceId: 'tab-a',
    getDetachedDraftFileIdsToPreserve: () => [2, 3],
    onStored: overrides.onStored,
    onRemoved: overrides.onRemoved,
  })
  return { controller, draftId }
}

describe('draft local snapshot controller', () => {
  beforeEach(() => {
    Storage.clear()
    vi.restoreAllMocks()
    vi.mocked(logger.error).mockClear()
    vi.mocked(reportDraftOperationalEvent).mockClear()
  })

  it('adds tracking metadata and keeps only detached files present in the draft', () => {
    const onStored = vi.fn()
    const { controller } = createController({ onStored })

    expect(controller.store({
      boardUrl: 'general',
      title: 'local draft',
      fileIds: [1, 2],
    })).toBe(true)

    expect(Storage.get(baseKey)).toEqual(expect.objectContaining({
      boardUrl: 'general',
      title: 'local draft',
      clientDraftKey: 'client-key',
      version: 3,
      clientInstanceId: 'tab-a',
      unassociatedUploadFileIds: [2],
    }))
    expect(onStored).toHaveBeenCalledWith(expect.objectContaining({
      unassociatedUploadFileIds: [2],
    }))
  })

  it('migrates a newly identified server draft to its draft-specific key', () => {
    const { controller, draftId } = createController()
    controller.store({
      boardUrl: 'general',
      title: 'before server id',
      clientModifiedAt: '2026-08-05T12:00:00.000Z',
    })

    draftId.value = 91

    expect(Storage.has(baseKey)).toBe(false)
    expect(loadStoredDraftSnapshot(draftKey)).toEqual(expect.objectContaining({
      draftId: 91,
      title: 'before server id',
      clientDraftKey: 'client-key',
    }))
    expect(controller.activeStorageKey.value).toBe(draftKey)
  })

  it('reports one write failure until a successful snapshot resets the failure edge', () => {
    const { controller } = createController()
    const setResult = vi.spyOn(Storage, 'setWithResult')
      .mockReturnValue({ ok: false, reason: 'unavailable' })

    expect(controller.store({ boardUrl: 'general', title: 'first' })).toBe(false)
    expect(controller.store({ boardUrl: 'general', title: 'second' })).toBe(false)
    expect(controller.lastSaveFailed.value).toBe(true)
    expect(reportDraftOperationalEvent).toHaveBeenCalledTimes(1)
    expect(reportDraftOperationalEvent).toHaveBeenCalledWith('local_storage_write_failed')

    setResult.mockRestore()
    expect(controller.store({ boardUrl: 'general', title: 'recovered' })).toBe(true)
    expect(controller.lastSaveFailed.value).toBe(false)

    vi.spyOn(Storage, 'setWithResult').mockReturnValue({ ok: false, reason: 'unavailable' })
    expect(controller.store({ boardUrl: 'general', title: 'failed again' })).toBe(false)
    expect(reportDraftOperationalEvent).toHaveBeenCalledTimes(2)
  })

  it('removes the active snapshot and notifies only for the active key', () => {
    const onRemoved = vi.fn()
    const { controller } = createController({ onRemoved })
    Storage.set(baseKey, { boardUrl: 'general' })
    Storage.set('noviis:draft:7:create:other:new', { boardUrl: 'other' })

    expect(controller.removeKey('noviis:draft:7:create:other:new')).toBe(true)
    expect(onRemoved).not.toHaveBeenCalled()
    expect(controller.remove()).toBe(true)
    expect(onRemoved).toHaveBeenCalledTimes(1)
  })

  it('reports a failed removal and can reset local failure status for a new session', () => {
    const { controller } = createController()
    vi.spyOn(Storage, 'setWithResult').mockReturnValueOnce({ ok: false, reason: 'unavailable' })
    controller.store({ boardUrl: 'general' })
    expect(controller.lastSaveFailed.value).toBe(true)

    controller.resetStatus()
    expect(controller.lastSaveFailed.value).toBe(false)

    vi.spyOn(Storage, 'remove').mockReturnValueOnce(false)
    expect(controller.remove()).toBe(false)
    expect(reportDraftOperationalEvent).toHaveBeenCalledWith('local_storage_remove_failed')
  })
})
