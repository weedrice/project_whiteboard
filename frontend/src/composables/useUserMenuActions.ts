import { computed, ref, type Ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { commentQueryKeys } from '@/composables/commentQueryKeys'
import { useUserBlockAction } from '@/composables/useUserBlockAction'
import { userQueryKeys } from '@/composables/userQueryKeys'

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
    const queryClient = useQueryClient()
    const { runUserBlockAction } = useUserBlockAction()
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

        await runUserBlockAction({
            confirmMessage: t('user.block.confirm', { name: displayName.value }),
            failureMessage: t('user.block.failed'),
            logMessage: 'Failed to block user:',
            successMessage: t('user.block.success', { name: displayName.value }),
            action: () => userApi.blockUser(userId.value),
            isSuccess: ({ data }) => data.success,
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: commentQueryKeys.all })
                queryClient.invalidateQueries({ queryKey: userQueryKeys.blocksRoot })
            },
        })
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
