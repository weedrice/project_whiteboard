import { getCurrentScope, onScopeDispose, ref, watch, type Ref } from 'vue'
import { unwrapApiData } from '@/api/response'
import logger from '@/utils/logger'
import type { ApiResponse } from '@/types'
import type { PollPayload, PostCreateResponse, ScheduledPost } from '@/api/post'

type ComposerToastType = 'info' | 'success' | 'warning' | 'error'
type PostComposerMode = 'create' | 'edit'
type PostComposerPayload = {
  title: string
  categoryId?: number
  tags: string[]
  contents: string
  isNsfw: boolean
  isSpoiler: boolean
  isSecret: boolean
  isNotice?: boolean
  seriesId?: number | null
  fileIds: number[]
  poll?: PollPayload | null
  draftId?: number
  scheduledAt?: string
}
type CreatePostComposerPayload = Omit<PostComposerPayload, 'seriesId'> & { seriesId?: number }

export type PostFormSubmitResult = {
  mode: PostComposerMode
  boardUrl: string
  postId?: string | number
  newPostId?: string | number
  scheduledPostId?: string | number
  scheduledAt?: string
  isSecret: boolean
  isBoardAdmin: boolean
}

type CreatePostMutate = (
  variables: { boardUrl: string, data: CreatePostComposerPayload },
  options: {
    onSuccess: (response: { data: ApiResponse<PostCreateResponse> }) => void
    onError: (error: unknown) => void
  },
) => void

type UpdatePostMutate = (
  variables: { postId: string | number, data: PostComposerPayload },
  options: {
    onSuccess: () => void
    onError: (error: unknown) => void
  },
) => void

type CreateScheduledPostMutate = (
  variables: { boardUrl: string, data: CreatePostComposerPayload & { scheduledAt: string } },
  options: {
    onSuccess: (response: { data: ApiResponse<ScheduledPost> }) => void
    onError: (error: unknown) => void
  },
) => void

type UpdateScheduledPostMutate = (
  variables: { scheduledPostId: string | number, data: CreatePostComposerPayload & { scheduledAt: string } },
  options: {
    onSuccess: (response: { data: ApiResponse<ScheduledPost> }) => void
    onError: (error: unknown) => void
  },
) => void

type UsePostComposerSubmitOptions = {
  identity: Ref<string>
  mode: () => PostComposerMode
  boardUrl: Ref<string>
  postId: Ref<string | number>
  scheduledPostId: Ref<string | number>
  board: Ref<{ isAdmin?: boolean } | null | undefined>
  form: Ref<{ title: string, categoryId: string | number }>
  hideCategory: () => boolean | undefined
  draftEnabled: Ref<boolean>
  draftConflict: Ref<boolean>
  draftId: Ref<number | null>
  saveDraftNow: () => Promise<{ draftId?: number | null } | null>
  buildPayload: () => Omit<PostComposerPayload, 'draftId'>
  markCurrentSnapshotSaved: () => void
  cleanupPublishedDraft: () => void
  clearScheduledDraftRecovery: () => void
  releaseUploadedFileOwnership: (fileIds: number[]) => void
  createPost: CreatePostMutate
  createScheduledPost: CreateScheduledPostMutate
  updateScheduledPost: UpdateScheduledPostMutate
  updatePost: UpdatePostMutate
  onSubmitted: () => ((result: PostFormSubmitResult) => void) | undefined
  createSuccessToastMessage: () => string | undefined
  scheduledAt: Ref<string>
  t: (key: string, params?: Record<string, unknown>) => string
  addToast: (message: string, type: ComposerToastType) => void
  validateBeforeSubmit?: () => Promise<boolean> | boolean
}

