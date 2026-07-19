import { describe, it, expect, vi, beforeEach } from 'vitest'
import { isRef, ref } from 'vue'
import { useAdmin } from '../useAdmin'
import { adminApi } from '@/api/admin'
import { apiDataResponse, apiSuccessDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'

const mockAuthStore = vi.hoisted(() => ({ sessionGeneration: 0 }))
const mockInvalidateConfig = vi.hoisted(() => vi.fn())

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => mockAuthStore,
}))

vi.mock('@/stores/config', () => ({
    useConfigStore: () => ({ invalidateConfig: mockInvalidateConfig }),
}))

// Mock dependencies
vi.mock('@/api/admin', () => ({
    adminApi: {
        getAdmins: vi.fn(),
        createAdmin: vi.fn(),
        activateAdmin: vi.fn(),
        deactivateAdmin: vi.fn(),
        getSuperAdmin: vi.fn(),
        activeSuperAdmin: vi.fn(),
        deactivateSuperAdmin: vi.fn(),
        getUsers: vi.fn(),
        getUserDetail: vi.fn(),
        getUserPosts: vi.fn(),
        getUserComments: vi.fn(),
        getUserSubscriptions: vi.fn(),
        updateUserStatus: vi.fn(),
        sanctionUser: vi.fn(),
        getReports: vi.fn(),
        resolveReport: vi.fn(),
        getIpBlocks: vi.fn(),
        blockIp: vi.fn(),
        unblockIp: vi.fn(),
        getConfigs: vi.fn(),
        createConfig: vi.fn(),
        updateConfig: vi.fn(),
        deleteConfig: vi.fn(),
        getDashboardStats: vi.fn(),
        getBoards: vi.fn(),
        createBoard: vi.fn(),
        updateBoard: vi.fn(),
        reorderBoards: vi.fn(),
        deleteBoard: vi.fn(),
        getBoardManager: vi.fn(),
        updateBoardManager: vi.fn(),
        getErrorLogs: vi.fn(),
        getErrorLog: vi.fn(),
        resolveErrorLog: vi.fn(),
        getErrorLogStats: vi.fn()
    }
}))

// Mock vue-query
const mockInvalidateQueries = vi.fn()
const mockSetQueriesData = vi.fn()
const mockRemoveQueries = vi.fn()
const mockFetchQuery = vi.fn(async (options: { queryFn: (context?: { signal?: AbortSignal }) => Promise<unknown> }) => (
    options.queryFn({})
))
const mockQueryOptions: Array<Record<string, unknown>> = []
vi.mock('@tanstack/vue-query', () => ({
    keepPreviousData: (previousData: unknown) => previousData,
    useQuery: vi.fn((options) => {
        mockQueryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn()
        }
    }),
    useMutation: vi.fn((options) => {
        return {
            mutate: async (variables: unknown) => {
                const context = await options.onMutate?.(variables)
                try {
                    const result = await options.mutationFn(variables)
                    options.onSuccess?.(result, variables, context)
                    options.onSettled?.(result, null, variables, context)
                    return result
                } catch (error) {
                    options.onError?.(error, variables, context)
                    options.onSettled?.(undefined, error, variables, context)
                    throw error
                }
            },
            mutateAsync: async (variables: unknown) => {
                const context = await options.onMutate?.(variables)
                try {
                    const result = await options.mutationFn(variables)
                    options.onSuccess?.(result, variables, context)
                    options.onSettled?.(result, null, variables, context)
                    return result
                } catch (error) {
                    options.onError?.(error, variables, context)
                    options.onSettled?.(undefined, error, variables, context)
                    throw error
                }
            },
            isLoading: ref(false),
            error: ref(null)
        }
    }),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries,
        setQueriesData: mockSetQueriesData,
        removeQueries: mockRemoveQueries,
        fetchQuery: mockFetchQuery,
    }))
}))

