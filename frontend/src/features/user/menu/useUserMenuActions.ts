import { computed, ref, type Ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import { useUserBlockAction } from '@/features/user/menu/useUserBlockAction'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { isSessionGenerationCurrent, sessionQueryKey } from '@/queryAuthScope'

interface UseUserMenuActionsOptions {
    userId: Ref<number>
    displayName: Ref<string>
    closeDropdown: () => void
    openProfile?: () => void
    t: (key: string, named?: Record<string, unknown>) => string
}

export function useUserMenuActions({
    userId,
    displayName,
    closeDropdown,
    openProfile,
    t,
}: UseUserMenuActionsOptions) {
    const authStore = useAuthStore()
    const queryClient = useQueryClient()
    const { runUserBlockAction } = useUserBlockAction()
    const isMessageModalOpen = ref(false)
    const isReportModalOpen = ref(false)
    const isSelf = computed(() => !!(authStore.user && authStore.user.userId === userId.value))
    const isMenuDisabled = computed(() => !userId.value)

    const handleOpenProfile = () => {
        closeDropdown()
        openProfile?.()
    }

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
        const sessionGeneration = authStore.sessionGeneration

        await runUserBlockAction({
            confirmMessage: t('user.block.confirm', { name: displayName.value }),
            failureMessage: t('user.block.failed'),
            logMessage: 'Failed to block user:',
            successMessage: t('user.block.success', { name: displayName.value }),
            action: () => userApi.blockUser(userId.value),
            isSuccess: ({ data }) => data.success,
            isIntentCurrent: () => isSessionGenerationCurrent(authStore, sessionGeneration),
            onSuccess: () => {
                if (!isSessionGenerationCurrent(authStore, sessionGeneration)) return
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(sessionGeneration, commentQueryKeys.all),
                })
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(sessionGeneration, userQueryKeys.blocksRoot),
                })
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(sessionGeneration, userQueryKeys.profile(userId.value)),
                })
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(sessionGeneration, userQueryKeys.publicPostsRoot(userId.value)),
                })
                queryClient.invalidateQueries({
                    queryKey: sessionQueryKey(sessionGeneration, userQueryKeys.publicCommentsRoot(userId.value)),
                })
            },
        })
    }

    const menuItems = computed(() => {
        const profileItem = { action: handleOpenProfile, label: t('user.menu.viewProfile') }

        if (!authStore.user || isSelf.value) return [profileItem]

        return [
            { action: openMessageModal, label: t('user.menu.sendMessage') },
            { action: openReportModal, label: t('user.menu.report') },
            { action: handleBlockUser, label: t('user.menu.block') },
            profileItem,
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
