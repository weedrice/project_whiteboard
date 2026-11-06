import { computed, ref, watch, type Ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { useUserBlockAction } from '@/features/user/menu/useUserBlockAction'
import { invalidateBlockVisibilityCaches } from '@/features/user/blockVisibilityCache'
import { isSessionGenerationCurrent } from '@/queryAuthScope'

interface UseUserMenuActionsOptions {
    userId: Ref<number>
    displayName: Ref<string>
    withdrawn?: Ref<boolean>
    closeDropdown: () => void
    openProfile?: () => void
    t: (key: string, named?: Record<string, unknown>) => string
}

export function useUserMenuActions({
    userId,
    displayName,
    withdrawn = ref(false),
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
    const isMenuDisabled = computed(() => !userId.value || (withdrawn.value && (!authStore.user || isSelf.value)))

    const handleOpenProfile = () => {
        closeDropdown()
        if (!withdrawn.value) openProfile?.()
    }

    const openMessageModal = () => {
        closeDropdown()
        if (isSelf.value || withdrawn.value) return
        isMessageModalOpen.value = true
    }

    const closeMessageModal = () => {
        isMessageModalOpen.value = false
    }

    const openReportModal = () => {
        closeDropdown()
        if (isSelf.value || withdrawn.value) return
        isReportModalOpen.value = true
    }

    const closeReportModal = () => {
        isReportModalOpen.value = false
    }

    watch([userId, displayName, withdrawn], () => {
        closeMessageModal()
        closeReportModal()
    }, { flush: 'sync' })

    const handleBlockUser = async () => {
        closeDropdown()
        if (isSelf.value) return
        const targetUserId = userId.value
        const targetDisplayName = displayName.value
        const sessionGeneration = authStore.sessionGeneration
        const isTargetIntentCurrent = () => (
            isSessionGenerationCurrent(authStore, sessionGeneration)
            && userId.value === targetUserId
            && displayName.value === targetDisplayName
        )

        await runUserBlockAction({
            confirmMessage: t('user.block.confirm', { name: targetDisplayName }),
            failureMessage: t('user.block.failed'),
            logMessage: 'Failed to block user:',
            successMessage: t('user.block.success', { name: targetDisplayName }),
            action: () => userApi.blockUser(targetUserId),
            isSuccess: ({ data }) => data.success,
            isIntentCurrent: isTargetIntentCurrent,
            onSuccess: () => {
                if (!isTargetIntentCurrent()) return
                void invalidateBlockVisibilityCaches(queryClient, sessionGeneration, targetUserId)
            },
        })
    }

    const menuItems = computed(() => {
        if (withdrawn.value) {
            return authStore.user && !isSelf.value
                ? [{ action: handleBlockUser, label: t('user.menu.block') }]
                : []
        }
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
