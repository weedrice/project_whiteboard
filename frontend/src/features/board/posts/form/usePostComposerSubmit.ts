import { ref, type Ref } from 'vue'
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

type UsePostComposerSubmitOptions = {
  mode: () => PostComposerMode
  boardUrl: Ref<string>
  postId: Ref<string | number>
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

  function notifyCreateSubmitted(newPostId: string | number, payload: PostComposerPayload) {
    options.onSubmitted()?.({
      mode: 'create',
      boardUrl: options.boardUrl.value,
      newPostId,
      isSecret: payload.isSecret,
      isBoardAdmin: options.board.value?.isAdmin ?? false,
    })
  }

  async function handleSubmit() {
    if (isSubmissionLocked.value) return
    isSubmissionLocked.value = true
    const unlock = () => {
      isSubmissionLocked.value = false
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
    if (options.mode() === 'create' && !options.hideCategory() && !options.form.value.categoryId) {
      options.addToast(options.t('board.writePost.validation'), 'error')
      unlock()
      return
    }

    let currentDraftId = options.draftId.value ?? undefined
    if (options.draftEnabled.value) {
      try {
        const savedDraft = await options.saveDraftNow()
        if (savedDraft?.draftId != null) {
          currentDraftId = savedDraft.draftId
        }
      } catch (error) {
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

    if (options.mode() === 'create') {
      const { seriesId, ...payloadWithoutSeries } = payload
      const createPayload: CreatePostComposerPayload = seriesId == null
        ? payloadWithoutSeries
        : { ...payloadWithoutSeries, seriesId }
      const scheduledAt = options.scheduledAt.value?.trim()
      if (scheduledAt) {
        options.createScheduledPost({ boardUrl: options.boardUrl.value, data: { ...createPayload, scheduledAt } }, {
          onSuccess: (response) => {
            unlock()
            options.releaseUploadedFileOwnership(payload.fileIds)
            options.markCurrentSnapshotSaved()
            options.clearScheduledDraftRecovery()
            const scheduledPost = unwrapApiData(response.data)
            options.addToast(options.t('board.writePost.scheduleSuccess'), 'success')
            options.onSubmitted()?.({
              mode: 'create',
              boardUrl: options.boardUrl.value,
              scheduledPostId: scheduledPost.scheduledPostId,
              scheduledAt: scheduledPost.scheduledAt,
              isSecret: payload.isSecret,
              isBoardAdmin: options.board.value?.isAdmin ?? false,
            })
          },
          onError: (error) => {
            unlock()
            logger.error('Failed to schedule post:', error)
          },
        })
        return
      }
      options.createPost({ boardUrl: options.boardUrl.value, data: createPayload }, {
        onSuccess: (response) => {
          unlock()
          options.releaseUploadedFileOwnership(payload.fileIds)
          options.markCurrentSnapshotSaved()
          options.cleanupPublishedDraft()
          const successToastMessage = options.createSuccessToastMessage()
          if (successToastMessage) {
            options.addToast(successToastMessage, 'success')
          }
          const createdPost = unwrapApiData(response.data)
          if (createdPost.earnedPoints && createdPost.earnedPoints > 0) {
            options.addToast(options.t('common.pointEarned', { points: createdPost.earnedPoints }), 'success')
          }
          notifyCreateSubmitted(createdPost.postId, payload)
        },
        onError: (error) => {
          unlock()
          logger.error('Failed to create post:', error)
        },
      })
      return
    }

    options.updatePost({ postId: options.postId.value, data: payload }, {
      onSuccess: () => {
        unlock()
        options.releaseUploadedFileOwnership(payload.fileIds)
        options.markCurrentSnapshotSaved()
        options.cleanupPublishedDraft()
        options.onSubmitted()?.({
          mode: 'edit',
          boardUrl: options.boardUrl.value,
          postId: options.postId.value,
          isSecret: payload.isSecret,
          isBoardAdmin: options.board.value?.isAdmin ?? false,
        })
      },
      onError: (error) => {
        unlock()
        logger.error('Failed to update post:', error)
      },
    })
    } catch (error) {
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
