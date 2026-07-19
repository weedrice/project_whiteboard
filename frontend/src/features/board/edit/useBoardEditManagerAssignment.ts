import { onUnmounted, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { BoardDetail } from '@/types'

type ManagerCandidate = {
  loginId: string
  displayName?: string
}

type TransferBoardManager = (payload: {
  boardUrl: string
  loginId: string
}) => Promise<BoardDetail>

interface UseBoardEditManagerAssignmentOptions {
  boardUrl: Ref<string>
  sessionGeneration: Ref<number>
  transferBoardManager: TransferBoardManager
}

export function useBoardEditManagerAssignment({
  boardUrl,
  sessionGeneration,
  transferBoardManager,
}: UseBoardEditManagerAssignmentOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const { handleError } = useErrorHandler()

  const isManagerModalOpen = ref(false)
  const isTransferringManager = ref(false)
  const currentManagerLabel = ref('')
  let managerTransferRequestId = 0

  function openManagerModal() {
    isManagerModalOpen.value = true
  }

  function closeManagerModal() {
    isManagerModalOpen.value = false
  }

  function setCurrentManagerLabel(label: string) {
    currentManagerLabel.value = label
  }

  function resetManagerAssignmentState() {
    managerTransferRequestId += 1
    currentManagerLabel.value = ''
    isManagerModalOpen.value = false
    isTransferringManager.value = false
  }

  async function confirmManagerSelection(users: ManagerCandidate[]) {
    if (users.length === 0) return

    const selectedUser = users[0]
    const transferBoardUrl = boardUrl.value
    const transferSessionGeneration = sessionGeneration.value
    const requestId = ++managerTransferRequestId
    const isCurrentRequest = () => requestId === managerTransferRequestId
      && transferBoardUrl === boardUrl.value
      && transferSessionGeneration === sessionGeneration.value

    isTransferringManager.value = true
    try {
      const updatedBoard = await transferBoardManager({
        boardUrl: transferBoardUrl,
        loginId: selectedUser.loginId
      })
      if (!isCurrentRequest()) return
      currentManagerLabel.value = updatedBoard.adminDisplayName || `${selectedUser.displayName} (${selectedUser.loginId})`
      closeManagerModal()
      toastStore.addToast(t('common.messages.saveSuccess'), 'success')
    } catch (err: unknown) {
      if (!isCurrentRequest()) return
      handleError(err, t('common.messages.saveFailed'))
    } finally {
      if (isCurrentRequest()) {
        isTransferringManager.value = false
      }
    }
  }

  onUnmounted(() => {
    managerTransferRequestId += 1
  })

  return {
    closeManagerModal,
    confirmManagerSelection,
    currentManagerLabel,
    isManagerModalOpen,
    isTransferringManager,
    openManagerModal,
    resetManagerAssignmentState,
    setCurrentManagerLabel,
  }
}
