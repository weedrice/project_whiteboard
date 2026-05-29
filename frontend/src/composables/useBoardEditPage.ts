import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import { useFormSubmit } from '@/composables/useFormSubmit'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useBoard } from '@/composables/useBoard'
import {
  assertBoardManageable,
  createEmptyBoardEditForm,
  resolveBoardManagerLabel,
  toBoardEditForm,
  type BoardEditFormData
} from '@/composables/useBoardEditResource'
import type { BoardUpdateData } from '@/types'

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
  const isManagerModalOpen = ref(false)
  const isTransferringManager = ref(false)
  const currentManagerLabel = ref('')
  let managerTransferRequestId = 0

  function resetBoardState() {
    managerTransferRequestId += 1
    form.value = createEmptyBoardEditForm()
    error.value = ''
    canManageBoard.value = true
    currentManagerLabel.value = ''
    isManagerModalOpen.value = false
    isTransferringManager.value = false
  }

  async function handleUpdate(formData: BoardEditFormData) {
    error.value = ''

    await submit(async () => {
      try {
        const board = await updateBoard({ boardUrl: boardUrl.value, data: formData as BoardUpdateData })
        toastStore.addToast(t('board.form.successUpdate'), 'success')
        router.push(`/board/${board.boardUrl}`)
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

  function openManagerModal() {
    isManagerModalOpen.value = true
  }

  function closeManagerModal() {
    isManagerModalOpen.value = false
  }

  async function confirmManagerSelection(users: Array<{ loginId: string; displayName?: string }>) {
    if (users.length === 0) return

    const selectedUser = users[0]
    const transferBoardUrl = boardUrl.value
    const requestId = ++managerTransferRequestId

    isTransferringManager.value = true
    try {
      const updatedBoard = await transferBoardManager({
        boardUrl: transferBoardUrl,
        loginId: selectedUser.loginId
      })
      if (requestId !== managerTransferRequestId || transferBoardUrl !== boardUrl.value) return
      currentManagerLabel.value = updatedBoard.adminDisplayName || `${selectedUser.displayName} (${selectedUser.loginId})`
      closeManagerModal()
      toastStore.addToast(t('common.messages.saveSuccess'), 'success')
    } catch (err: unknown) {
      if (requestId !== managerTransferRequestId || transferBoardUrl !== boardUrl.value) return
      handleError(err, t('common.messages.saveFailed'))
    } finally {
      if (requestId === managerTransferRequestId && transferBoardUrl === boardUrl.value) {
        isTransferringManager.value = false
      }
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
      router.push(`/board/${currentBoardUrl}`)
      return
    }

    form.value = toBoardEditForm(board)
    currentManagerLabel.value = resolveBoardManagerLabel(board, t('common.noData'))
  }, { immediate: true })

  watch(boardLoadError, (err) => {
    const currentBoardUrl = boardUrl.value
    if (!err || !currentBoardUrl) return

    handleError(err, t('board.loadFailed'))
    router.push(`/board/${currentBoardUrl}`)
  })

  onUnmounted(() => {
    managerTransferRequestId += 1
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
