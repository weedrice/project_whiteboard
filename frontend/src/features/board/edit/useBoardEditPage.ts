import { computed, ref, watch } from 'vue'
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

export function useBoardEditPage() {
  const { t } = useI18n()
  const toastStore = useToastStore()
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

  async function handleUpdate(formData: BoardEditFormData) {
    error.value = ''

    await submit(async () => {
      try {
        const board = await updateBoard({ boardUrl: boardUrl.value, data: formData as BoardUpdateData })
        toastStore.addToast(t('board.form.successUpdate'), 'success')
        router.push(`/board/${encodePathSegment(board.boardUrl)}`)
      } catch (err: unknown) {
        error.value = t('board.form.updateFailed')
        handleError(err, t('board.form.updateFailed'))
        throw err
      }
    })
  }

  async function handleDelete() {
    const isConfirmed = await confirm(t('board.form.deleteConfirm'))
    if (!isConfirmed) return

    try {
      await deleteBoard(boardUrl.value)
      toastStore.addToast(t('board.form.successDelete'), 'success')
      router.push('/')
    } catch (err: unknown) {
      handleError(err, t('board.form.deleteFailed'))
    }
  }

  watch(boardUrl, () => {
    resetBoardState()
  }, { immediate: true })

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
