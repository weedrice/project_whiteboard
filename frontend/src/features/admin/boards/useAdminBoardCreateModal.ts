import { reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import { normalizeBoardUrlInput, validateBoardWriteFields } from '@/utils/board'
import { normalizeBoardWritePayload } from '@/utils/inputNormalization'
import {
  captureAuthSessionIntent,
  isAuthSessionIntentCurrent,
  type AuthSessionIntent,
} from '@/utils/authSessionIntent'
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
  const authStore = useAuthStore()
  const isModalOpen = ref(false)
  const isCreatingBoard = ref(false)
  const createForm = reactive<BoardCreateData>(createEmptyForm())
  let modalGeneration = 0
  let requestSequence = 0
  let activeRequest = 0
  let modalSessionIntent: AuthSessionIntent | null = null

  function resetCreateForm() {
    Object.assign(createForm, createEmptyForm())
  }

  function openCreateModal() {
    if (isCreatingBoard.value) return
    modalGeneration += 1
    activeRequest = 0
    isCreatingBoard.value = false
    resetCreateForm()
    modalSessionIntent = captureAuthSessionIntent(authStore)
    isModalOpen.value = true
  }

  function forceCloseModal() {
    modalGeneration += 1
    activeRequest = 0
    modalSessionIntent = null
    isCreatingBoard.value = false
    isModalOpen.value = false
  }

  function closeModal() {
    if (isCreatingBoard.value) return
    forceCloseModal()
  }

  watch(() => createForm.boardUrl, (boardUrl) => {
    const normalizedBoardUrl = normalizeBoardUrlInput(boardUrl)
    if (boardUrl !== normalizedBoardUrl) {
      createForm.boardUrl = normalizedBoardUrl
    }
  })

  async function handleCreateBoard() {
    if (
      isCreatingBoard.value
      || !isModalOpen.value
      || !modalSessionIntent
      || !isAuthSessionIntentCurrent(authStore, modalSessionIntent)
    ) {
      return
    }

    const requiredFieldValidation = validateBoardWriteFields(createForm)
    if (!requiredFieldValidation.valid) {
      toastStore.addToast(t(requiredFieldValidation.messageKey), requiredFieldValidation.toastType)
      return
    }

    const submittedGeneration = modalGeneration
    const submittedSessionIntent = modalSessionIntent
    const requestId = ++requestSequence
    activeRequest = requestId
    isCreatingBoard.value = true
    try {
      await createBoard(normalizeBoardWritePayload(createForm))
      if (
        submittedGeneration !== modalGeneration
        || activeRequest !== requestId
        || !isAuthSessionIntentCurrent(authStore, submittedSessionIntent)
      ) {
        return
      }
      toastStore.addToast(t('admin.boards.messages.created'), 'success')
      forceCloseModal()
    } catch {
      // Error handled globally
    } finally {
      if (submittedGeneration === modalGeneration && activeRequest === requestId) {
        activeRequest = 0
        isCreatingBoard.value = false
      }
    }
  }

  watch(
    () => [authStore.sessionGeneration, authStore.user?.userId] as const,
    () => {
      if (!isModalOpen.value) return
      forceCloseModal()
      resetCreateForm()
    },
    { flush: 'sync' },
  )

  return {
    closeModal,
    createForm,
    handleCreateBoard,
    isCreatingBoard,
    isModalOpen,
    openCreateModal
  }
}
