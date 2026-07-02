import type { Ref } from 'vue'
import { unwrapApiData } from '@/api/response'
import logger from '@/utils/logger'
import type { ApiResponse } from '@/types'

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
  fileIds: number[]
  draftId?: number
}

export type PostFormSubmitResult = {
  mode: PostComposerMode
  boardUrl: string
  postId?: string | number
  newPostId?: string | number
  isSecret: boolean
  isBoardAdmin: boolean
}

type CreatePostMutate = (
  variables: { boardUrl: string, data: PostComposerPayload },
  options: {
    onSuccess: (response: { data: ApiResponse<string | number> }) => void
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

type UsePostComposerSubmitOptions = {
  mode: () => PostComposerMode
  boardUrl: Ref<string>
  postId: Ref<string | number>
  board: Ref<{ isAdmin?: boolean } | null | undefined>
  form: Ref<{ title: string, categoryId: string | number }>
  hideCategory: () => boolean | undefined
  draftEnabled: Ref<boolean>
  draftId: Ref<number | null>
  saveDraftNow: () => Promise<{ draftId?: number | null } | null>
  buildPayload: () => Omit<PostComposerPayload, 'draftId'>
  markCurrentSnapshotSaved: () => void
  cleanupPublishedDraft: () => void
  createPost: CreatePostMutate
  updatePost: UpdatePostMutate
  onSubmitted: () => ((result: PostFormSubmitResult) => void) | undefined
  createSuccessToastMessage: () => string | undefined
  t: (key: string) => string
  addToast: (message: string, type: ComposerToastType) => void
}

export function usePostComposerSubmit(options: UsePostComposerSubmitOptions) {
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
    if (!options.form.value.title.trim()) {
      options.addToast(options.t('board.writePost.validation'), 'error')
      return
    }
    if (options.mode() === 'create' && !options.hideCategory() && !options.form.value.categoryId) {
      options.addToast(options.t('board.writePost.validation'), 'error')
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
        return
      }
    }
    const payload = {
      ...options.buildPayload(),
      ...(currentDraftId !== undefined && { draftId: currentDraftId }),
    }

    if (options.mode() === 'create') {
      options.createPost({ boardUrl: options.boardUrl.value, data: payload }, {
        onSuccess: (response) => {
          options.markCurrentSnapshotSaved()
          options.cleanupPublishedDraft()
          const successToastMessage = options.createSuccessToastMessage()
          if (successToastMessage) {
            options.addToast(successToastMessage, 'success')
          }
          notifyCreateSubmitted(unwrapApiData(response.data), payload)
        },
        onError: (error) => {
          logger.error('Failed to create post:', error)
        },
      })
      return
    }

    options.updatePost({ postId: options.postId.value, data: payload }, {
      onSuccess: () => {
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
        logger.error('Failed to update post:', error)
      },
    })
  }

  return {
    handleSubmit,
  }
}
