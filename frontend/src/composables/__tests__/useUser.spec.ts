import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import { useUser } from '../useUser'
import { userApi } from '@/api/user'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { apiDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'

const mocks = vi.hoisted(() => {
    const invalidateQueries = vi.fn()
    const clear = vi.fn()
    const queryOptions: Array<Record<string, unknown>> = []
    return {
        invalidateQueries,
        clear,
        queryOptions,
    }
})

vi.mock('@tanstack/vue-query', () => ({
    QueryCache: class QueryCache {
        constructor(_options?: unknown) {}
    },
    MutationCache: class MutationCache {
        constructor(_options?: unknown) {}
    },
    QueryClient: class QueryClient {
        constructor(_options?: unknown) {}
    },
    useQueryClient: () => ({
        invalidateQueries: mocks.invalidateQueries,
        clear: mocks.clear,
    }),
    useQuery: vi.fn((options: Record<string, unknown>) => {
        mocks.queryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: async () => {
                if (options.queryFn) {
                    return await (options.queryFn as () => Promise<unknown>)()
                }
                return null
            },
        }
    }),
    useMutation: vi.fn(({ mutationFn, onSuccess }) => ({
        mutateAsync: async (variables: unknown) => {
            const result = await mutationFn(variables)
            if (onSuccess) {
                onSuccess(result, variables, undefined)
            }
            return result
        },
    })),
}))

vi.mock('@/api/user', () => ({
    userApi: {
        getMyProfile: vi.fn(),
        getUserProfile: vi.fn(),
        getUserSettings: vi.fn(),
        getBlockList: vi.fn(),
        getNotificationSettings: vi.fn(),
        getMyAgents: vi.fn(),
        getMyPoint: vi.fn(),
        getMyScraps: vi.fn(),
        getMyPointHistories: vi.fn(),
        updateMyProfile: vi.fn(),
        updatePassword: vi.fn(),
        deleteAccount: vi.fn(),
        updateUserSettings: vi.fn(),
        updateNotificationSettingsBulk: vi.fn(),
        claimAgent: vi.fn(),
        suspendMyAgent: vi.fn(),
        activateMyAgent: vi.fn(),
        deleteMyAgent: vi.fn(),
        blockUser: vi.fn(),
        unblockUser: vi.fn(),
        getRecentlyViewedPosts: vi.fn(),
    },
}))