describe('useAdmin', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockQueryOptions.length = 0
        mockAuthStore.sessionGeneration = 0
    })

    describe('Admin Management', () => {
        it('useAdmins returns query hooks', () => {
            const { useAdmins } = useAdmin()
            const result = useAdmins(ref({ page: 0, size: 20 }))

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('uses value snapshots in admin keys and recomputes when params change', () => {
            const { useAdmins } = useAdmin()
            const params = ref({ page: 0, size: 20 })

            useAdmins(params)
            const queryKey = mockQueryOptions.at(-1)?.queryKey as { value: readonly unknown[] }
            const initialKey = queryKey.value

            expect(initialKey).toEqual(['session', 0, 'admin', 'admins', { page: 0, size: 20 }])
            expect(initialKey.some(isRef)).toBe(false)
            params.value = { page: 1, size: 50 }
            expect(queryKey.value).toEqual(['session', 0, 'admin', 'admins', { page: 1, size: 50 }])
            expect(initialKey).toEqual(['session', 0, 'admin', 'admins', { page: 0, size: 20 }])
        })

        it('useAdmins queryFn forwards params and preserves placeholder data', async () => {
            const { useAdmins } = useAdmin()
            const params = ref({ page: 1, size: 20 })
            const response = {
                content: [{ adminId: 1 }],
                page: 1,
                totalPages: 3,
                totalElements: 41,
                size: 20,
                hasNext: true,
                hasPrevious: true
            }

            vi.mocked(adminApi.getAdmins)
                .mockResolvedValueOnce(apiDataResponse<typeof adminApi.getAdmins>(response))

            useAdmins(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }

            await expect(query.queryFn()).resolves.toEqual({
                content: [{ adminId: 1 }],
                number: 1,
                totalPages: 3,
                totalElements: 41,
                size: 20,
                first: false,
                last: false,
                empty: false
            })
            expect(adminApi.getAdmins).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData).toBeUndefined()
        })

        it('useCreateAdmin calls adminApi.createAdmin', async () => {
            const { useCreateAdmin } = useAdmin()
            const mutation = useCreateAdmin()

            vi.mocked(adminApi.createAdmin).mockResolvedValue(apiSuccessResponse<typeof adminApi.createAdmin>())

            await mutation.mutateAsync({ loginId: 'newadmin', boardId: 1, role: 'BOARD_ADMIN' })

            expect(adminApi.createAdmin).toHaveBeenCalledWith({ loginId: 'newadmin', boardId: 1, role: 'BOARD_ADMIN' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'admins'] })
        })

        it('useCreateAdmin accepts moderator role', async () => {
            const { useCreateAdmin } = useAdmin()
            const mutation = useCreateAdmin()

            vi.mocked(adminApi.createAdmin).mockResolvedValue(apiSuccessResponse<typeof adminApi.createAdmin>())

            await mutation.mutateAsync({ loginId: 'modadmin', boardId: 1, role: 'MODERATOR' })

            expect(adminApi.createAdmin).toHaveBeenCalledWith({ loginId: 'modadmin', boardId: 1, role: 'MODERATOR' })
        })

        it('ignores a completed mutation after the session generation changes', async () => {
            const { useCreateAdmin } = useAdmin()
            const mutation = useCreateAdmin()
            let releaseRequest!: () => void
            const requestGate = new Promise<void>((resolve) => {
                releaseRequest = resolve
            })

            vi.mocked(adminApi.createAdmin).mockImplementationOnce(async () => {
                await requestGate
                return apiSuccessResponse<typeof adminApi.createAdmin>()
            })

            const request = mutation.mutateAsync({
                loginId: 'old-session',
                boardId: 1,
                role: 'BOARD_ADMIN',
            })
            await Promise.resolve()
            mockAuthStore.sessionGeneration = 1
            releaseRequest()
            await request

            expect(mockInvalidateQueries).not.toHaveBeenCalled()
        })

        it('useUpdateAdminStatus calls activate API', async () => {
            const { useUpdateAdminStatus } = useAdmin()
            const mutation = useUpdateAdminStatus()

            vi.mocked(adminApi.activateAdmin).mockResolvedValue(apiSuccessResponse<typeof adminApi.activateAdmin>())

            await mutation.mutateAsync({ adminId: 1, action: 'activate' })

            expect(adminApi.activateAdmin).toHaveBeenCalledWith(1)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'admins'] })
        })

        it('useUpdateAdminStatus calls deactivate API', async () => {
            const { useUpdateAdminStatus } = useAdmin()
            const mutation = useUpdateAdminStatus()

            vi.mocked(adminApi.deactivateAdmin).mockResolvedValue(apiSuccessResponse<typeof adminApi.deactivateAdmin>())

            await mutation.mutateAsync({ adminId: 1, action: 'deactivate' })

            expect(adminApi.deactivateAdmin).toHaveBeenCalledWith(1)
        })
    })

    describe('Super Admin Management', () => {
        it('useSuperAdmins returns query hooks', () => {
            const { useSuperAdmins } = useAdmin()
            const result = useSuperAdmins()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useSuperAdmins queryFn returns typed array payload', async () => {
            const { useSuperAdmins } = useAdmin()

            vi.mocked(adminApi.getSuperAdmin)
                .mockResolvedValueOnce(apiDataResponse<typeof adminApi.getSuperAdmin>([{ loginId: 'super' }]))

            useSuperAdmins()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual([{ loginId: 'super' }])
        })

        it('useUpdateSuperAdminStatus calls activate API', async () => {
            const { useUpdateSuperAdminStatus } = useAdmin()
            const mutation = useUpdateSuperAdminStatus()

            vi.mocked(adminApi.activeSuperAdmin).mockResolvedValue(apiSuccessResponse<typeof adminApi.activeSuperAdmin>())

            await mutation.mutateAsync({ loginId: 'superadmin', action: 'activate', reason: 'grant' })

            expect(adminApi.activeSuperAdmin).toHaveBeenCalledWith({ loginId: 'superadmin', reason: 'grant' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'super'] })
        })

        it('useUpdateSuperAdminStatus calls deactivate API', async () => {
            const { useUpdateSuperAdminStatus } = useAdmin()
            const mutation = useUpdateSuperAdminStatus()

            vi.mocked(adminApi.deactivateSuperAdmin).mockResolvedValue(apiSuccessResponse<typeof adminApi.deactivateSuperAdmin>())

            await mutation.mutateAsync({ loginId: 'superadmin', action: 'deactivate', reason: 'revoke' })

            expect(adminApi.deactivateSuperAdmin).toHaveBeenCalledWith({ loginId: 'superadmin', reason: 'revoke' })
        })
    })

    describe('User Management', () => {
        it('useUsers returns query hooks', () => {
            const { useUsers } = useAdmin()
            const params = ref({ page: 0, size: 10 })
            const result = useUsers(params)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useUsers queryFn forwards params and preserves placeholder data', async () => {
            const { useUsers } = useAdmin()
            const params = ref({ page: 2, size: 5, q: 'john' })
            const response = {
                content: [{ userId: 1 }],
                number: 2,
                size: 5,
                totalElements: 16,
                totalPages: 4,
                first: false,
                last: false,
                empty: false
            }
            vi.mocked(adminApi.getUsers).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getUsers>(response))

            useUsers(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }

            await expect(query.queryFn()).resolves.toEqual(response)
            expect(adminApi.getUsers).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData).toBeUndefined()
        })

        it('useUpdateUserStatus calls adminApi', async () => {
            const { useUpdateUserStatus } = useAdmin()
            const mutation = useUpdateUserStatus()

            vi.mocked(adminApi.updateUserStatus).mockResolvedValue(apiSuccessResponse<typeof adminApi.updateUserStatus>())

            await mutation.mutateAsync({ userId: 1, status: 'ACTIVE', reason: 'reviewed' })

            expect(adminApi.updateUserStatus).toHaveBeenCalledWith(1, 'ACTIVE', 'reviewed')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'users'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'users', 'detail'] })
        })

        it('useSanctionUser calls adminApi.sanctionUser', async () => {
            const { useSanctionUser } = useAdmin()
            const mutation = useSanctionUser()

            vi.mocked(adminApi.sanctionUser).mockResolvedValue(apiSuccessResponse<typeof adminApi.sanctionUser>())

            await mutation.mutateAsync({ targetUserId: 1, type: 'BAN', remark: 'Violation' })

            expect(adminApi.sanctionUser).toHaveBeenCalledWith({ targetUserId: 1, type: 'BAN', remark: 'Violation' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'users'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'users', 'detail'] })
        })

        it('admin user detail queries forward user-specific params', async () => {
            const { useAdminUserDetail, useAdminUserPosts, useAdminUserComments, useAdminUserSubscriptions } = useAdmin()
            const userId = ref(5)
            const params = ref({ page: 1, size: 10 })

            vi.mocked(adminApi.getUserDetail).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getUserDetail>({ userId: 5 }))
            vi.mocked(adminApi.getUserPosts).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getUserPosts>({ content: [{ postId: 1, deleted: true }], page: 1, size: 10, totalElements: 21, totalPages: 3, hasNext: true, hasPrevious: true }))
            vi.mocked(adminApi.getUserComments).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getUserComments>({ content: [{ commentId: 2, deleted: true }], page: 0, size: 10, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false }))
            vi.mocked(adminApi.getUserSubscriptions).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getUserSubscriptions>({ content: [{ boardId: 3, subscriptionAccessible: false }], page: 2, size: 10, totalElements: 23, totalPages: 3, hasNext: false, hasPrevious: true }))

            useAdminUserDetail(userId)
            const detailQuery = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(detailQuery.queryFn()).resolves.toEqual({ userId: 5 })
            expect(adminApi.getUserDetail).toHaveBeenCalledWith(5)

            useAdminUserPosts(userId, params)
            const postsQuery = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }
            await expect(postsQuery.queryFn()).resolves.toMatchObject({
                content: [{ postId: 1, deleted: true }],
                number: 1,
                first: false,
                last: false
            })
            expect(adminApi.getUserPosts).toHaveBeenCalledWith(5, params.value)
            expect(postsQuery.placeholderData).toBeUndefined()

            useAdminUserComments(userId, params)
            const commentsQuery = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }
            await expect(commentsQuery.queryFn()).resolves.toMatchObject({
                content: [{ commentId: 2, deleted: true }],
                number: 0,
                first: true,
                last: true
            })
            expect(adminApi.getUserComments).toHaveBeenCalledWith(5, params.value)
            expect(commentsQuery.placeholderData).toBeUndefined()

            useAdminUserSubscriptions(userId, params)
            const subscriptionsQuery = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }
            await expect(subscriptionsQuery.queryFn()).resolves.toMatchObject({
                content: [{ boardId: 3, subscriptionAccessible: false }],
                number: 2,
                first: false,
                last: true
            })
            expect(adminApi.getUserSubscriptions).toHaveBeenCalledWith(5, params.value)
            expect(subscriptionsQuery.placeholderData).toBeUndefined()
        })
    })

    describe('Report Management', () => {
        it('useReports returns query hooks', () => {
            const { useReports } = useAdmin()
            const params = ref({ page: 0, size: 10 })
            const result = useReports(params)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useReports queryFn forwards params and preserves placeholder data', async () => {
            const { useReports } = useAdmin()
            const params = ref({ page: 1, size: 20 })
            const response = {
                content: [{ reportId: 7 }],
                number: 1,
                size: 20,
                totalElements: 21,
                totalPages: 2,
                first: false,
                last: true,
                empty: false
            }
            vi.mocked(adminApi.getReports).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getReports>(response))

            useReports(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }

            await expect(query.queryFn()).resolves.toEqual(response)
            expect(adminApi.getReports).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData).toBeUndefined()
        })

        it('useResolveReport calls adminApi.resolveReport', async () => {
            const { useResolveReport } = useAdmin()
            const mutation = useResolveReport()
            const updatedReport = {
                reportId: 1,
                status: 'RESOLVED',
                targetType: 'POST',
                targetId: 10,
            }

            vi.mocked(adminApi.resolveReport).mockResolvedValue(
                apiSuccessDataResponse<typeof adminApi.resolveReport>(updatedReport),
            )

            await mutation.mutateAsync({ reportId: 1, data: { status: 'RESOLVED' } })

            expect(adminApi.resolveReport).toHaveBeenCalledWith(1, { status: 'RESOLVED' })
            expect(mockSetQueriesData).toHaveBeenCalledTimes(2)
            expect(mockInvalidateQueries).toHaveBeenCalledTimes(1)

            const statusFilterPredicate = mockInvalidateQueries.mock.calls[0]?.[0]?.predicate as (
                query: { queryKey: readonly unknown[] }
            ) => boolean
            expect(statusFilterPredicate({
                queryKey: ['session', 0, 'admin', 'reports', { status: 'PENDING' }],
            })).toBe(true)
            expect(statusFilterPredicate({
                queryKey: ['session', 0, 'admin', 'reports', { status: 'RESOLVED' }],
            })).toBe(true)
            expect(statusFilterPredicate({
                queryKey: ['session', 0, 'admin', 'reports', { targetType: 'POST' }],
            })).toBe(false)

            const replaceCachedReport = mockSetQueriesData.mock.calls[1]?.[1] as (page: unknown) => unknown
            expect(replaceCachedReport({
                content: [{ ...updatedReport, status: 'PENDING' }],
                number: 0,
                size: 20,
                totalElements: 1,
                totalPages: 1,
                first: true,
                last: true,
                empty: false,
            })).toMatchObject({ content: [updatedReport] })
        })

        it('useResolveReport leaves report caches unchanged when resolve fails', async () => {
            const { useResolveReport } = useAdmin()
            const mutation = useResolveReport()
            const error = new Error('validation error')

            vi.mocked(adminApi.resolveReport).mockRejectedValue(error)

            await expect(mutation.mutateAsync({ reportId: 1, data: { status: 'RESOLVED' } })).rejects.toThrow(error)

            expect(adminApi.resolveReport).toHaveBeenCalledWith(1, { status: 'RESOLVED' })
            expect(mockSetQueriesData).not.toHaveBeenCalled()
            expect(mockInvalidateQueries).not.toHaveBeenCalled()
        })
    })

    describe('IP Block Management', () => {
        it('useIpBlocks returns query hooks', () => {
            const { useIpBlocks } = useAdmin()
            const result = useIpBlocks(ref({ page: 0, size: 20 }))

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useIpBlocks queryFn forwards params and preserves placeholder data', async () => {
            const { useIpBlocks } = useAdmin()
            const params = ref({ page: 1, size: 20 })
            const response = {
                content: [{ ipAddress: '1.1.1.1' }],
                page: 0,
                totalPages: 1,
                totalElements: 1,
                size: 20,
                hasNext: false,
                hasPrevious: false
            }
            vi.mocked(adminApi.getIpBlocks).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getIpBlocks>(response))

            useIpBlocks(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }
            await expect(query.queryFn()).resolves.toEqual({
                content: [{ ipAddress: '1.1.1.1' }],
                number: 0,
                totalPages: 1,
                totalElements: 1,
                size: 20,
                first: true,
                last: true,
                empty: false
            })
            expect(adminApi.getIpBlocks).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData).toBeUndefined()
        })

        it('useBlockIp calls adminApi.blockIp', async () => {
            const { useBlockIp } = useAdmin()
            const mutation = useBlockIp()

            vi.mocked(adminApi.blockIp).mockResolvedValue(apiSuccessResponse<typeof adminApi.blockIp>())

            await mutation.mutateAsync({ ipAddress: '192.168.1.1', reason: 'Spam' })

            expect(adminApi.blockIp).toHaveBeenCalledWith({ ipAddress: '192.168.1.1', reason: 'Spam' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'ip-blocks'] })
        })

        it('useUnblockIp calls adminApi.unblockIp', async () => {
            const { useUnblockIp } = useAdmin()
            const mutation = useUnblockIp()

            vi.mocked(adminApi.unblockIp).mockResolvedValue(apiSuccessResponse<typeof adminApi.unblockIp>())

            await mutation.mutateAsync('192.168.1.1')

            expect(adminApi.unblockIp).toHaveBeenCalledWith('192.168.1.1')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'ip-blocks'] })
        })
    })

    describe('Config Management', () => {
        it('useConfigs returns query hooks', () => {
            const { useConfigs } = useAdmin()
            const result = useConfigs()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useConfigs queryFn returns api data', async () => {
            const { useConfigs } = useAdmin()
            vi.mocked(adminApi.getConfigs).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getConfigs>([{ key: 'site.name' }]))

            useConfigs()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual([{ key: 'site.name' }])
        })

        it('useCreateConfig calls adminApi.createConfig', async () => {
            const { useCreateConfig } = useAdmin()
            const mutation = useCreateConfig()

            vi.mocked(adminApi.createConfig).mockResolvedValue(apiSuccessResponse<typeof adminApi.createConfig>())

            await mutation.mutateAsync({ key: 'test_key', value: 'test_value' })

            expect(adminApi.createConfig).toHaveBeenCalledWith({ key: 'test_key', value: 'test_value' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'configs'] })
        })

        it('useUpdateConfig calls adminApi.updateConfig', async () => {
            const { useUpdateConfig } = useAdmin()
            const mutation = useUpdateConfig()

            vi.mocked(adminApi.updateConfig).mockResolvedValue(apiSuccessResponse<typeof adminApi.updateConfig>())

            await mutation.mutateAsync({ key: 'test_key', value: 'new_value', description: 'Updated' })

            expect(adminApi.updateConfig).toHaveBeenCalledWith('test_key', 'new_value', 'Updated')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'configs'] })
        })

        it('useDeleteConfig calls adminApi.deleteConfig', async () => {
            const { useDeleteConfig } = useAdmin()
            const mutation = useDeleteConfig()

            vi.mocked(adminApi.deleteConfig).mockResolvedValue(apiSuccessResponse<typeof adminApi.deleteConfig>())

            await mutation.mutateAsync('test_key')

            expect(adminApi.deleteConfig).toHaveBeenCalledWith('test_key')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'configs'] })
        })
    })

    describe('Dashboard Stats', () => {
        it('useDashboardStats returns query hooks', () => {
            const { useDashboardStats } = useAdmin()
            const result = useDashboardStats()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useDashboardStats queryFn returns stats payload', async () => {
            const { useDashboardStats } = useAdmin()
            vi.mocked(adminApi.getDashboardStats).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getDashboardStats>({ users: 10 }))

            useDashboardStats()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual({ users: 10 })
        })
    })

    describe('Board Management', () => {
        it('useAdminBoards returns query hooks', () => {
            const { useAdminBoards } = useAdmin()
            const result = useAdminBoards()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useAdminBoards queryFn returns board list', async () => {
            const { useAdminBoards } = useAdmin()
            vi.mocked(adminApi.getBoards).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getBoards>([{ boardUrl: 'free' }]))

            useAdminBoards()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual([{ boardUrl: 'free' }])
        })

        it('useCreateBoard calls adminApi.createBoard', async () => {
            const { useCreateBoard } = useAdmin()
            const mutation = useCreateBoard()

            vi.mocked(adminApi.createBoard).mockResolvedValue(apiSuccessResponse<typeof adminApi.createBoard>())

            await mutation.mutateAsync({ boardName: 'New Board', boardUrl: 'new-board' })

            expect(adminApi.createBoard).toHaveBeenCalledWith({ boardName: 'New Board', boardUrl: 'new-board' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'boards'] })
        })

        it('useUpdateBoard calls adminApi.updateBoard', async () => {
            const { useUpdateBoard } = useAdmin()
            const mutation = useUpdateBoard()

            vi.mocked(adminApi.updateBoard).mockResolvedValue(apiSuccessResponse<typeof adminApi.updateBoard>())

            await mutation.mutateAsync({ boardUrl: 'test-board', data: { boardName: 'Updated Name' } })

            expect(adminApi.updateBoard).toHaveBeenCalledWith('test-board', { boardName: 'Updated Name' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'boards'] })
        })

        it('useDeleteBoard calls adminApi.deleteBoard', async () => {
            const { useDeleteBoard } = useAdmin()
            const mutation = useDeleteBoard()

            vi.mocked(adminApi.deleteBoard).mockResolvedValue(apiSuccessResponse<typeof adminApi.deleteBoard>())

            await mutation.mutateAsync('test-board')

            expect(adminApi.deleteBoard).toHaveBeenCalledWith('test-board')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'boards'] })
            expect(mockRemoveQueries).toHaveBeenCalledWith({
                queryKey: ['session', 0, 'board', 'detail', 'test-board'],
            })
        })

        it('cleans old board resources and refreshes new resources after a URL change', async () => {
            const { useUpdateBoard } = useAdmin()
            const mutation = useUpdateBoard()
            vi.mocked(adminApi.updateBoard).mockResolvedValue(apiSuccessResponse<typeof adminApi.updateBoard>())

            await mutation.mutateAsync({
                boardUrl: 'old-board',
                data: { boardName: 'Board', boardUrl: 'new-board' },
            })

            expect(mockRemoveQueries).toHaveBeenCalledWith({
                queryKey: ['session', 0, 'board', 'detail', 'old-board'],
            })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({
                queryKey: ['session', 0, 'board', 'detail', 'new-board'],
            })
        })

        it('useReorderBoards calls the atomic order API', async () => {
            const { useReorderBoards } = useAdmin()
            const mutation = useReorderBoards()
            vi.mocked(adminApi.reorderBoards).mockResolvedValue(apiDataResponse([]))

            await mutation.mutate([2, 1])

            expect(adminApi.reorderBoards).toHaveBeenCalledWith([2, 1])
        })

        it('useBoardManager queryFn returns manager payload', async () => {
            const { useBoardManager } = useAdmin()
            const boardId = ref(3)
            vi.mocked(adminApi.getBoardManager).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getBoardManager>({ adminId: 99 }))

            useBoardManager(boardId)
            const query = mockQueryOptions.at(-1) as {
                queryKey: { value: unknown[] } | unknown[]
                queryFn: () => Promise<unknown>
            }
            await expect(query.queryFn()).resolves.toEqual({ adminId: 99 })
            const resolvedQueryKey = Array.isArray(query.queryKey) ? query.queryKey : query.queryKey.value
            expect(resolvedQueryKey).toEqual(['session', 0, 'admin', 'board-manager', 3])
            expect(adminApi.getBoardManager).toHaveBeenCalledWith(3)
        })

        it('useUpdateBoardManager calls adminApi.updateBoardManager', async () => {
            const { useUpdateBoardManager } = useAdmin()
            const mutation = useUpdateBoardManager()

            vi.mocked(adminApi.updateBoardManager).mockResolvedValue(apiSuccessResponse<typeof adminApi.updateBoardManager>())

            await mutation.mutateAsync({ boardId: 5, data: { loginId: 'manager' } })

            expect(adminApi.updateBoardManager).toHaveBeenCalledWith(5, { loginId: 'manager' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'board-manager', 5] })
        })
    })

    describe('Error Log Management', () => {
        it('useErrorLogs returns query hooks', () => {
            const { useErrorLogs } = useAdmin()
            const params = ref({ page: 0, size: 20 })
            const result = useErrorLogs(params)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useErrorLogs queryFn forwards params and preserves placeholder data', async () => {
            const { useErrorLogs } = useAdmin()
            const params = ref({ page: 0, size: 20, errorType: 'BusinessException', httpStatus: 500 })
            const response = {
                content: [{ errorLogId: 1, errorType: 'BusinessException', httpStatus: 500 }],
                number: 0,
                size: 20,
                totalElements: 1,
                totalPages: 1,
                first: true,
                last: true,
                empty: false
            }
            vi.mocked(adminApi.getErrorLogs).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getErrorLogs>(response))

            useErrorLogs(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }

            await expect(query.queryFn()).resolves.toEqual(response)
            expect(adminApi.getErrorLogs).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData).toBeUndefined()
        })

        it('useErrorLogs queryFn forwards AbortSignal to API config', async () => {
            const { useErrorLogs } = useAdmin()
            const params = ref({ page: 0, size: 20 })
            const signal = new AbortController().signal
            const response = {
                content: [],
                number: 0,
                size: 20,
                totalElements: 0,
                totalPages: 0,
                first: true,
                last: true,
                empty: true
            }
            vi.mocked(adminApi.getErrorLogs).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getErrorLogs>(response))

            useErrorLogs(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: (context?: { signal?: AbortSignal }) => Promise<unknown>
            }

            await expect(query.queryFn({ signal })).resolves.toEqual(response)
            expect(adminApi.getErrorLogs).toHaveBeenCalledWith(params.value, { signal })
        })

        it('useErrorLog uses detail query key and returns detail payload', async () => {
            const { useErrorLog } = useAdmin()
            const errorLogId = ref<number | null>(1)
            const signal = new AbortController().signal
            const detailResponse = { errorLogId: 1, stackTrace: 'stack trace' }
            vi.mocked(adminApi.getErrorLog).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getErrorLog>(detailResponse))

            useErrorLog(errorLogId)
            const query = mockQueryOptions.at(-1) as {
                enabled: { value: boolean }
                queryKey: { value: unknown[] }
                queryFn: (context?: { signal?: AbortSignal }) => Promise<unknown>
            }

            await expect(query.queryFn({ signal })).resolves.toEqual(detailResponse)
            expect(query.queryKey.value).toEqual(['session', 0, 'admin', 'error-logs', 'detail', 1])
            expect(query.enabled.value).toBe(true)
            expect(adminApi.getErrorLog).toHaveBeenCalledWith(1, { signal })
        })

        it('useErrorLog detail query stays disabled and skips API when id is null', async () => {
            const { useErrorLog } = useAdmin()
            const errorLogId = ref<number | null>(null)

            useErrorLog(errorLogId)
            const query = mockQueryOptions.at(-1) as {
                enabled: { value: boolean }
                queryFn: () => Promise<unknown>
            }

            await expect(query.queryFn()).resolves.toBeNull()
            expect(query.enabled.value).toBe(false)
            expect(adminApi.getErrorLog).not.toHaveBeenCalled()
        })

        it('scopes imperative error log detail fetches to the current session', async () => {
            const { useErrorLog } = useAdmin()
            const detailResponse = { errorLogId: 3, stackTrace: 'stack trace' }
            vi.mocked(adminApi.getErrorLog).mockResolvedValueOnce(
                apiDataResponse<typeof adminApi.getErrorLog>(detailResponse),
            )

            const { mutateAsync } = useErrorLog()
            await expect(mutateAsync(3)).resolves.toEqual(detailResponse)

            expect(mockFetchQuery).toHaveBeenCalledWith(expect.objectContaining({
                queryKey: ['session', 0, 'admin', 'error-logs', 'detail', 3],
                meta: { authScoped: true },
            }))
        })

        it('useResolveErrorLog calls adminApi.resolveErrorLog with memo', async () => {
            const { useResolveErrorLog } = useAdmin()
            const mutation = useResolveErrorLog()

            vi.mocked(adminApi.resolveErrorLog).mockResolvedValue(apiSuccessResponse<typeof adminApi.resolveErrorLog>())

            await mutation.mutateAsync({ errorLogId: 1, data: { memo: '확인 완료' } })

            expect(adminApi.resolveErrorLog).toHaveBeenCalledWith(1, { memo: '확인 완료' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'error-logs'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'error-log-stats'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'error-logs', 'detail', 1] })
        })

        it('useResolveErrorLog calls adminApi.resolveErrorLog without memo', async () => {
            const { useResolveErrorLog } = useAdmin()
            const mutation = useResolveErrorLog()

            vi.mocked(adminApi.resolveErrorLog).mockResolvedValue(apiSuccessResponse<typeof adminApi.resolveErrorLog>())

            await mutation.mutateAsync({ errorLogId: 2, data: undefined })

            expect(adminApi.resolveErrorLog).toHaveBeenCalledWith(2, undefined)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'error-logs'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'error-log-stats'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'admin', 'error-logs', 'detail', 2] })
        })

        it('useErrorLogStats returns query hooks', () => {
            const { useErrorLogStats } = useAdmin()
            const result = useErrorLogStats()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useErrorLogStats queryFn returns stats payload', async () => {
            const { useErrorLogStats } = useAdmin()
            const statsResponse = { totalCount: 100, unresolvedCount: 30, resolvedCount: 70 }
            vi.mocked(adminApi.getErrorLogStats).mockResolvedValueOnce(apiDataResponse<typeof adminApi.getErrorLogStats>(statsResponse))

            useErrorLogStats()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual(statsResponse)
        })
    })
})
