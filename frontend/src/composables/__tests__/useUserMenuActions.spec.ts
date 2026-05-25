import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useUserMenuActions } from '../useUserMenuActions'
import { userApi } from '@/api/user'

const authState = vi.hoisted(() => ({
    user: { userId: 1 } as { userId: number } | null,
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
        confirm.mockResolvedValue(true)
    })

    it('disables menu actions for the current user', () => {
        const closeDropdown = vi.fn()
        const actions = useUserMenuActions({
            userId: ref(1),
            displayName: ref('Self'),
            closeDropdown,
            t,
        })

        expect(actions.isSelf.value).toBe(true)
        expect(actions.isMenuDisabled.value).toBe(true)
        expect(actions.menuItems.value).toEqual([])
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
        vi.mocked(userApi.blockUser).mockResolvedValueOnce({
            data: { success: true },
        } as never)
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
        expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments'] })
    })

    it('logs and toasts block failures', async () => {
        const error = new Error('fail')
        vi.mocked(userApi.blockUser).mockRejectedValueOnce(error as never)
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
