import { describe, expect, it, vi } from 'vitest'
import {
  createDraftSessionStatusController,
} from '@/features/board/posts/draft/postDraftStatus'

describe('draft status model', () => {
  it('keeps conflict, protected, and deleted mutually exclusive', () => {
    const state = createDraftSessionStatusController()

    state.draftConflict.value = true
    expect(state.draftConflict.value).toBe(true)
    expect(state.draftProtected.value).toBe(false)
    expect(state.draftDeleted.value).toBe(false)

    state.draftProtected.value = true
    expect(state.draftConflict.value).toBe(false)
    expect(state.draftProtected.value).toBe(true)

    state.draftDeleted.value = true
    expect(state.draftConflict.value).toBe(false)
    expect(state.draftProtected.value).toBe(false)
    expect(state.draftDeleted.value).toBe(true)
  })

  it('only clears the status represented by the flag being reset', () => {
    const state = createDraftSessionStatusController('protected')

    state.draftConflict.value = false
    expect(state.draftProtected.value).toBe(true)

    state.draftProtected.value = false
    expect(state.draftConflict.value).toBe(false)
    expect(state.draftProtected.value).toBe(false)
    expect(state.draftDeleted.value).toBe(false)
  })

  it('represents operation and persistence without illegal flag combinations', () => {
    const state = createDraftSessionStatusController('active', () => 'client-key')

    state.isRestoringDraft.value = true
    expect(state.operation.value).toEqual({ type: 'restoring' })
    expect(state.isSavingDraft.value).toBe(false)

    state.isSavingDraft.value = true
    expect(state.operation.value).toEqual({ type: 'saving' })
    expect(state.isRestoringDraft.value).toBe(false)

    state.lastSavedAt.value = '2026-09-09T00:00:00.000Z'
    state.lastSaveScope.value = 'browser'
    expect(state.persistence.value).toEqual({
      type: 'saved',
      scope: 'browser',
      at: '2026-09-09T00:00:00.000Z',
    })

    state.lastSaveFailed.value = true
    expect(state.persistence.value).toEqual({ type: 'failed', target: 'server', exhausted: false })
    expect(state.lastSaveScope.value).toBeNull()

    state.setPersistenceFailure('browser')
    state.lastSaveFailed.value = true
    expect(state.persistence.value).toEqual({ type: 'failed', target: 'browser', exhausted: false })
  })

  it('invalidates requests and revision state as one session boundary', () => {
    const keys = ['first-key', 'second-key']
    const state = createDraftSessionStatusController('active', () => keys.shift() ?? 'next-key')
    const saveRequest = state.startRequest('save')
    const recoveryRequest = state.startRequest('recovery')
    state.incrementLocalRevision()
    state.identity.draftId.value = 91
    state.identity.version.value = 3

    const resetQueuedWork = vi.fn()
    expect(state.invalidatePendingWork(true, resetQueuedWork)).toBe(1)

    expect(saveRequest.signal.aborted).toBe(true)
    expect(recoveryRequest.signal.aborted).toBe(true)
    expect(state.isRequestCurrent('save', saveRequest)).toBe(false)
    expect(state.getLocalRevision()).toBe(0)
    expect(state.getPersistedRevision()).toBe(0)
    expect(resetQueuedWork).toHaveBeenCalledOnce()

    state.resetIdentity()
    expect(state.identity).toMatchObject({
      draftId: expect.objectContaining({ value: null }),
      version: expect.objectContaining({ value: null }),
      clientDraftKey: expect.objectContaining({ value: 'second-key' }),
    })
  })
})
