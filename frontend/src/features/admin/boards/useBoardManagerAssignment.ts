import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
import { useAuthStore } from '@/stores/auth'
import type { AdminBoard, BoardAdminInfo } from '@/types'

type UpdateBoardManagerPayload = {
  boardId: number
  data: { loginId: string }
}

interface UseBoardManagerAssignmentOptions {
  selectedBoard: ComputedRef<AdminBoard | null>
  boardManagerData: Ref<BoardAdminInfo | null | undefined>
  updateBoardManager: (payload: UpdateBoardManagerPayload) => Promise<unknown>
}

export function useBoardManagerAssignment({
  selectedBoard,
  boardManagerData,
  updateBoardManager
}: UseBoardManagerAssignmentOptions) {
  const { t } = useI18n()
  const toastStore = useToastStore()
  const authStore = useAuthStore()

  const isAssigningManager = ref(false)
  const isManagerModalOpen = ref(false)
  const managerSelectionMode = ref<'single' | 'multiple'>('single')
  let modalRevision = 0
  let modalBoardId: number | null = null

  const currentManagerLabel = computed(() => {
    const manager = boardManagerData.value

    if (manager?.user?.displayName || manager?.user?.loginId) {
      return `${manager.user.displayName || '-'} (${manager.user.loginId || '-'})`
    }

    if (selectedBoard.value?.adminDisplayName) {
      return selectedBoard.value.adminDisplayName
    }

    return t('common.noData')
  })

  function openManagerModal(mode: 'single' | 'multiple' = 'single') {
    const boardId = selectedBoard.value?.boardId
    if (!boardId) return
    modalRevision += 1
    modalBoardId = boardId
    isAssigningManager.value = false
    managerSelectionMode.value = mode
    isManagerModalOpen.value = true
  }

  function closeManagerModal() {
    modalRevision += 1
    modalBoardId = null
    isAssigningManager.value = false
    isManagerModalOpen.value = false
  }

  async function confirmManagerSelection(users: Array<{ loginId: string; displayName?: string }>) {
    const targetBoard = selectedBoard.value
    if (
      !targetBoard
      || users.length === 0
      || isAssigningManager.value
      || !isManagerModalOpen.value
      || modalBoardId !== targetBoard.boardId
    ) return

    const submittedRevision = modalRevision
    const submittedSessionGeneration = authStore.sessionGeneration
    isAssigningManager.value = true
    try {
      const selectedUser = users[0]
      await updateBoardManager({
        boardId: targetBoard.boardId,
        data: { loginId: selectedUser.loginId }
      })

      const isCurrentIntent = submittedRevision === modalRevision
        && submittedSessionGeneration === authStore.sessionGeneration
        && isManagerModalOpen.value
        && modalBoardId === targetBoard.boardId
        && selectedBoard.value?.boardId === targetBoard.boardId
      if (!isCurrentIntent) return

      targetBoard.adminDisplayName = selectedUser.displayName
      closeManagerModal()
      toastStore.addToast(t('admin.admins.messages.added'), 'success')
    } catch {
      // Error handled globally
    } finally {
      if (submittedRevision === modalRevision) isAssigningManager.value = false
    }
  }

  return {
    isAssigningManager,
    isManagerModalOpen,
    managerSelectionMode,
    currentManagerLabel,
    openManagerModal,
    closeManagerModal,
    confirmManagerSelection
  }
}
