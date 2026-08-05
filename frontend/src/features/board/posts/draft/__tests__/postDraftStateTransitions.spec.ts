import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createDraftStateTransitionController } from '@/features/board/posts/draft/postDraftStateTransitions'
import { markDraftDeletedLocally } from '@/features/board/posts/draft/postDraftTombstone'
import { reportDraftOperationalEvent } from '@/utils/clientErrorReporter'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'

vi.mock('@/features/board/posts/draft/postDraftTombstone', () => ({
  markDraftDeletedLocally: vi.fn(() => true),
}))

vi.mock('@/utils/clientErrorReporter', () => ({
  reportDraftOperationalEvent: vi.fn().mockResolvedValue(undefined),
}))

function createController(options: {
  localRevision?: number
  persistedRevision?: number
  localSnapshot?: DraftRecoverySnapshot | null
  storeResult?: boolean
} = {}) {
  const draftId = ref<number | null>(91)
  const draftDeleted = ref(false)
  const draftProtected = ref(false)
  const protectedDraftForkAvailable = ref(false)
  const staleReferencesReset = ref(false)
  const draftConflict = ref(true)
  const lastSaveFailed = ref(true)
  const clearAutosaveTimer = vi.fn()
  const clearSaveRetry = vi.fn()
  const invalidatePendingSaves = vi.fn()
  const resetDraftTracking = vi.fn(() => {
    draftId.value = null
  })
  const buildPayload = vi.fn(() => ({
    boardUrl: 'general',
    title: 'Local title',
    contents: '<p>Local body</p>',
    fileIds: [2, 3],
  }))
  const applyDraft = vi.fn()
  const onStaleReferencesReset = vi.fn()
  const removeLocalSnapshot = vi.fn(() => true)
  const storeLocalSnapshot = vi.fn(() => options.storeResult ?? true)
  const prepareStaleSnapshot = vi.fn((snapshot: DraftRecoverySnapshot) => ({
    ...snapshot,
    title: `${snapshot.title} prepared`,
  }))

  const controller = createDraftStateTransitionController({
    draftId,
    ownerId: ref(7),
    draftDeleted,
    draftProtected,
    protectedDraftForkAvailable,
    staleReferencesReset,
    draftConflict,
    lastSaveFailed,
    localRevision: () => options.localRevision ?? 1,
    persistedRevision: () => options.persistedRevision ?? 0,
    clearAutosaveTimer,
    clearSaveRetry,
    invalidatePendingSaves,
    resetDraftTracking,
    buildPayload,
    getDetachedDraftFileIdsToPreserve: () => [3],
    prepareStaleSnapshot,
    applyDraft,
    onStaleReferencesReset,
    loadLocalSnapshot: () => options.localSnapshot ?? null,
    removeLocalSnapshot,
    storeLocalSnapshot,
  })

  return {
    controller,
    state: {
      draftId,
      draftDeleted,
      draftProtected,
      protectedDraftForkAvailable,
      staleReferencesReset,
      draftConflict,
      lastSaveFailed,
    },
    actions: {
      clearAutosaveTimer,
      clearSaveRetry,
      invalidatePendingSaves,
      resetDraftTracking,
      applyDraft,
      onStaleReferencesReset,
      removeLocalSnapshot,
      storeLocalSnapshot,
      prepareStaleSnapshot,
    },
  }
}

describe('draft state transition controller', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(markDraftDeletedLocally).mockReturnValue(true)
  })

  it('detaches a missing server draft while preserving the current content locally', () => {
    const { controller, state, actions } = createController()

    controller.transitionToDeletedDraft()

    expect(markDraftDeletedLocally).toHaveBeenCalledWith(7, 91)
    expect(actions.clearAutosaveTimer).toHaveBeenCalled()
    expect(actions.clearSaveRetry).toHaveBeenCalled()
    expect(actions.invalidatePendingSaves).toHaveBeenCalled()
    expect(actions.resetDraftTracking).toHaveBeenCalled()
    expect(state.draftDeleted.value).toBe(true)
    expect(state.draftProtected.value).toBe(false)
    expect(state.draftConflict.value).toBe(false)
    expect(state.staleReferencesReset.value).toBe(true)
    expect(state.lastSaveFailed.value).toBe(false)
    expect(actions.applyDraft).toHaveBeenCalledWith(expect.objectContaining({
      draftId: undefined,
      version: undefined,
      title: 'Local title prepared',
      unassociatedUploadFileIds: [3],
    }))
    expect(actions.storeLocalSnapshot).toHaveBeenCalledWith(expect.objectContaining({
      title: 'Local title prepared',
    }))
  })

  it('reports a tombstone write failure without dropping the detached local draft', () => {
    vi.mocked(markDraftDeletedLocally).mockReturnValue(false)
    const { controller, actions } = createController()

    controller.transitionToDeletedDraft()

    expect(reportDraftOperationalEvent).toHaveBeenCalledWith('tombstone_write_failed')
    expect(actions.storeLocalSnapshot).toHaveBeenCalledTimes(1)
  })

  it('forks local changes when a server draft becomes protected', () => {
    const { controller, state, actions } = createController({
      localRevision: 3,
      persistedRevision: 2,
    })

    controller.transitionToProtectedDraft(true)

    expect(actions.removeLocalSnapshot).toHaveBeenCalledTimes(1)
    expect(actions.resetDraftTracking).toHaveBeenCalledTimes(1)
    expect(actions.applyDraft).toHaveBeenCalledWith(expect.objectContaining({
      contractValidationFailed: true,
      title: 'Local title prepared',
    }))
    expect(state.protectedDraftForkAvailable.value).toBe(true)
    expect(state.draftProtected.value).toBe(true)
    expect(state.draftDeleted.value).toBe(false)
    expect(state.draftConflict.value).toBe(false)
    expect(state.staleReferencesReset.value).toBe(true)
  })

  it('does not fork a protected draft when there are no local changes', () => {
    const { controller, state, actions } = createController({
      localRevision: 2,
      persistedRevision: 2,
      localSnapshot: { boardUrl: 'general', hasLocalChanges: false },
    })

    controller.transitionToProtectedDraft(false)

    expect(actions.removeLocalSnapshot).toHaveBeenCalledTimes(1)
    expect(actions.resetDraftTracking).not.toHaveBeenCalled()
    expect(actions.applyDraft).not.toHaveBeenCalled()
    expect(actions.storeLocalSnapshot).not.toHaveBeenCalled()
    expect(state.protectedDraftForkAvailable.value).toBe(false)
    expect(state.draftProtected.value).toBe(true)
    expect(state.staleReferencesReset.value).toBe(false)
  })

  it('stops autosave again when preserving a detached snapshot fails', () => {
    const { controller, actions } = createController({ storeResult: false })

    controller.transitionToDeletedDraft()

    expect(actions.clearAutosaveTimer).toHaveBeenCalledTimes(2)
  })
})
