import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { normalizeBoardUrlInput, validateRequiredBoardFields } from '@/utils/board'
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

  function resetCreateForm() {
    Object.assign(createForm, createEmptyForm())
  }

  function openCreateModal() {
    resetCreateForm()
    isModalOpen.value = true
  }

  function closeModal() {
    isModalOpen.value = false
  }

  watch(() => createForm.boardUrl, (boardUrl) => {
    const normalizedBoardUrl = normalizeBoardUrlInput(boardUrl)
    if (boardUrl !== normalizedBoardUrl) {
      createForm.boardUrl = normalizedBoardUrl
    }
  })

  async function handleCreateBoard() {
    const requiredFieldValidation = validateRequiredBoardFields(createForm)
    if (!requiredFieldValidation.valid) {
      toastStore.addToast(t(requiredFieldValidation.messageKey), requiredFieldValidation.toastType)
      return
    }

    isCreatingBoard.value = true
    try {
      await createBoard(normalizeBoardWritePayload(createForm))
      toastStore.addToast(t('admin.boards.messages.created'), 'success')
      closeModal()
    } catch {
      // Error handled globally
    } finally {
      isCreatingBoard.value = false
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