export function usePostComposerSubmit(options: UsePostComposerSubmitOptions) {
  const isSubmissionLocked = ref(false)
  let submissionRevision = 0

  const stopIdentityWatch = watch(
    options.identity,
    () => {
      submissionRevision += 1
      isSubmissionLocked.value = false
    },
    { flush: 'sync' },
  )

  if (getCurrentScope()) {
    onScopeDispose(() => {
      stopIdentityWatch()
      submissionRevision += 1
      isSubmissionLocked.value = false
    })
  }

  function notifyCreateSubmitted(
    newPostId: string | number,
    payload: PostComposerPayload,
    context: SubmissionContext,
  ) {
    context.onSubmitted?.({
      mode: 'create',
      boardUrl: context.boardUrl,
      newPostId,
      isSecret: payload.isSecret,
      isBoardAdmin: context.isBoardAdmin,
    })
  }

  type SubmissionContext = {
    revision: number
    identity: string
    mode: PostComposerMode
    boardUrl: string
    postId: string | number
    scheduledPostId: string | number
    draftId?: number
    scheduledAt: string
    isBoardAdmin: boolean
    onSubmitted?: (result: PostFormSubmitResult) => void
    createSuccessToastMessage?: string
  }

  async function handleSubmit() {
    if (isSubmissionLocked.value) return
    const context: SubmissionContext = {
      revision: ++submissionRevision,
      identity: options.identity.value,
      mode: options.mode(),
      boardUrl: options.boardUrl.value,
      postId: options.postId.value,
      scheduledPostId: options.scheduledPostId.value,
      draftId: options.draftId.value ?? undefined,
      scheduledAt: options.scheduledAt.value?.trim(),
      isBoardAdmin: options.board.value?.isAdmin ?? false,
      onSubmitted: options.onSubmitted(),
      createSuccessToastMessage: options.createSuccessToastMessage(),
    }
    isSubmissionLocked.value = true
    const isCurrentSubmission = () => (
      context.revision === submissionRevision
      && context.identity === options.identity.value
    )
    const unlock = () => {
      if (isCurrentSubmission()) isSubmissionLocked.value = false
    }

    try {
    if (options.draftConflict.value) {
      options.addToast(options.t('board.writePost.draftStatus.conflict'), 'error')
      unlock()
      return
    }
    if (options.validateBeforeSubmit) {
      const validationResult = options.validateBeforeSubmit()
      const isValid = validationResult instanceof Promise ? await validationResult : validationResult
      if (!isCurrentSubmission()) return
      if (!isValid) {
        unlock()
        return
      }
    }
    if (!options.form.value.title.trim()) {
      options.addToast(options.t('board.writePost.validation'), 'error')
      unlock()
      return
    }
    if (context.mode === 'create' && !options.hideCategory() && !options.form.value.categoryId) {
      options.addToast(options.t('board.writePost.validation'), 'error')
      unlock()
      return
    }

    let currentDraftId = context.draftId
    if (options.draftEnabled.value) {
      try {
        const savedDraft = await options.saveDraftNow()
        if (!isCurrentSubmission()) return
        if (savedDraft?.draftId != null) {
          currentDraftId = savedDraft.draftId
        }
      } catch (error) {
        if (!isCurrentSubmission()) return
        logger.error('Failed to save draft before submit:', error)
        options.addToast(options.t('common.error.unknown'), 'error')
        unlock()
        return
      }
    }
    const payload = {
      ...options.buildPayload(),
      ...(currentDraftId !== undefined && { draftId: currentDraftId }),
    }

    if (context.scheduledPostId) {
      const scheduledAt = context.scheduledAt
      if (!scheduledAt) {
        options.addToast(options.t('board.writePost.validation'), 'error')
        unlock()
        return
      }
      const { seriesId, ...payloadWithoutSeries } = payload
      const scheduledPayload: CreatePostComposerPayload = seriesId == null
        ? payloadWithoutSeries
        : { ...payloadWithoutSeries, seriesId }
      const requestData = { ...scheduledPayload, scheduledAt }
      const submittedState = JSON.stringify(requestData)
      const currentState = () => {
        const currentPayload = {
          ...options.buildPayload(),
          ...(currentDraftId !== undefined && { draftId: currentDraftId }),
        }
        const { seriesId: currentSeriesId, ...currentWithoutSeries } = currentPayload
        const normalizedCurrent = currentSeriesId == null
          ? currentWithoutSeries
          : { ...currentWithoutSeries, seriesId: currentSeriesId }
        return JSON.stringify({
          ...normalizedCurrent,
          scheduledAt: options.scheduledAt.value?.trim(),
        })
      }
      options.updateScheduledPost({
        scheduledPostId: context.scheduledPostId,
        data: requestData,
      }, {
        onSuccess: (response) => {
          if (!isCurrentSubmission()) return
          unlock()
          options.releaseUploadedFileOwnership(payload.fileIds)
          if (submittedState !== currentState()) {
            options.addToast(options.t('board.writePost.scheduleChangedDuringSave'), 'warning')
            return
          }
          options.markCurrentSnapshotSaved()
          const scheduledPost = unwrapApiData(response.data)
          options.addToast(options.t('board.writePost.scheduleUpdateSuccess'), 'success')
          context.onSubmitted?.({
            mode: 'edit',
            boardUrl: context.boardUrl,
            scheduledPostId: scheduledPost.scheduledPostId,
            scheduledAt: scheduledPost.scheduledAt,
            isSecret: payload.isSecret,
            isBoardAdmin: context.isBoardAdmin,
          })
        },
        onError: (error) => {
          if (!isCurrentSubmission()) return
          unlock()
          logger.error('Failed to update scheduled post:', error)
        },
      })
      return
    }

    if (context.mode === 'create') {
      const { seriesId, ...payloadWithoutSeries } = payload
      const createPayload: CreatePostComposerPayload = seriesId == null
        ? payloadWithoutSeries
        : { ...payloadWithoutSeries, seriesId }
      const scheduledAt = context.scheduledAt
      if (scheduledAt) {
        options.createScheduledPost({ boardUrl: context.boardUrl, data: { ...createPayload, scheduledAt } }, {
          onSuccess: (response) => {
            if (!isCurrentSubmission()) return
            unlock()
            options.releaseUploadedFileOwnership(payload.fileIds)
            options.markCurrentSnapshotSaved()
            options.clearScheduledDraftRecovery()
            const scheduledPost = unwrapApiData(response.data)
            options.addToast(options.t('board.writePost.scheduleSuccess'), 'success')
            context.onSubmitted?.({
              mode: 'create',
              boardUrl: context.boardUrl,
              scheduledPostId: scheduledPost.scheduledPostId,
              scheduledAt: scheduledPost.scheduledAt,
              isSecret: payload.isSecret,
              isBoardAdmin: context.isBoardAdmin,
            })
          },
          onError: (error) => {
            if (!isCurrentSubmission()) return
            unlock()
            logger.error('Failed to schedule post:', error)
          },
        })
        return
      }
      options.createPost({ boardUrl: context.boardUrl, data: createPayload }, {
        onSuccess: (response) => {
          if (!isCurrentSubmission()) return
          unlock()
          options.releaseUploadedFileOwnership(payload.fileIds)
          options.markCurrentSnapshotSaved()
          options.cleanupPublishedDraft()
          const successToastMessage = context.createSuccessToastMessage
          if (successToastMessage) {
            options.addToast(successToastMessage, 'success')
          }
          const createdPost = unwrapApiData(response.data)
          if (createdPost.earnedPoints && createdPost.earnedPoints > 0) {
            options.addToast(options.t('common.pointEarned', { points: createdPost.earnedPoints }), 'success')
          }
          notifyCreateSubmitted(createdPost.postId, payload, context)
        },
        onError: (error) => {
          if (!isCurrentSubmission()) return
          unlock()
          logger.error('Failed to create post:', error)
        },
      })
      return
    }

    options.updatePost({ postId: context.postId, data: payload }, {
      onSuccess: () => {
        if (!isCurrentSubmission()) return
        unlock()
        options.releaseUploadedFileOwnership(payload.fileIds)
        options.markCurrentSnapshotSaved()
        options.cleanupPublishedDraft()
        context.onSubmitted?.({
          mode: 'edit',
          boardUrl: context.boardUrl,
          postId: context.postId,
          isSecret: payload.isSecret,
          isBoardAdmin: context.isBoardAdmin,
        })
      },
      onError: (error) => {
        if (!isCurrentSubmission()) return
        unlock()
        logger.error('Failed to update post:', error)
      },
    })
    } catch (error) {
      if (!isCurrentSubmission()) return
      unlock()
      logger.error('Failed to submit post:', error)
      options.addToast(options.t('common.error.unknown'), 'error')
    }
  }

  return {
    handleSubmit,
    isSubmissionLocked,
  }
}
