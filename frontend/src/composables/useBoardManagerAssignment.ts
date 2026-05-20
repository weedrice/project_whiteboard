import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToastStore } from '@/stores/toast'
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

  const isAssigningManager = ref(false)
  const isManagerModalOpen = ref(false)
  const managerSelectionMode = ref<'single' | 'multiple'>('single')

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
    managerSelectionMode.value = mode
    isManagerModalOpen.value = true
  }

  function closeManagerModal() {
    isManagerModalOpen.value = false
  }

  async function confirmManagerSelection(users: Array<{ loginId: string; displayName?: string }>) {
    if (!selectedBoard.value || users.length === 0) return

    isAssigningManager.value = true
    try {
      const selectedUser = users[0]
      await updateBoardManager({
        boardId: selectedBoard.value.boardId,
        data: { loginId: selectedUser.loginId }
      })

      selectedBoard.value.adminDisplayName = selectedUser.displayName
      closeManagerModal()
      toastStore.addToast(t('admin.admins.messages.added'), 'success')
    } catch {
      // Error handled globally
    } finally {
      isAssigningManager.value = false
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
