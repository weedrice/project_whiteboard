import { describe, expect, it } from 'vitest'
import { createDraftBlockingStatusController } from '@/features/board/posts/draft/postDraftStatus'

describe('draft status model', () => {
  it('keeps conflict, protected, and deleted mutually exclusive', () => {
    const state = createDraftBlockingStatusController()

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
    const state = createDraftBlockingStatusController('protected')

    state.draftConflict.value = false
    expect(state.draftProtected.value).toBe(true)

    state.draftProtected.value = false
    expect(state.draftConflict.value).toBe(false)
    expect(state.draftProtected.value).toBe(false)
    expect(state.draftDeleted.value).toBe(false)
  })
})
