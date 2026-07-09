import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePostComposerSubmit } from '../usePostComposerSubmit'
import logger from '@/utils/logger'

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
  draftId?: number | null
  saveDraftNow?: () => Promise<{ draftId?: number | null } | null>
  createSuccessToastMessage?: () => string | undefined
} = {}) {
  const createPost = vi.fn()
  const createScheduledPost = vi.fn()
  const updatePost = vi.fn()
  const addToast = vi.fn()
  const markCurrentSnapshotSaved = vi.fn()
  const cleanupPublishedDraft = vi.fn()
  const onSubmitted = vi.fn()
  const submit = usePostComposerSubmit({
    mode: () => overrides.mode ?? 'create',
    boardUrl: ref('free'),
    postId: ref('77'),
    board: ref({ isAdmin: true }),
    form: ref({
      title: overrides.title ?? 'Post title',
      categoryId: overrides.categoryId ?? '3',
    }),
    hideCategory: () => overrides.hideCategory,
    draftEnabled: ref(overrides.draftEnabled ?? false),
    draftId: ref(overrides.draftId ?? null),
    saveDraftNow: overrides.saveDraftNow ?? vi.fn().mockResolvedValue(null),
    buildPayload: () => basePayload,
    markCurrentSnapshotSaved,
    cleanupPublishedDraft,
    createPost,
    createScheduledPost,
    updatePost,
    scheduledAt: ref(''),
    onSubmitted: () => onSubmitted,
    createSuccessToastMessage: overrides.createSuccessToastMessage ?? (() => undefined),
    t: (key: string) => key,
    addToast,
  })

  return {
    ...submit,
    addToast,
    cleanupPublishedDraft,
    createPost,
    createScheduledPost,
    markCurrentSnapshotSaved,
    onSubmitted,
    updatePost,
  }
}

describe('usePostComposerSubmit', () => {
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
    expect(submit.onSubmitted).toHaveBeenCalledWith({
      mode: 'edit',
      boardUrl: 'free',
      postId: '77',
      isSecret: false,
      isBoardAdmin: true,
    })
  })
})
