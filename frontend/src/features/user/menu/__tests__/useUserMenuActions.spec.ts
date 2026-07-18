import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useUserMenuActions } from '../useUserMenuActions'
import { userApi } from '@/api/user'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'
import { createDeferred } from '@/test/async'

const authState = vi.hoisted(() => ({
    user: { userId: 1 } as { userId: number } | null,
    sessionGeneration: 0,
}))
const addToast = vi.hoisted(() => vi.fn())
const confirm = vi.hoisted(() => vi.fn())
const invalidateQueries = vi.hoisted(() => vi.fn())
const loggerError = vi.hoisted(() => vi.fn())

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => authState,
}))

vi.mock('@/stores/toast', () => ({
    useToastStore: () => ({ addToast }),
}))

vi.mock('@/composables/useConfirm', () => ({
    useConfirm: () => ({ confirm }),
}))

vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: () => ({ invalidateQueries }),
}))

vi.mock('@/api/user', () => ({
    userApi: {
        blockUser: vi.fn(),
    },
}))

vi.mock('@/utils/logger', () => ({
    default: {
        error: loggerError,
    },
}))

const t = (key: string, named?: Record<string, unknown>) => {
    return named?.name ? `${key}:${named.name}` : key
}

describe('useUserMenuActions', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        authState.user = { userId: 1 }
        authState.sessionGeneration = 0
        confirm.mockResolvedValue(true)
    })

    it('keeps profile access for the current user without private actions', () => {
        const closeDropdown = vi.fn()
        const openProfile = vi.fn()
        const actions = useUserMenuActions({
            userId: ref(1),
            displayName: ref('Self'),
            closeDropdown,
            openProfile,
            t,
        })

        expect(actions.isSelf.value).toBe(true)
        expect(actions.isMenuDisabled.value).toBe(false)
        expect(actions.menuItems.value).toHaveLength(1)
        expect(actions.menuItems.value[0].label).toBe('user.menu.viewProfile')

        actions.menuItems.value[0].action()
        expect(closeDropdown).toHaveBeenCalledTimes(1)
        expect(openProfile).toHaveBeenCalledTimes(1)
    })

    it('opens and closes message and report modals through menu actions', () => {
        const closeDropdown = vi.fn()
        const actions = useUserMenuActions({
            userId: ref(2),
            displayName: ref('Other'),
            closeDropdown,
            t,
        })

        actions.menuItems.value[0].action()
        expect(closeDropdown).toHaveBeenCalledTimes(1)
        expect(actions.isMessageModalOpen.value).toBe(true)
        actions.closeMessageModal()
        expect(actions.isMessageModalOpen.value).toBe(false)

        actions.menuItems.value[1].action()
        expect(actions.isReportModalOpen.value).toBe(true)
        actions.closeReportModal()
        expect(actions.isReportModalOpen.value).toBe(false)
    })

    it('preserves block success side effects for comments and toast', async () => {
        vi.mocked(userApi.blockUser).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.blockUser>())
        const actions = useUserMenuActions({
            userId: ref(2),
            displayName: ref('Other'),
            closeDropdown: vi.fn(),
            t,
        })

        await actions.menuItems.value[2].action()

        expect(confirm).toHaveBeenCalledWith('user.block.confirm:Other')
        expect(userApi.blockUser).toHaveBeenCalledWith(2)
        expect(addToast).toHaveBeenCalledWith('user.block.success:Other', 'success')
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', 'blocks'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', '2'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', '2', 'posts'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', '2', 'comments'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'post'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'posts'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'home'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'search'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'feed'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'messages'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'messages', 'unread-count'] })
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'tags'] })
        expect(invalidateQueries).toHaveBeenCalledTimes(14)
    })

    it('does not block a replacement target after a delayed confirmation', async () => {
        const confirmResult = createDeferred<boolean>()
        confirm.mockReturnValueOnce(confirmResult.promise)
        const targetUserId = ref(2)
        const targetDisplayName = ref('Original')
        const actions = useUserMenuActions({
            userId: targetUserId,
            displayName: targetDisplayName,
            closeDropdown: vi.fn(),
            t,
        })

        const pending = actions.menuItems.value[2].action()
        targetUserId.value = 3
        targetDisplayName.value = 'Replacement'
        confirmResult.resolve(true)
        await pending

        expect(confirm).toHaveBeenCalledWith('user.block.confirm:Original')
        expect(userApi.blockUser).not.toHaveBeenCalled()
        expect(invalidateQueries).not.toHaveBeenCalled()
    })

    it('ignores a delayed block response after the target changes', async () => {
        const blockResult = createDeferred<Awaited<ReturnType<typeof userApi.blockUser>>>()
        vi.mocked(userApi.blockUser).mockReturnValueOnce(blockResult.promise)
        const targetUserId = ref(2)
        const targetDisplayName = ref('Original')
        const actions = useUserMenuActions({
            userId: targetUserId,
            displayName: targetDisplayName,
            closeDropdown: vi.fn(),
            t,
        })

        const pending = actions.menuItems.value[2].action()
        await vi.waitFor(() => expect(userApi.blockUser).toHaveBeenCalledWith(2))
        targetUserId.value = 3
        targetDisplayName.value = 'Replacement'
        blockResult.resolve(apiSuccessResponse<typeof userApi.blockUser>())
        await pending

        expect(addToast).not.toHaveBeenCalledWith('user.block.success:Original', 'success')
        expect(invalidateQueries).not.toHaveBeenCalled()
    })

    it('logs and toasts block failures', async () => {
        const error = new Error('fail')
        vi.mocked(userApi.blockUser).mockRejectedValueOnce(error)
        const actions = useUserMenuActions({
            userId: ref(2),
            displayName: ref('Other'),
            closeDropdown: vi.fn(),
            t,
        })

        await actions.menuItems.value[2].action()

        expect(loggerError).toHaveBeenCalledWith('Failed to block user:', error)
        expect(addToast).toHaveBeenCalledWith('user.block.failed', 'error')
    })
})
