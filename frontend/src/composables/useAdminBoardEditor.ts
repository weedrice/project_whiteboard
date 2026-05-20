import { computed, reactive, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirm } from '@/composables/useConfirm'
import { useToastStore } from '@/stores/toast'
import type { AdminBoard, BoardUpdateData } from '@/types'

type UpdateBoardPayload = {
  boardUrl: string
  data: BoardUpdateData
}

interface UseAdminBoardEditorOptions {
  boardsData: Ref<AdminBoard[] | undefined>
  updateBoard: (payload: UpdateBoardPayload) => Promise<unknown>
}

export function useAdminBoardEditor({ boardsData, updateBoard }: UseAdminBoardEditorOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { confirm } = useConfirm()

  const boards = ref<AdminBoard[]>([])
  const originalBoardUrls = ref<Record<number, string>>({})
  const modifiedBoardIds = ref<number[]>([])
  const selectedBoardId = ref<number | null>(null)
  const isSubmitting = ref(false)
  const isSavingSortOrder = ref(false)

  const form = reactive({
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

  const hasUnsavedChanges = computed(() => isSelectedFormDirty.value || modifiedBoardIds.value.length > 0)

  watch(boardsData, (newData) => {
    const list = (newData || []) as AdminBoard[]
    const copied = JSON.parse(JSON.stringify(list)) as AdminBoard[]
    copied.sort((a, b) => a.sortOrder - b.sortOrder)

    boards.value = copied
    originalBoardUrls.value = Object.fromEntries(copied.map((board) => [board.boardId, board.boardUrl]))

    if (copied.length === 0) {
      selectedBoardId.value = null
      return
    }

    if (!selectedBoardId.value || !copied.some((board) => board.boardId === selectedBoardId.value)) {
      selectedBoardId.value = copied[0].boardId
    }
  }, { immediate: true })

  watch(selectedBoard, (board) => {
    if (!board) return
    form.boardName = board.boardName
    form.boardUrl = board.boardUrl
    form.description = board.description || ''
    form.iconUrl = board.iconUrl || ''
    form.sortOrder = String(board.sortOrder)
    form.isActive = board.isActive
    form.agentUseYn = board.isPublic ? (board.agentUseYn ?? false) : false
    form.guidePrompt = board.guidePrompt || ''
  }, { immediate: true })

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
        markBoardModified(board.boardId)
        changedBoardIds.push(board.boardId)
      }
    })

    if (selectedBoard.value) {
      form.sortOrder = String(selectedBoard.value.sortOrder)
    }

    return changedBoardIds
  }

  async function saveBoardUpdates(boardIds: number[], showSuccessToast = true) {
    const uniqueBoardIds = [...new Set(boardIds)]
    if (uniqueBoardIds.length === 0) return

    const updates = uniqueBoardIds.map((boardId) => {
      const board = boards.value.find((item) => item.boardId === boardId)
      if (!board) return Promise.resolve()

      const requestBoardUrl = originalBoardUrls.value[board.boardId] || board.boardUrl

      return updateBoard({
        boardUrl: requestBoardUrl,
        data: {
          boardName: board.boardName,
          boardUrl: board.boardUrl,
          description: board.description || '',
          iconUrl: board.iconUrl || '',
          allowNsfw: board.allowNsfw,
          sortOrder: board.sortOrder,
          isActive: board.isActive,
          agentUseYn: board.agentUseYn ?? false,
          guidePrompt: board.guidePrompt || ''
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

  async function handleDragEnd() {
    if (isSavingSortOrder.value) return

    const changedBoardIds = renumberSortOrder()
    if (changedBoardIds.length === 0) return

    isSavingSortOrder.value = true
    try {
      await saveBoardUpdates(changedBoardIds, false)
    } catch {
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
      const isConfirmed = await confirm('저장하지 않은 변경사항이 있습니다. 이동하시겠습니까?')
      if (!isConfirmed) return
    }

    selectedBoardId.value = board.boardId
  }

  function applySelectedBoardForm() {
    if (!selectedBoard.value) return

    const board = selectedBoard.value

    if (board.boardName !== form.boardName) {
      board.boardName = form.boardName
      markBoardModified(board.boardId)
    }

    if (board.boardUrl !== form.boardUrl) {
      board.boardUrl = form.boardUrl
      markBoardModified(board.boardId)
    }

    if ((board.description || '') !== form.description) {
      board.description = form.description
      markBoardModified(board.boardId)
    }

    if ((board.iconUrl || '') !== form.iconUrl) {
      board.iconUrl = form.iconUrl
      markBoardModified(board.boardId)
    }

    if (board.isActive !== form.isActive) {
      board.isActive = form.isActive
      markBoardModified(board.boardId)
    }

    const nextAgentUseYn = board.isPublic ? form.agentUseYn : false
    if ((board.agentUseYn ?? false) !== nextAgentUseYn) {
      board.agentUseYn = nextAgentUseYn
      markBoardModified(board.boardId)
    }

    if ((board.guidePrompt || '') !== form.guidePrompt) {
      board.guidePrompt = form.guidePrompt
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

    if (!form.boardName || !form.boardUrl) {
      toastStore.addToast(t('board.writePost.validation'), 'warning')
      return
    }

    applySelectedBoardForm()

    if (modifiedBoardIds.value.length === 0) {
      return
    }

    isSubmitting.value = true
    try {
      await saveBoardUpdates(modifiedBoardIds.value, true)
    } catch {
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
