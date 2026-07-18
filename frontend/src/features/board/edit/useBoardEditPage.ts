import { computed, onScopeDispose, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBoard } from '@/features/board/useBoard'
import {
  assertBoardManageable,
  createEmptyBoardEditForm,
  resolveBoardManagerLabel,
  toBoardEditForm,
  type BoardEditFormData
} from '@/features/board/edit/useBoardEditResource'
import { useBoardEditManagerAssignment } from '@/features/board/edit/useBoardEditManagerAssignment'
import type { BoardUpdateData } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import { useAuthStore } from '@/stores/auth'
import { captureAuthSessionIntent, isAuthSessionIntentCurrent } from '@/utils/authSessionIntent'
import { isCancellationError } from '@/utils/cancellationError'
import type { BoardFormSubmitContext } from '@/features/board/form/useBoardFormSubmit'

export function useBoardEditPage() {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const authStore = useAuthStore()
  const { confirm } = useConfirm()
  const route = useRoute()
  const router = useRouter()
  const boardUrl = computed(() => String(route.params.boardUrl ?? ''))

  const form = ref(createEmptyBoardEditForm())
  const { isSubmitting, submit } = useFormSubmit()
  const { handleError } = useErrorHandler()
  const { useBoardDetail, useUpdateBoard, useDeleteBoard, useTransferBoardManager } = useBoard()
  const { data: boardData, isLoading, error: boardLoadError } = useBoardDetail(boardUrl)
  const { mutateAsync: updateBoard } = useUpdateBoard()
  const { mutateAsync: deleteBoard } = useDeleteBoard()
  const { mutateAsync: transferBoardManager } = useTransferBoardManager()

  const error = ref('')
  const canManageBoard = ref(true)
  let updateController: AbortController | null = null
  const {
    closeManagerModal,
    confirmManagerSelection,
    currentManagerLabel,
    isManagerModalOpen,
    isTransferringManager,
    openManagerModal,
    resetManagerAssignmentState,
    setCurrentManagerLabel,
  } = useBoardEditManagerAssignment({
    boardUrl,
    transferBoardManager,
  })

  function resetBoardState() {
    form.value = createEmptyBoardEditForm()
    error.value = ''
    canManageBoard.value = true
    resetManagerAssignmentState()
  }

  async function handleUpdate(
    formData: BoardEditFormData,
    submissionContext?: BoardFormSubmitContext,
  ): Promise<boolean> {
    error.value = ''
    const targetBoardUrl = boardUrl.value
    const routeIntent = route.fullPath
    const intent = captureAuthSessionIntent(authStore)

    return submit(async () => {
      updateController?.abort()
      const controller = new AbortController()
      const abortFromSubmission = () => controller.abort()
      if (submissionContext?.signal.aborted) {
        controller.abort()
      } else {
        submissionContext?.signal.addEventListener('abort', abortFromSubmission, { once: true })
      }
      const signal = controller.signal
      updateController = controller
      try {
        const board = await updateBoard({
          boardUrl: targetBoardUrl,
          data: formData as BoardUpdateData,
          signal,
        })
        submissionContext?.commitUpload()
        if (
          signal.aborted
          || !isAuthSessionIntentCurrent(authStore, intent)
          || boardUrl.value !== targetBoardUrl
          || route.fullPath !== routeIntent
        ) return true
        toastStore.addToast(t('board.form.successUpdate'), 'success')
        await router.push(`/board/${encodePathSegment(board.boardUrl)}`)
        return true
      } catch (err: unknown) {
        if (
          signal.aborted
          || !isAuthSessionIntentCurrent(authStore, intent)
          || boardUrl.value !== targetBoardUrl
          || isCancellationError(err)
        ) return false
        error.value = t('board.form.updateFailed')
        handleError(err, t('board.form.updateFailed'))
        return false
      } finally {
        submissionContext?.signal.removeEventListener('abort', abortFromSubmission)
        if (updateController === controller) updateController = null
      }
    })
  }

  async function handleDelete() {
    const targetBoardUrl = boardUrl.value
    const sessionGeneration = authStore.sessionGeneration
    const isConfirmed = await confirm(t('board.form.deleteConfirm'))
    if (
      !isConfirmed
      || boardUrl.value !== targetBoardUrl
      || authStore.sessionGeneration !== sessionGeneration
    ) return

    try {
      await deleteBoard(targetBoardUrl)
      if (
        boardUrl.value !== targetBoardUrl
        || authStore.sessionGeneration !== sessionGeneration
      ) return
      toastStore.addToast(t('board.form.successDelete'), 'success')
      router.push('/')
    } catch (err: unknown) {
      if (
        boardUrl.value !== targetBoardUrl
        || authStore.sessionGeneration !== sessionGeneration
      ) return
      handleError(err, t('board.form.deleteFailed'))
    }
  }

  watch(boardUrl, () => {
    updateController?.abort()
    resetBoardState()
  }, { immediate: true })

  watch(() => authStore.sessionGeneration, () => updateController?.abort())
  onScopeDispose(() => updateController?.abort())

  watch(boardData, (board) => {
    const currentBoardUrl = boardUrl.value
    if (!board || !currentBoardUrl || board.boardUrl !== currentBoardUrl) return

    if (!assertBoardManageable(board)) {
      canManageBoard.value = false
      toastStore.addToast(t('common.messages.forbidden'), 'error')
      router.push(`/board/${encodePathSegment(currentBoardUrl)}`)
      return
    }

    form.value = toBoardEditForm(board)
    setCurrentManagerLabel(resolveBoardManagerLabel(board, t('common.noData')))
  }, { immediate: true })

  watch(boardLoadError, (err) => {
    const currentBoardUrl = boardUrl.value
    if (!err || !currentBoardUrl) return

    handleError(err, t('board.loadFailed'))
    router.push(`/board/${encodePathSegment(currentBoardUrl)}`)
  })

  return {
    boardUrl,
    canManageBoard,
    closeManagerModal,
    confirmManagerSelection,
    currentManagerLabel,
    error,
    form,
    goBack: router.back,
    handleDelete,
    handleUpdate,
    isLoading,
    isManagerModalOpen,
    isSubmitting,
    isTransferringManager,
    openManagerModal
  }
}
