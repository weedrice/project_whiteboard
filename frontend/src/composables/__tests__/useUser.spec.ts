import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useUser } from '../useUser'
import { ref } from 'vue'
import { userApi } from '@/api/user'

// Mock vue-query
const mockInvalidateQueries = vi.fn()
const mockClear = vi.fn()
vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries,
        clear: mockClear
    })),
    useQuery: vi.fn(({ queryFn }) => {
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn()
        }
    }),
    useMutation: vi.fn(({ mutationFn, onSuccess }) => {
        return {
            mutate: async (variables: unknown) => {
                const result = await mutationFn(variables)
                if (onSuccess) onSuccess(result, variables, undefined)
                return result
            },
            mutateAsync: async (variables: unknown) => {
                const result = await mutationFn(variables)
                if (onSuccess) onSuccess(result, variables, undefined)
                return result
            },
            isLoading: ref(false),
            error: ref(null)
        }
    })
}))

// Mock userApi
vi.mock('@/api/user', () => ({
    userApi: {
        getMyProfile: vi.fn(),
        getUserProfile: vi.fn(),
        getUserSettings: vi.fn(),
        getBlockList: vi.fn(),
        getNotificationSettings: vi.fn(),
        updateMyProfile: vi.fn(),
        updatePassword: vi.fn(),
        deleteAccount: vi.fn(),
        updateUserSettings: vi.fn(),
        updateNotificationSettings: vi.fn(),
        blockUser: vi.fn(),
        unblockUser: vi.fn(),
        getRecentlyViewedPosts: vi.fn()
    }
}))

describe('useUser', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    describe('Queries', () => {
        it('useMyProfile returns query hooks', () => {
            const { useMyProfile } = useUser()
            const result = useMyProfile()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useUserProfile returns query hooks', () => {
            const { useUserProfile } = useUser()
            const userId = ref(1)
            const result = useUserProfile(userId)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useUserSettings returns query hooks', () => {
            const { useUserSettings } = useUser()
            const result = useUserSettings()

            expect(result).toHaveProperty('data')
        })

        it('useBlockList returns query hooks', () => {
            const { useBlockList } = useUser()
            const result = useBlockList()

            expect(result).toHaveProperty('data')
        })

        it('useNotificationSettings returns query hooks', () => {
            const { useNotificationSettings } = useUser()
            const result = useNotificationSettings()

            expect(result).toHaveProperty('data')
        })

        it('useRecentlyViewedPosts returns query hooks', () => {
            const { useRecentlyViewedPosts } = useUser()
            const params = ref({ page: 0, size: 10 })
            const result = useRecentlyViewedPosts(params)

            expect(result).toHaveProperty('data')
        })
    })

    describe('Mutations', () => {
        it('useUpdateMyProfile calls API and invalidates cache', async () => {
            vi.mocked(userApi.updateMyProfile).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useUpdateMyProfile } = useUser()
            const mutation = useUpdateMyProfile()

            await mutation.mutateAsync({ displayName: 'NewName' })

            expect(userApi.updateMyProfile).toHaveBeenCalledWith({ displayName: 'NewName' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'me'] })
        })

        it('useUpdatePassword calls API', async () => {
            vi.mocked(userApi.updatePassword).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useUpdatePassword } = useUser()
            const mutation = useUpdatePassword()

            await mutation.mutateAsync({ currentPassword: 'old', newPassword: 'new' })

            expect(userApi.updatePassword).toHaveBeenCalledWith('old', 'new')
        })

        it('useDeleteAccount calls API and clears cache', async () => {
            vi.mocked(userApi.deleteAccount).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useDeleteAccount } = useUser()
            const mutation = useDeleteAccount()

            await mutation.mutateAsync('password')

            expect(userApi.deleteAccount).toHaveBeenCalledWith('password')
            expect(mockClear).toHaveBeenCalled()
        })

        it('useUpdateUserSettings calls API and invalidates cache', async () => {
            vi.mocked(userApi.updateUserSettings).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useUpdateUserSettings } = useUser()
            const mutation = useUpdateUserSettings()

            await mutation.mutateAsync({ theme: 'DARK' })

            expect(userApi.updateUserSettings).toHaveBeenCalledWith({ theme: 'DARK' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'settings'] })
        })

        it('useUpdateNotificationSettings calls API and invalidates cache', async () => {
            vi.mocked(userApi.updateNotificationSettings).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useUpdateNotificationSettings } = useUser()
            const mutation = useUpdateNotificationSettings()

            await mutation.mutateAsync({ emailNotification: true })

            expect(userApi.updateNotificationSettings).toHaveBeenCalledWith({ emailNotification: true })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'notification-settings'] })
        })

        it('useBlockUser calls API and invalidates cache', async () => {
            vi.mocked(userApi.blockUser).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useBlockUser } = useUser()
            const mutation = useBlockUser()

            await mutation.mutateAsync(123)

            expect(userApi.blockUser).toHaveBeenCalledWith(123)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'blocks'] })
        })

        it('useUnblockUser calls API and invalidates cache', async () => {
            vi.mocked(userApi.unblockUser).mockResolvedValue({
                data: { success: true }
            } as any)

            const { useUnblockUser } = useUser()
            const mutation = useUnblockUser()

            await mutation.mutateAsync(123)

            expect(userApi.unblockUser).toHaveBeenCalledWith(123)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'blocks'] })
        })
    })
})

