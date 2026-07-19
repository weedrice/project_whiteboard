import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePostComposerSubmit } from '../usePostComposerSubmit'
import logger from '@/utils/logger'
import { createDeferred } from '@/test/async'

vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn(),
  },
}))

const basePayload = {
  title: 'Post title',
  categoryId: 3,
  tags: ['tag'],
  contents: '<p>Body</p>',
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
  isNotice: false,
  fileIds: [10],
}

function createSubmit(overrides: {
  mode?: 'create' | 'edit'
  title?: string
  categoryId?: string | number
  hideCategory?: boolean
  draftEnabled?: boolean
  draftConflict?: boolean
  draftId?: number | null
  scheduledAt?: string
  scheduledPostId?: string
  saveDraftNow?: () => Promise<{ draftId?: number | null } | null>
  createSuccessToastMessage?: () => string | undefined
} = {}) {
  const identity = ref('session-1:create:free:new')
  const payload = ref({ ...basePayload })
  const boardUrl = ref('free')
  const postId = ref('77')
  const scheduledPostId = ref(overrides.scheduledPostId ?? '')
  const createPost = vi.fn()
  const createScheduledPost = vi.fn()
  const updateScheduledPost = vi.fn()
  const updatePost = vi.fn()
  const addToast = vi.fn()
  const markCurrentSnapshotSaved = vi.fn()
  const cleanupPublishedDraft = vi.fn()
  const clearScheduledDraftRecovery = vi.fn()
  const releaseUploadedFileOwnership = vi.fn()
  const onSubmitted = vi.fn()
  const submit = usePostComposerSubmit({
    identity,
    mode: () => overrides.mode ?? 'create',
    boardUrl,
    postId,
    scheduledPostId,
    board: ref({ isAdmin: true }),
    form: ref({
      title: overrides.title ?? 'Post title',
      categoryId: overrides.categoryId ?? '3',
    }),
    hideCategory: () => overrides.hideCategory,
    draftEnabled: ref(overrides.draftEnabled ?? false),
    draftConflict: ref(overrides.draftConflict ?? false),
    draftId: ref(overrides.draftId ?? null),
    saveDraftNow: overrides.saveDraftNow ?? vi.fn().mockResolvedValue(null),
    buildPayload: () => payload.value,
    markCurrentSnapshotSaved,
    cleanupPublishedDraft,
    clearScheduledDraftRecovery,
    releaseUploadedFileOwnership,
    createPost,
    createScheduledPost,
    updateScheduledPost,
    updatePost,
    scheduledAt: ref(overrides.scheduledAt ?? ''),
    onSubmitted: () => onSubmitted,
    createSuccessToastMessage: overrides.createSuccessToastMessage ?? (() => undefined),
    t: (key: string) => key,
    addToast,
  })

  return {
    ...submit,
    identity,
    boardUrl,
    postId,
    payload,
    scheduledPostId,
    addToast,
    cleanupPublishedDraft,
    clearScheduledDraftRecovery,
    releaseUploadedFileOwnership,
    createPost,
    createScheduledPost,
    updateScheduledPost,
    markCurrentSnapshotSaved,
    onSubmitted,
    updatePost,
  }
}