describe('useUser', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mocks.queryOptions.length = 0
    })

    it('fetches my profile with medium staleTime', async () => {
        vi.mocked(userApi.getMyProfile).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getMyProfile>({ userId: 1, displayName: 'me' })
        )

        const { useMyProfile } = useUser()
        useMyProfile()
        const options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['user', 'me'])
        expect(options.staleTime).toBe(QUERY_STALE_TIME.MEDIUM)

        const controller = new AbortController()
        const result = await (options.queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({
            signal: controller.signal,
        })
        expect(result).toEqual({ userId: 1, displayName: 'me' })
        expect(userApi.getMyProfile).toHaveBeenCalledWith({ signal: controller.signal })
    })

    it('fetches user profile and supports enabled guard', async () => {
        vi.mocked(userApi.getUserProfile).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getUserProfile>({ userId: 9 })
        )

        const { useUserProfile } = useUser()
        const userId = ref(9)
        useUserProfile(userId)
        let options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['user', userId])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)

        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toEqual({ userId: 9 })
        expect(userApi.getUserProfile).toHaveBeenCalledWith(9)

        useUserProfile(ref(''))
        options = mocks.queryOptions.at(-1)!
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(false)
    })

    it('fetches settings, blocks and notification settings queries', async () => {
        vi.mocked(userApi.getUserSettings).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getUserSettings>({ theme: 'LIGHT' })
        )
        vi.mocked(userApi.getBlockList).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getBlockList>({
                content: [{ userId: 100, displayName: 'blocked', secondaryText: 'blocked-login' }],
                totalElements: 1,
                totalPages: 1,
                size: 20,
                number: 0,
                first: true,
                last: true,
                empty: false,
            })
        )
        vi.mocked(userApi.getNotificationSettings).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getNotificationSettings>([
                { notificationType: 'COMMENT', isEnabled: true },
            ])
        )

        vi.mocked(userApi.getMyAgents).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getMyAgents>({ agents: [{ agentId: 10 }] })
        )

        const { useUserSettings, useBlockList, useNotificationSettings, useMyAgents } = useUser()

        useUserSettings()
        let options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['user', 'settings'])
        expect(options.staleTime).toBe(QUERY_STALE_TIME.MEDIUM)
        let result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toEqual({ theme: 'LIGHT' })

        useBlockList()
        options = mocks.queryOptions.at(-1)!
        expect((options.queryKey as { value: unknown[] }).value).toEqual(['user', 'blocks', {}])
        result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toEqual({
            content: [{ userId: 100, displayName: 'blocked', secondaryText: 'blocked-login' }],
            totalElements: 1,
            totalPages: 1,
            size: 20,
            number: 0,
            first: true,
            last: true,
            empty: false,
        })

        useNotificationSettings()
        options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['user', 'notification-settings'])
        result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toEqual([{ notificationType: 'COMMENT', isEnabled: true }])

        useMyAgents()
        options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['user', 'agents'])
        expect(options.staleTime).toBe(QUERY_STALE_TIME.MEDIUM)
        const controller = new AbortController()
        result = await (options.queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({
            signal: controller.signal,
        })
        expect(result).toEqual({ agents: [{ agentId: 10 }] })
        expect(userApi.getMyAgents).toHaveBeenCalledWith({ signal: controller.signal })
    })

    it('fetches recently viewed posts with optional params', async () => {
        vi.mocked(userApi.getRecentlyViewedPosts)
            .mockResolvedValueOnce(apiDataResponse<typeof userApi.getRecentlyViewedPosts>({ content: [{ postId: 1 }] }))
            .mockResolvedValueOnce(apiDataResponse<typeof userApi.getRecentlyViewedPosts>({ content: [{ postId: 2 }] }))

        const { useRecentlyViewedPosts } = useUser()

        useRecentlyViewedPosts()
        let options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['user', 'history', 'views', undefined])
        let result = await (options.queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({
            signal: new AbortController().signal,
        })
        expect(userApi.getRecentlyViewedPosts).toHaveBeenNthCalledWith(1, {}, expect.objectContaining({ signal: expect.any(AbortSignal) }))
        expect(result).toEqual({ content: [{ postId: 1 }] })

        const params = ref({ page: 1, size: 20 })
        useRecentlyViewedPosts(params)
        options = mocks.queryOptions.at(-1)!
        expect(options.queryKey).toEqual(['user', 'history', 'views', params])
        result = await (options.queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({
            signal: new AbortController().signal,
        })
        expect(userApi.getRecentlyViewedPosts).toHaveBeenNthCalledWith(2, { page: 1, size: 20 }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
        expect(result).toEqual({ content: [{ postId: 2 }] })
    })

    it('fetches my points with user-scoped query key and enabled guard', async () => {
        vi.mocked(userApi.getMyPoint).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getMyPoint>({ currentPoint: 12345 })
        )

        const { useMyPoint } = useUser()
        const enabled = ref(false)
        const userIdentity = ref(7)
        useMyPoint(enabled, userIdentity)
        const options = mocks.queryOptions.at(-1)!

        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual(['user', 'points', 'me', 7])
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(false)
        expect(options.staleTime).toBe(QUERY_STALE_TIME.SHORT)

        enabled.value = true
        expect((options.enabled as ReturnType<typeof computed>).value).toBe(true)

        const result = await (options.queryFn as () => Promise<unknown>)()
        expect(result).toEqual({ currentPoint: 12345 })
        expect(userApi.getMyPoint).toHaveBeenCalled()
    })

    it('fetches my scraps and point history with paginated query state', async () => {
        vi.mocked(userApi.getMyScraps).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getMyScraps>({ content: [{ postId: 1 }], totalPages: 1 })
        )
        vi.mocked(userApi.getMyPointHistories).mockResolvedValueOnce(
            apiDataResponse<typeof userApi.getMyPointHistories>({ content: [{ historyId: 2 }], totalPages: 1 })
        )

        const { useMyScraps, useMyPointHistories } = useUser()
        const params = ref({ page: 1, size: 15 })

        useMyScraps(params)
        let options = mocks.queryOptions.at(-1)!
        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual(['user', 'scraps', { page: 1, size: 15 }])
        let result = await (options.queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({
            signal: new AbortController().signal,
        })
        expect(result).toEqual({ content: [{ postId: 1 }], totalPages: 1 })
        expect(userApi.getMyScraps).toHaveBeenCalledWith({ page: 1, size: 15 }, expect.objectContaining({ signal: expect.any(AbortSignal) }))

        useMyPointHistories(params)
        options = mocks.queryOptions.at(-1)!
        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual(['user', 'points', 'history', { page: 1, size: 15 }])
        result = await (options.queryFn as (context: { signal: AbortSignal }) => Promise<unknown>)({
            signal: new AbortController().signal,
        })
        expect(result).toEqual({ content: [{ historyId: 2 }], totalPages: 1 })
        expect(userApi.getMyPointHistories).toHaveBeenCalledWith({ page: 1, size: 15 }, expect.objectContaining({ signal: expect.any(AbortSignal) }))
    })

    it('updates profile and invalidates my profile cache', async () => {
        vi.mocked(userApi.updateMyProfile).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.updateMyProfile>())

        const { useUpdateMyProfile } = useUser()
        const mutation = useUpdateMyProfile()
        await mutation.mutateAsync({ displayName: 'new-name' })

        expect(userApi.updateMyProfile).toHaveBeenCalledWith({ displayName: 'new-name' })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'me'] })
    })

    it('updates password without cache invalidation side effects', async () => {
        vi.mocked(userApi.updatePassword).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.updatePassword>())

        const { useUpdatePassword } = useUser()
        await useUpdatePassword().mutateAsync({ currentPassword: 'old', newPassword: 'new' })

        expect(userApi.updatePassword).toHaveBeenCalledWith('old', 'new')
    })

    it('deletes account and clears query client cache', async () => {
        vi.mocked(userApi.deleteAccount).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.deleteAccount>())

        const { useDeleteAccount } = useUser()
        await useDeleteAccount().mutateAsync('password')

        expect(userApi.deleteAccount).toHaveBeenCalledWith('password')
        expect(mocks.clear).toHaveBeenCalled()
    })

    it('updates user settings and invalidates settings query', async () => {
        vi.mocked(userApi.updateUserSettings).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.updateUserSettings>())

        const { useUpdateUserSettings } = useUser()
        await useUpdateUserSettings().mutateAsync({ theme: 'DARK' })

        expect(userApi.updateUserSettings).toHaveBeenCalledWith({ theme: 'DARK' })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'settings'] })
    })

    it('updates notification settings and invalidates notification settings query', async () => {
        vi.mocked(userApi.updateNotificationSettingsBulk).mockResolvedValueOnce(
            apiSuccessResponse<typeof userApi.updateNotificationSettingsBulk>()
        )

        const { useUpdateNotificationSettings } = useUser()
        await useUpdateNotificationSettings().mutateAsync({
            settings: [{ notificationType: 'COMMENT', isEnabled: true }],
        })

        expect(userApi.updateNotificationSettingsBulk).toHaveBeenCalledWith({
            settings: [{ notificationType: 'COMMENT', isEnabled: true }],
        })
        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'notification-settings'] })
    })

    it('claims, suspends, activates and deletes agents and invalidates agent list', async () => {
        vi.mocked(userApi.claimAgent).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.claimAgent>())
        vi.mocked(userApi.suspendMyAgent).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.suspendMyAgent>())
        vi.mocked(userApi.activateMyAgent).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.activateMyAgent>())
        vi.mocked(userApi.deleteMyAgent).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.deleteMyAgent>())

        const { useClaimAgent, useSuspendMyAgent, useActivateMyAgent, useDeleteMyAgent } = useUser()

        await useClaimAgent().mutateAsync('noviis_agt_xxx')
        expect(userApi.claimAgent).toHaveBeenCalledWith('noviis_agt_xxx')

        await useSuspendMyAgent().mutateAsync(7)
        expect(userApi.suspendMyAgent).toHaveBeenCalledWith(7)

        await useActivateMyAgent().mutateAsync(7)
        expect(userApi.activateMyAgent).toHaveBeenCalledWith(7)

        await useDeleteMyAgent().mutateAsync(7)
        expect(userApi.deleteMyAgent).toHaveBeenCalledWith(7)

        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'agents'] })
    })

    it('blocks and unblocks user and invalidates block list', async () => {
        vi.mocked(userApi.blockUser).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.blockUser>())
        vi.mocked(userApi.unblockUser).mockResolvedValueOnce(apiSuccessResponse<typeof userApi.unblockUser>())

        const { useBlockUser, useUnblockUser } = useUser()

        await useBlockUser().mutateAsync(123)
        expect(userApi.blockUser).toHaveBeenCalledWith(123)

        await useUnblockUser().mutateAsync(123)
        expect(userApi.unblockUser).toHaveBeenCalledWith(123)

        expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ['user', 'blocks'] })
    })
})
