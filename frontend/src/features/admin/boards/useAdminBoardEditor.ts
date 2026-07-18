import { computed, reactive, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import { normalizeBoardUrlInput, validateBoardWriteFields } from '@/utils/board'
import { normalizeBoardWritePayload } from '@/utils/inputNormalization'
import type { AdminBoard, BoardUpdateData } from '@/types'

type UpdateBoardPayload = {
  boardUrl: string
  data: BoardUpdateData
}

interface BoardEditorSnapshot {
  boards: AdminBoard[]
  originalBoardUrls: Record<number, string>
  modifiedBoardIds: number[]
  selectedBoardId: number | null
  orderDirty: boolean
}

interface UseAdminBoardEditorOptions {
  boardsData: Ref<AdminBoard[] | undefined>
  updateBoard: (payload: UpdateBoardPayload) => Promise<unknown>
  reorderBoards: (boardIds: number[]) => Promise<AdminBoard[]>
  refetchBoards: () => Promise<unknown>
}

export interface AdminBoardEditorForm {
  boardName: string
  boardUrl: string
  description: string
  iconUrl: string
  sortOrder: string
  isActive: boolean
  agentUseYn: boolean
  guidePrompt: string
}

export function useAdminBoardEditor({ boardsData, updateBoard, reorderBoards, refetchBoards }: UseAdminBoardEditorOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { confirm, confirmWithReason } = useConfirm()

  const boards = ref<AdminBoard[]>([])
  const originalBoardUrls = ref<Record<number, string>>({})
  const modifiedBoardIds = ref<number[]>([])
  const selectedBoardId = ref<number | null>(null)
  const isSubmitting = ref(false)
  const isSavingSortOrder = ref(false)
  const orderDirty = ref(false)

  const form = reactive<AdminBoardEditorForm>({
    boardName: '',
    boardUrl: '',
    description: '',
    iconUrl: '',
    sortOrder: '0',
    isActive: true,
    agentUseYn: false,
    guidePrompt: ''
  })

  const selectedBoard = computed<AdminBoard | null>(() => {
    if (!selectedBoardId.value) return null
    return boards.value.find((board) => board.boardId === selectedBoardId.value) || null
  })

  const normalizedSortOrder = computed(() => Number.parseInt(String(form.sortOrder || '0'), 10) || 0)

  const isSelectedFormDirty = computed(() => {
    if (!selectedBoard.value) return false

    return (
      form.boardName !== selectedBoard.value.boardName ||
      form.boardUrl !== selectedBoard.value.boardUrl ||
      form.description !== (selectedBoard.value.description || '') ||
      form.iconUrl !== (selectedBoard.value.iconUrl || '') ||
      normalizedSortOrder.value !== selectedBoard.value.sortOrder ||
      form.isActive !== selectedBoard.value.isActive ||
      form.agentUseYn !== (selectedBoard.value.agentUseYn ?? false) ||
      form.guidePrompt !== (selectedBoard.value.guidePrompt || '')
    )
  })

  const hasUnsavedChanges = computed(() => (
    isSelectedFormDirty.value || modifiedBoardIds.value.length > 0 || orderDirty.value
  ))

  function cloneBoards(list: AdminBoard[]) {
    return list.map((board) => ({ ...board }))
  }

  function sortedBoardCopies(list: AdminBoard[]) {
    return cloneBoards(list).sort((a, b) => a.sortOrder - b.sortOrder)
  }

  function syncFormFromBoard(board: AdminBoard) {
    form.boardName = board.boardName
    form.boardUrl = board.boardUrl
    form.description = board.description || ''
    form.iconUrl = board.iconUrl || ''
    form.sortOrder = String(board.sortOrder)
    form.isActive = board.isActive
    form.agentUseYn = board.isPublic ? (board.agentUseYn ?? false) : false
    form.guidePrompt = board.guidePrompt || ''
  }

  function createSnapshot(snapshotBoards = cloneBoards(boards.value)): BoardEditorSnapshot {
    return {
      boards: snapshotBoards,
      originalBoardUrls: { ...originalBoardUrls.value },
      modifiedBoardIds: [...modifiedBoardIds.value],
      selectedBoardId: selectedBoardId.value,
      orderDirty: orderDirty.value,
    }
  }

  function restoreSnapshot(snapshot: BoardEditorSnapshot) {
    boards.value = cloneBoards(snapshot.boards)
    originalBoardUrls.value = { ...snapshot.originalBoardUrls }
    modifiedBoardIds.value = [...snapshot.modifiedBoardIds]
    selectedBoardId.value = snapshot.selectedBoardId
    orderDirty.value = snapshot.orderDirty

    if (selectedBoard.value) {
      syncFormFromBoard(selectedBoard.value)
    }
  }

  watch(boardsData, (newData) => {
    const list = (newData || []) as AdminBoard[]
    const copied = sortedBoardCopies(list)
    const previousBoards = new Map(boards.value.map((board) => [board.boardId, board]))
    const previousOrder = boards.value.map((board) => board.boardId)
    const selectedId = selectedBoardId.value
    const preserveSelectedForm = isSelectedFormDirty.value
    const formSnapshot = { ...form }

    const merged = copied.map((board) => {
      const previousBoard = previousBoards.get(board.boardId)
      if (previousBoard && modifiedBoardIds.value.includes(board.boardId)) {
        return { ...previousBoard }
      }

      return board
    })

    const nextBoards = orderDirty.value
      ? [...merged].sort((a, b) => {
          const aIndex = previousOrder.indexOf(a.boardId)
          const bIndex = previousOrder.indexOf(b.boardId)
          if (aIndex < 0) return bIndex < 0 ? a.sortOrder - b.sortOrder : 1
          if (bIndex < 0) return -1
          return aIndex - bIndex
        }).map((board, index) => ({ ...board, sortOrder: index + 1 }))
      : merged

    boards.value = nextBoards
    originalBoardUrls.value = Object.fromEntries(nextBoards.map((board) => [
      board.boardId,
      modifiedBoardIds.value.includes(board.boardId)
        ? (originalBoardUrls.value[board.boardId] || board.boardUrl)
        : board.boardUrl,
    ]))

    if (nextBoards.length === 0) {
      selectedBoardId.value = null
      return
    }

    if (!selectedBoardId.value || !nextBoards.some((board) => board.boardId === selectedBoardId.value)) {
      selectedBoardId.value = nextBoards[0].boardId
      syncFormFromBoard(nextBoards[0])
    } else if (preserveSelectedForm && selectedId === selectedBoardId.value) {
      Object.assign(form, formSnapshot)
    } else if (selectedBoard.value) {
      syncFormFromBoard(selectedBoard.value)
    }
  }, { immediate: true })

  watch(selectedBoardId, () => {
    const board = selectedBoard.value
    if (!board) return
    syncFormFromBoard(board)
  }, { immediate: true })

  watch(() => form.boardUrl, (boardUrl) => {
    const normalizedBoardUrl = normalizeBoardUrlInput(boardUrl)
    if (boardUrl !== normalizedBoardUrl) {
      form.boardUrl = normalizedBoardUrl
    }
  })

  function markBoardModified(boardId: number) {
    if (!modifiedBoardIds.value.includes(boardId)) {
      modifiedBoardIds.value = [...modifiedBoardIds.value, boardId]
    }
  }

  function renumberSortOrder() {
    const changedBoardIds: number[] = []

    boards.value.forEach((board, index) => {
      const nextSortOrder = index + 1
      if (board.sortOrder !== nextSortOrder) {
        board.sortOrder = nextSortOrder
        changedBoardIds.push(board.boardId)
      }
    })

    if (selectedBoard.value) {
      form.sortOrder = String(selectedBoard.value.sortOrder)
    }

    if (changedBoardIds.length > 0) orderDirty.value = true

    return changedBoardIds
  }

  async function saveBoardUpdates(boardIds: number[], showSuccessToast = true, moderationReason?: string) {
    const uniqueBoardIds = [...new Set(boardIds)]
    if (uniqueBoardIds.length === 0) return

    const updates = uniqueBoardIds.map((boardId) => {
      const board = boards.value.find((item) => item.boardId === boardId)
      if (!board) return Promise.resolve()

      const requestBoardUrl = originalBoardUrls.value[board.boardId] || board.boardUrl

      return updateBoard({
        boardUrl: requestBoardUrl,
        data: {
          ...normalizeBoardWritePayload({
          boardName: board.boardName,
          boardUrl: board.boardUrl,
          description: board.description || '',
          iconUrl: board.iconUrl || '',
          allowNsfw: board.allowNsfw,
          isActive: board.isActive,
          agentUseYn: board.agentUseYn ?? false,
          guidePrompt: board.guidePrompt || ''
          }, { isPublic: board.isPublic }),
          ...(moderationReason && board.boardId === selectedBoardId.value ? { moderationReason } : {})
        }
      }).then(() => {
        originalBoardUrls.value[board.boardId] = board.boardUrl
      })
    })

    await Promise.all(updates)

    modifiedBoardIds.value = modifiedBoardIds.value.filter((boardId) => !uniqueBoardIds.includes(boardId))

    if (showSuccessToast) {
      toastStore.addToast(t('common.messages.saveSuccess'), 'success')
    }
  }

  async function saveBoardOrder() {
    const canonicalBoards = await reorderBoards(boards.value.map((board) => board.boardId))
    const copied = sortedBoardCopies(canonicalBoards)
    boards.value = copied
    originalBoardUrls.value = Object.fromEntries(copied.map((board) => [board.boardId, board.boardUrl]))
    orderDirty.value = false
    if (selectedBoard.value) syncFormFromBoard(selectedBoard.value)
  }

  async function handleDragEnd() {
    if (isSavingSortOrder.value) return

    const snapshot = createSnapshot(sortedBoardCopies(boards.value))
    const changedBoardIds = renumberSortOrder()
    if (changedBoardIds.length === 0) return

    isSavingSortOrder.value = true
    try {
      await saveBoardOrder()
    } catch {
      restoreSnapshot(snapshot)
      // Error handled globally
    } finally {
      isSavingSortOrder.value = false
    }
  }

  function toggleBoardStatus() {
    form.isActive = !form.isActive
  }

  async function selectBoard(board: AdminBoard) {
    if (selectedBoardId.value === board.boardId) return

    if (hasUnsavedChanges.value) {
      const isConfirmed = await confirm(t('admin.boards.messages.confirmDiscardChanges'))
      if (!isConfirmed) return
    }

    selectedBoardId.value = board.boardId
  }

  function applySelectedBoardForm() {
    if (!selectedBoard.value) return

    const board = selectedBoard.value
    const normalizedForm = normalizeBoardWritePayload({
      boardName: form.boardName,
      description: form.description,
      iconUrl: form.iconUrl,
      agentUseYn: form.agentUseYn,
      guidePrompt: form.guidePrompt,
    }, { isPublic: board.isPublic })

    if (board.boardName !== normalizedForm.boardName) {
      board.boardName = normalizedForm.boardName
      markBoardModified(board.boardId)
    }

    if (board.boardUrl !== form.boardUrl) {
      board.boardUrl = form.boardUrl
      markBoardModified(board.boardId)
    }

    if ((board.description || '') !== normalizedForm.description) {
      board.description = normalizedForm.description
      markBoardModified(board.boardId)
    }

    if ((board.iconUrl || '') !== normalizedForm.iconUrl) {
      board.iconUrl = normalizedForm.iconUrl
      markBoardModified(board.boardId)
    }

    if (board.isActive !== form.isActive) {
      board.isActive = form.isActive
      markBoardModified(board.boardId)
    }

    const nextAgentUseYn = normalizedForm.agentUseYn ?? false
    if ((board.agentUseYn ?? false) !== nextAgentUseYn) {
      board.agentUseYn = nextAgentUseYn
      markBoardModified(board.boardId)
    }

    if ((board.guidePrompt || '') !== normalizedForm.guidePrompt) {
      board.guidePrompt = normalizedForm.guidePrompt
      markBoardModified(board.boardId)
    }

    const targetPosition = Math.max(1, Math.min(normalizedSortOrder.value, boards.value.length))
    form.sortOrder = String(targetPosition)
    const currentIndex = boards.value.findIndex((item) => item.boardId === board.boardId)

    if (currentIndex >= 0 && currentIndex !== targetPosition - 1) {
      const [movedBoard] = boards.value.splice(currentIndex, 1)
      boards.value.splice(targetPosition - 1, 0, movedBoard)
      renumberSortOrder()
    }
  }

  async function handleSaveChanges() {
    if (!selectedBoard.value) return

    const validation = validateBoardWriteFields({
      ...form,
      sortOrder: normalizedSortOrder.value,
    })
    if (!validation.valid) {
      toastStore.addToast(t('board.writePost.validation'), 'warning')
      return
    }

    const moderationReason = selectedBoard.value.isActive && !form.isActive
      ? await confirmWithReason(
          t('admin.boards.messages.confirmDeactivate'),
          undefined,
          undefined,
          undefined,
          undefined,
          { maxLength: 500 },
        )
      : undefined
    if (selectedBoard.value.isActive && !form.isActive && !moderationReason) return

    const snapshot = createSnapshot()
    applySelectedBoardForm()

    if (modifiedBoardIds.value.length === 0 && !orderDirty.value) {
      return
    }

    isSubmitting.value = true
    try {
      await saveBoardUpdates(modifiedBoardIds.value, false, moderationReason ?? undefined)
      if (orderDirty.value) await saveBoardOrder()
      toastStore.addToast(t('common.messages.saveSuccess'), 'success')
    } catch {
      restoreSnapshot(snapshot)
      await refetchBoards()
      // Error handled globally
    } finally {
      isSubmitting.value = false
    }
  }

  return {
    boards,
    selectedBoardId,
    selectedBoard,
    form,
    hasUnsavedChanges,
    isSubmitting,
    isSavingSortOrder,
    handleDragEnd,
    toggleBoardStatus,
    selectBoard,
    handleSaveChanges
  }
}
