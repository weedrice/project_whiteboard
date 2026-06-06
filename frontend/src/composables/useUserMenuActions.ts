import { computed, ref, type Ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import { commentQueryKeys } from '@/composables/commentQueryKeys'
import { useConfirm } from '@/composables/useConfirm'
import logger from '@/utils/logger'

interface UseUserMenuActionsOptions {
    userId: Ref<number>
    displayName: Ref<string>
    closeDropdown: () => void
    t: (key: string, named?: Record<string, unknown>) => string
}

export function useUserMenuActions({
    userId,
    displayName,
    closeDropdown,
    t,
}: UseUserMenuActionsOptions) {
    const authStore = useAuthStore()
    const toastStore = useToastStore()
    const { confirm } = useConfirm()
    const queryClient = useQueryClient()
    const isMessageModalOpen = ref(false)
    const isReportModalOpen = ref(false)
    const isSelf = computed(() => !!(authStore.user && authStore.user.userId === userId.value))
    const isMenuDisabled = computed(() => !authStore.user || isSelf.value)

    const openMessageModal = () => {
        closeDropdown()
        if (isSelf.value) return
        isMessageModalOpen.value = true
    }

    const closeMessageModal = () => {
        isMessageModalOpen.value = false
    }

    const openReportModal = () => {
        closeDropdown()
        if (isSelf.value) return
        isReportModalOpen.value = true
    }

    const closeReportModal = () => {
        isReportModalOpen.value = false
    }

    const handleBlockUser = async () => {
        closeDropdown()
        if (isSelf.value) return

        const isConfirmed = await confirm(t('user.block.confirm', { name: displayName.value }))
        if (!isConfirmed) return

        try {
            const { data } = await userApi.blockUser(userId.value)
            if (data.success) {
                toastStore.addToast(t('user.block.success', { name: displayName.value }), 'success')
                queryClient.invalidateQueries({ queryKey: commentQueryKeys.all })
            }
        } catch (error) {
            logger.error('Failed to block user:', error)
            toastStore.addToast(t('user.block.failed'), 'error')
        }
    }

    const menuItems = computed(() => {
        if (isSelf.value) return []

        return [
            { action: openMessageModal, label: t('user.menu.sendMessage') },
            { action: openReportModal, label: t('user.menu.report') },
            { action: handleBlockUser, label: t('user.menu.block') },
        ]
    })

    return {
        isMessageModalOpen,
        isReportModalOpen,
        isSelf,
        isMenuDisabled,
        menuItems,
        closeMessageModal,
        closeReportModal,
    }
}
