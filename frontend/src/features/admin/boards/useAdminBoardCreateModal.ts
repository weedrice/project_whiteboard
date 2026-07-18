import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { normalizeBoardUrlInput, validateBoardWriteFields } from '@/utils/board'
import { normalizeBoardWritePayload } from '@/utils/inputNormalization'
import type { BoardCreateData } from '@/types'

type CreateBoard = (data: BoardCreateData) => Promise<unknown>

const createEmptyForm = (): BoardCreateData => ({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: '',
  agentUseYn: false,
  guidePrompt: ''
})

export function useAdminBoardCreateModal(createBoard: CreateBoard) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const isModalOpen = ref(false)
  const isCreatingBoard = ref(false)
  const createForm = reactive<BoardCreateData>(createEmptyForm())
  let modalGeneration = 0
  let requestSequence = 0
  let activeRequest = 0

  function resetCreateForm() {
    Object.assign(createForm, createEmptyForm())
  }

  function openCreateModal() {
    modalGeneration += 1
    activeRequest = 0
    isCreatingBoard.value = false
    resetCreateForm()
    isModalOpen.value = true
  }

  function closeModal() {
    modalGeneration += 1
    activeRequest = 0
    isCreatingBoard.value = false
    isModalOpen.value = false
  }

  watch(() => createForm.boardUrl, (boardUrl) => {
    const normalizedBoardUrl = normalizeBoardUrlInput(boardUrl)
    if (boardUrl !== normalizedBoardUrl) {
      createForm.boardUrl = normalizedBoardUrl
    }
  })

  async function handleCreateBoard() {
    if (isCreatingBoard.value) {
      return
    }

    const requiredFieldValidation = validateBoardWriteFields(createForm)
    if (!requiredFieldValidation.valid) {
      toastStore.addToast(t(requiredFieldValidation.messageKey), requiredFieldValidation.toastType)
      return
    }

    const submittedGeneration = modalGeneration
    const requestId = ++requestSequence
    activeRequest = requestId
    isCreatingBoard.value = true
    try {
      await createBoard(normalizeBoardWritePayload(createForm))
      if (submittedGeneration !== modalGeneration || activeRequest !== requestId) {
        return
      }
      toastStore.addToast(t('admin.boards.messages.created'), 'success')
      closeModal()
    } catch {
      // Error handled globally
    } finally {
      if (submittedGeneration === modalGeneration && activeRequest === requestId) {
        activeRequest = 0
        isCreatingBoard.value = false
      }
    }
  }

  return {
    closeModal,
    createForm,
    handleCreateBoard,
    isCreatingBoard,
    isModalOpen,
    openCreateModal
  }
}
