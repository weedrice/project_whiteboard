import { describe, expect, it } from 'vitest'
import {
  createDraftBlockingStatusController,
  resolveDraftStatus,
} from '@/features/board/posts/draft/postDraftStatus'

describe('draft status model', () => {
  it('keeps conflict, protected, and deleted mutually exclusive', () => {
    const state = createDraftBlockingStatusController()

    state.draftConflict.value = true
    expect(state.status.value).toBe('conflict')
    expect(state.draftConflict.value).toBe(true)
    expect(state.draftProtected.value).toBe(false)
    expect(state.draftDeleted.value).toBe(false)

    state.draftProtected.value = true
    expect(state.status.value).toBe('protected')
    expect(state.draftConflict.value).toBe(false)
    expect(state.draftProtected.value).toBe(true)

    state.draftDeleted.value = true
    expect(state.status.value).toBe('deleted')
    expect(state.draftProtected.value).toBe(false)
    expect(state.draftDeleted.value).toBe(true)
  })

  it('only clears the status represented by the flag being reset', () => {
    const state = createDraftBlockingStatusController('protected')

    state.draftConflict.value = false
    expect(state.status.value).toBe('protected')

    state.draftProtected.value = false
    expect(state.status.value).toBe('active')
  })

  it('resolves one public status with blocking states taking precedence', () => {
    expect(resolveDraftStatus({
      enabled: true,
      blockingStatus: 'conflict',
      isRestoring: true,
      isSaving: true,
      restoreFailed: true,
      saveFailed: true,
      dirty: true,
    })).toBe('conflict')

    expect(resolveDraftStatus({
      enabled: true,
      blockingStatus: 'active',
      isRestoring: true,
      isSaving: true,
      restoreFailed: false,
      saveFailed: false,
      dirty: false,
    })).toBe('restoring')
  })

  it('distinguishes saving, failures, dirty, clean, and disabled states', () => {
    const base = {
      enabled: true,
      blockingStatus: 'active' as const,
      isRestoring: false,
      isSaving: false,
      restoreFailed: false,
      saveFailed: false,
      dirty: false,
    }

    expect(resolveDraftStatus({ ...base, isSaving: true })).toBe('saving')
    expect(resolveDraftStatus({ ...base, restoreFailed: true })).toBe('restore-failed')
    expect(resolveDraftStatus({ ...base, saveFailed: true })).toBe('save-failed')
    expect(resolveDraftStatus({ ...base, dirty: true })).toBe('dirty')
    expect(resolveDraftStatus(base)).toBe('clean')
    expect(resolveDraftStatus({ ...base, enabled: false, dirty: true })).toBe('disabled')
  })
})
