import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useUser } from '../useUser'
import { ref } from 'vue'

// Mock vue-query
vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: vi.fn(() => ({
        invalidateQueries: vi.fn(),
        clear: vi.fn()
    })),
    useQuery: vi.fn(({ queryFn }) => {
        const data = queryFn()
        return { data: ref(data) }
    }),
    useMutation: vi.fn(({ mutationFn, onSuccess }) => {
        return {
            mutate: async (variables: any) => {
                const result = await mutationFn(variables)
                if (onSuccess) onSuccess(result, variables, undefined)
                return result
            }
        }
    })
}))

// Mock userApi
vi.mock('@/api/user', () => ({
    userApi: {
        getMyProfile: vi.fn().mockResolvedValue({ data: { data: { userId: 1, nickname: 'TestUser' } } }),
        getUserProfile: vi.fn().mockResolvedValue({ data: { data: { userId: 2, nickname: 'OtherUser' } } }),
        getUserSettings: vi.fn().mockResolvedValue({ data: { data: {} } }),
        getBlockList: vi.fn().mockResolvedValue({ data: { data: [] } }),
        getNotificationSettings: vi.fn().mockResolvedValue({ data: { data: {} } }),
        updateMyProfile: vi.fn().mockResolvedValue({ data: { success: true } }),
        updatePassword: vi.fn().mockResolvedValue({ data: { success: true } }),
        deleteAccount: vi.fn().mockResolvedValue({ data: { success: true } }),
        updateUserSettings: vi.fn().mockResolvedValue({ data: { success: true } }),
        updateNotificationSettings: vi.fn().mockResolvedValue({ data: { success: true } }),
        blockUser: vi.fn().mockResolvedValue({ data: { success: true } }),
        unblockUser: vi.fn().mockResolvedValue({ data: { success: true } }),
        getRecentlyViewedPosts: vi.fn().mockResolvedValue({ data: { data: [] } })
    }
}))

describe('useUser', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('fetches my profile', async () => {
        const { useMyProfile } = useUser()
        const { data } = useMyProfile()
        expect(data).toBeDefined()
    })

    it('updates my profile', async () => {
        const { useUpdateMyProfile } = useUser()
        const mutation = useUpdateMyProfile()
        const result = await mutation.mutate({ displayName: 'NewName' })
        expect(result).toEqual({ success: true })
    })
})