describe('usePostComposerSubmit', () => {
  it('keeps a single-flight lock from draft save through the mutation callback', async () => {
    const draft = createDeferred<{ draftId: number }>()
    const saveDraftNow = vi.fn(() => draft.promise)
    const submit = createSubmit({ draftEnabled: true, saveDraftNow })

    const first = submit.handleSubmit()
    const duplicate = submit.handleSubmit()

    expect(submit.isSubmissionLocked.value).toBe(true)
    expect(saveDraftNow).toHaveBeenCalledOnce()
    draft.resolve({ draftId: 8 })
    await Promise.all([first, duplicate])

    expect(submit.createPost).toHaveBeenCalledOnce()
    expect(submit.isSubmissionLocked.value).toBe(true)
    submit.createPost.mock.calls[0][1].onSuccess({ data: { data: { postId: 1 } } })
    expect(submit.isSubmissionLocked.value).toBe(false)
  })

  it('stops before mutation when the form identity changes during the pre-submit draft save', async () => {
    const draft = createDeferred<{ draftId: number }>()
    const submit = createSubmit({
      draftEnabled: true,
      saveDraftNow: vi.fn(() => draft.promise),
    })

    const pendingSubmit = submit.handleSubmit()
    submit.identity.value = 'session-1:create:qna:new'
    submit.boardUrl.value = 'qna'
    draft.resolve({ draftId: 91 })
    await pendingSubmit

    expect(submit.isSubmissionLocked.value).toBe(false)
    expect(submit.createPost).not.toHaveBeenCalled()
    expect(submit.createScheduledPost).not.toHaveBeenCalled()
    expect(submit.updatePost).not.toHaveBeenCalled()
    expect(submit.addToast).not.toHaveBeenCalled()
  })

  it.each([
    ['create', { mode: 'create' as const }, 'createPost'],
    ['scheduled create', { mode: 'create' as const, scheduledAt: '2026-07-14T12:00' }, 'createScheduledPost'],
    ['update', { mode: 'edit' as const }, 'updatePost'],
  ])('ignores a delayed %s response after switching to another form', async (_label, overrides, mutationName) => {
    const submit = createSubmit(overrides)
    await submit.handleSubmit()

    const mutation = submit[mutationName as 'createPost' | 'createScheduledPost' | 'updatePost']
    const staleOptions = mutation.mock.calls[0][1] as {
      onSuccess: (response?: unknown) => void
    }
    submit.identity.value = 'session-1:edit:qna:99'
    submit.boardUrl.value = 'qna'
    submit.postId.value = '99'

    expect(submit.isSubmissionLocked.value).toBe(false)
    await submit.handleSubmit()
    expect(mutation).toHaveBeenCalledTimes(2)
    expect(submit.isSubmissionLocked.value).toBe(true)
    if (mutationName === 'createPost') {
      staleOptions.onSuccess({ data: { data: { postId: 123, earnedPoints: 50 } } })
    } else if (mutationName === 'createScheduledPost') {
      staleOptions.onSuccess({ data: { data: { scheduledPostId: 44, scheduledAt: '2026-07-14T12:00' } } })
    } else {
      staleOptions.onSuccess()
    }

    expect(submit.isSubmissionLocked.value).toBe(true)
    expect(submit.releaseUploadedFileOwnership).not.toHaveBeenCalled()
    expect(submit.markCurrentSnapshotSaved).not.toHaveBeenCalled()
    expect(submit.cleanupPublishedDraft).not.toHaveBeenCalled()
    expect(submit.clearScheduledDraftRecovery).not.toHaveBeenCalled()
    expect(submit.addToast).not.toHaveBeenCalled()
    expect(submit.onSubmitted).not.toHaveBeenCalled()
  })

  it('releases the single-flight lock when mutation dispatch throws synchronously', async () => {
    const submit = createSubmit()
    submit.createPost.mockImplementation(() => {
      throw new Error('dispatch failed')
    })

    await submit.handleSubmit()

    expect(submit.isSubmissionLocked.value).toBe(false)
    expect(submit.addToast).toHaveBeenCalledWith('common.error.unknown', 'error')
  })

  it('blocks blank titles and missing categories before mutating', async () => {
    const blankTitle = createSubmit({ title: '   ' })

    await blankTitle.handleSubmit()

    expect(blankTitle.addToast).toHaveBeenCalledWith('board.writePost.validation', 'error')
    expect(blankTitle.createPost).not.toHaveBeenCalled()

    const missingCategory = createSubmit({ categoryId: '' })

    await missingCategory.handleSubmit()

    expect(missingCategory.addToast).toHaveBeenCalledWith('board.writePost.validation', 'error')
    expect(missingCategory.createPost).not.toHaveBeenCalled()
  })

  it('creates with the saved draft id and emits the submitted result after success cleanup', async () => {
    const calls: string[] = []
    const submit = createSubmit({
      draftEnabled: true,
      draftId: 4,
      saveDraftNow: vi.fn().mockResolvedValue({ draftId: 91 }),
      createSuccessToastMessage: () => 'created',
    })
    submit.markCurrentSnapshotSaved.mockImplementation(() => calls.push('mark'))
    submit.cleanupPublishedDraft.mockImplementation(() => calls.push('cleanup'))
    submit.onSubmitted.mockImplementation(() => calls.push('submitted'))

    await submit.handleSubmit()

    expect(submit.createPost).toHaveBeenCalledWith({
      boardUrl: 'free',
      data: {
        ...basePayload,
        draftId: 91,
      },
    }, expect.objectContaining({
      onSuccess: expect.any(Function),
      onError: expect.any(Function),
    }))

    const createOptions = submit.createPost.mock.calls[0][1]
    createOptions.onSuccess({ data: { data: { postId: 123, earnedPoints: 50 } } })

    expect(submit.addToast).toHaveBeenCalledWith('created', 'success')
    expect(submit.addToast).toHaveBeenCalledWith('common.pointEarned', 'success')
    expect(submit.onSubmitted).toHaveBeenCalledWith({
      mode: 'create',
      boardUrl: 'free',
      newPostId: 123,
      isSecret: false,
      isBoardAdmin: true,
    })
    expect(calls).toEqual(['mark', 'cleanup', 'submitted'])
    expect(submit.releaseUploadedFileOwnership).toHaveBeenCalledWith([10])
  })

  it('aborts submit when saving a draft before submit fails', async () => {
    const error = new Error('draft failed')
    const submit = createSubmit({
      draftEnabled: true,
      saveDraftNow: vi.fn().mockRejectedValue(error),
    })

    await submit.handleSubmit()

    expect(logger.error).toHaveBeenCalledWith('Failed to save draft before submit:', error)
    expect(submit.addToast).toHaveBeenCalledWith('common.error.unknown', 'error')
    expect(submit.createPost).not.toHaveBeenCalled()
    expect(submit.updatePost).not.toHaveBeenCalled()
  })

  it('keeps the server draft for scheduled publishing and clears only local recovery', async () => {
    const submit = createSubmit({
      draftEnabled: true,
      draftId: 4,
      scheduledAt: '2026-07-14T12:00',
      saveDraftNow: vi.fn().mockResolvedValue({ draftId: 91 }),
    })

    await submit.handleSubmit()

    expect(submit.createScheduledPost).toHaveBeenCalledWith({
      boardUrl: 'free',
      data: { ...basePayload, draftId: 91, scheduledAt: '2026-07-14T12:00' },
    }, expect.any(Object))

    const scheduleOptions = submit.createScheduledPost.mock.calls[0][1]
    scheduleOptions.onSuccess({
      data: { data: { scheduledPostId: 44, scheduledAt: '2026-07-14T12:00' } },
    })

    expect(submit.clearScheduledDraftRecovery).toHaveBeenCalledOnce()
    expect(submit.cleanupPublishedDraft).not.toHaveBeenCalled()
    expect(submit.releaseUploadedFileOwnership).toHaveBeenCalledWith([10])
  })

  it('updates an editable scheduled post with its complete composer payload', async () => {
    const submit = createSubmit({
      mode: 'edit',
      scheduledPostId: '44',
      scheduledAt: '2026-07-20T12:00',
    })

    await submit.handleSubmit()

    expect(submit.updateScheduledPost).toHaveBeenCalledWith({
      scheduledPostId: '44',
      data: { ...basePayload, scheduledAt: '2026-07-20T12:00' },
    }, expect.objectContaining({
      onSuccess: expect.any(Function),
      onError: expect.any(Function),
    }))
    expect(submit.updatePost).not.toHaveBeenCalled()

    submit.updateScheduledPost.mock.calls[0][1].onSuccess({
      data: { data: { scheduledPostId: 44, scheduledAt: '2026-07-20T12:00' } },
    })

    expect(submit.markCurrentSnapshotSaved).toHaveBeenCalledOnce()
    expect(submit.addToast).toHaveBeenCalledWith('board.writePost.scheduleUpdateSuccess', 'success')
    expect(submit.onSubmitted).toHaveBeenCalledWith({
      mode: 'edit',
      boardUrl: 'free',
      scheduledPostId: 44,
      scheduledAt: '2026-07-20T12:00',
      isSecret: false,
      isBoardAdmin: true,
    })
  })

  it('keeps edits made while a scheduled update is pending and asks for another save', async () => {
    const submit = createSubmit({
      mode: 'edit',
      scheduledPostId: '44',
      scheduledAt: '2026-07-20T12:00',
    })
    await submit.handleSubmit()
    submit.payload.value = { ...basePayload, title: 'Edited while saving' }

    submit.updateScheduledPost.mock.calls[0][1].onSuccess({
      data: { data: { scheduledPostId: 44, scheduledAt: '2026-07-20T12:00' } },
    })

    expect(submit.markCurrentSnapshotSaved).not.toHaveBeenCalled()
    expect(submit.onSubmitted).not.toHaveBeenCalled()
    expect(submit.addToast).toHaveBeenCalledWith(
      'board.writePost.scheduleChangedDuringSave',
      'warning',
    )
    expect(submit.isSubmissionLocked.value).toBe(false)
  })

  it('compares scheduled save state with the draft id issued immediately before submit', async () => {
    const submit = createSubmit({
      mode: 'edit',
      scheduledPostId: '44',
      scheduledAt: '2026-07-20T12:00',
      draftEnabled: true,
      draftId: 4,
      saveDraftNow: vi.fn().mockResolvedValue({ draftId: 91 }),
    })

    await submit.handleSubmit()

    expect(submit.updateScheduledPost).toHaveBeenCalledWith({
      scheduledPostId: '44',
      data: { ...basePayload, draftId: 91, scheduledAt: '2026-07-20T12:00' },
    }, expect.any(Object))

    submit.updateScheduledPost.mock.calls[0][1].onSuccess({
      data: { data: { scheduledPostId: 44, scheduledAt: '2026-07-20T12:00' } },
    })

    expect(submit.markCurrentSnapshotSaved).toHaveBeenCalledOnce()
    expect(submit.addToast).not.toHaveBeenCalledWith(
      'board.writePost.scheduleChangedDuringSave',
      'warning',
    )
  })

  it.each([
    ['immediate create', { mode: 'create' as const }],
    ['scheduled create', { mode: 'create' as const, scheduledAt: '2026-07-14T12:00' }],
    ['update', { mode: 'edit' as const }],
  ])('blocks %s while the server draft is conflicted', async (_label, overrides) => {
    const submit = createSubmit({
      ...overrides,
      draftEnabled: true,
      draftConflict: true,
    })

    await submit.handleSubmit()

    expect(submit.addToast).toHaveBeenCalledWith('board.writePost.draftStatus.conflict', 'error')
    expect(submit.createPost).not.toHaveBeenCalled()
    expect(submit.createScheduledPost).not.toHaveBeenCalled()
    expect(submit.updatePost).not.toHaveBeenCalled()
  })

  it('updates existing posts and includes the existing draft id when no new draft id is returned', async () => {
    const submit = createSubmit({
      mode: 'edit',
      draftEnabled: true,
      draftId: 4,
      saveDraftNow: vi.fn().mockResolvedValue(null),
    })

    await submit.handleSubmit()

    expect(submit.updatePost).toHaveBeenCalledWith({
      postId: '77',
      data: {
        ...basePayload,
        draftId: 4,
      },
    }, expect.objectContaining({
      onSuccess: expect.any(Function),
      onError: expect.any(Function),
    }))

    const updateOptions = submit.updatePost.mock.calls[0][1]
    updateOptions.onSuccess()

    expect(submit.markCurrentSnapshotSaved).toHaveBeenCalled()
    expect(submit.cleanupPublishedDraft).toHaveBeenCalled()
    expect(submit.releaseUploadedFileOwnership).toHaveBeenCalledWith([10])
    expect(submit.onSubmitted).toHaveBeenCalledWith({
      mode: 'edit',
      boardUrl: 'free',
      postId: '77',
      isSecret: false,
      isBoardAdmin: true,
    })
  })
})
