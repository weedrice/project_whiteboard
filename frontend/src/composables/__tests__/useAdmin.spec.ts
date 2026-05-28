import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useAdmin } from '../useAdmin'
import { adminApi } from '@/api/admin'

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
const mockQueryOptions: Array<Record<string, unknown>> = []
vi.mock('@tanstack/vue-query', () => ({
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
                try {
                    const result = await options.mutationFn(variables)
                    options.onSuccess?.(result, variables)
                    options.onSettled?.(result, null, variables)
                    return result
                } catch (error) {
                    options.onError?.(error, variables)
                    options.onSettled?.(undefined, error, variables)
                    throw error
                }
            },
            mutateAsync: async (variables: unknown) => {
                try {
                    const result = await options.mutationFn(variables)
                    options.onSuccess?.(result, variables)
                    options.onSettled?.(result, null, variables)
                    return result
                } catch (error) {
                    options.onError?.(error, variables)
                    options.onSettled?.(undefined, error, variables)
                    throw error
                }
            },
            isLoading: ref(false),
            error: ref(null)
        }
    }),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries
    }))
}))

describe('useAdmin', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockQueryOptions.length = 0
    })

    describe('Admin Management', () => {
        it('useAdmins returns query hooks', () => {
            const { useAdmins } = useAdmin()
            const result = useAdmins(ref({ page: 0, size: 20 }))

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
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
                .mockResolvedValueOnce({ data: { data: response } } as any)

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
            expect(query.placeholderData('prev-admins')).toBe('prev-admins')
        })

        it('useCreateAdmin calls adminApi.createAdmin', async () => {
            const { useCreateAdmin } = useAdmin()
            const mutation = useCreateAdmin()

            vi.mocked(adminApi.createAdmin).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ loginId: 'newadmin', boardId: 1, role: 'BOARD_ADMIN' })

            expect(adminApi.createAdmin).toHaveBeenCalledWith({ loginId: 'newadmin', boardId: 1, role: 'BOARD_ADMIN' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'admins'] })
        })

        it('useCreateAdmin accepts moderator role', async () => {
            const { useCreateAdmin } = useAdmin()
            const mutation = useCreateAdmin()

            vi.mocked(adminApi.createAdmin).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ loginId: 'modadmin', boardId: 1, role: 'MODERATOR' })

            expect(adminApi.createAdmin).toHaveBeenCalledWith({ loginId: 'modadmin', boardId: 1, role: 'MODERATOR' })
        })

        it('useUpdateAdminStatus calls activate API', async () => {
            const { useUpdateAdminStatus } = useAdmin()
            const mutation = useUpdateAdminStatus()

            vi.mocked(adminApi.activateAdmin).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ adminId: 1, action: 'activate' })

            expect(adminApi.activateAdmin).toHaveBeenCalledWith(1)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'admins'] })
        })

        it('useUpdateAdminStatus calls deactivate API', async () => {
            const { useUpdateAdminStatus } = useAdmin()
            const mutation = useUpdateAdminStatus()

            vi.mocked(adminApi.deactivateAdmin).mockResolvedValue({ data: { success: true } } as any)

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
                .mockResolvedValueOnce({ data: { data: [{ loginId: 'super' }] } } as any)

            useSuperAdmins()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual([{ loginId: 'super' }])
        })

        it('useUpdateSuperAdminStatus calls activate API', async () => {
            const { useUpdateSuperAdminStatus } = useAdmin()
            const mutation = useUpdateSuperAdminStatus()

            vi.mocked(adminApi.activeSuperAdmin).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ loginId: 'superadmin', action: 'activate' })

            expect(adminApi.activeSuperAdmin).toHaveBeenCalledWith({ loginId: 'superadmin' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'super'] })
        })

        it('useUpdateSuperAdminStatus calls deactivate API', async () => {
            const { useUpdateSuperAdminStatus } = useAdmin()
            const mutation = useUpdateSuperAdminStatus()

            vi.mocked(adminApi.deactivateSuperAdmin).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ loginId: 'superadmin', action: 'deactivate' })

            expect(adminApi.deactivateSuperAdmin).toHaveBeenCalledWith({ loginId: 'superadmin' })
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
            vi.mocked(adminApi.getUsers).mockResolvedValueOnce({ data: { data: response } } as any)

            useUsers(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }

            await expect(query.queryFn()).resolves.toEqual(response)
            expect(adminApi.getUsers).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData('prev-users')).toBe('prev-users')
        })

        it('useUpdateUserStatus calls adminApi', async () => {
            const { useUpdateUserStatus } = useAdmin()
            const mutation = useUpdateUserStatus()

            vi.mocked(adminApi.updateUserStatus).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ userId: 1, status: 'ACTIVE' })

            expect(adminApi.updateUserStatus).toHaveBeenCalledWith(1, 'ACTIVE')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'users'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'users', 'detail'] })
        })

        it('useSanctionUser calls adminApi.sanctionUser', async () => {
            const { useSanctionUser } = useAdmin()
            const mutation = useSanctionUser()

            vi.mocked(adminApi.sanctionUser).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ targetUserId: 1, type: 'BAN', remark: 'Violation' })

            expect(adminApi.sanctionUser).toHaveBeenCalledWith({ targetUserId: 1, type: 'BAN', remark: 'Violation' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'users'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'users', 'detail'] })
        })

        it('admin user detail queries forward user-specific params', async () => {
            const { useAdminUserDetail, useAdminUserPosts, useAdminUserComments, useAdminUserSubscriptions } = useAdmin()
            const userId = ref(5)
            const params = ref({ page: 1, size: 10 })

            vi.mocked(adminApi.getUserDetail).mockResolvedValueOnce({ data: { data: { userId: 5 } } } as any)
            vi.mocked(adminApi.getUserPosts).mockResolvedValueOnce({ data: { data: { content: [{ postId: 1, deleted: true }], page: 1, size: 10, totalElements: 21, totalPages: 3, hasNext: true, hasPrevious: true } } } as any)
            vi.mocked(adminApi.getUserComments).mockResolvedValueOnce({ data: { data: { content: [{ commentId: 2, deleted: true }], page: 0, size: 10, totalElements: 2, totalPages: 1, hasNext: false, hasPrevious: false } } } as any)
            vi.mocked(adminApi.getUserSubscriptions).mockResolvedValueOnce({ data: { data: { content: [{ boardId: 3, subscriptionAccessible: false }], page: 2, size: 10, totalElements: 23, totalPages: 3, hasNext: false, hasPrevious: true } } } as any)

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
            expect(postsQuery.placeholderData('prev-posts')).toBe('prev-posts')

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
            expect(commentsQuery.placeholderData('prev-comments')).toBe('prev-comments')

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
            expect(subscriptionsQuery.placeholderData('prev-subscriptions')).toBe('prev-subscriptions')
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
            vi.mocked(adminApi.getReports).mockResolvedValueOnce({ data: { data: response } } as any)

            useReports(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }

            await expect(query.queryFn()).resolves.toEqual(response)
            expect(adminApi.getReports).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData('prev-reports')).toBe('prev-reports')
        })

        it('useResolveReport calls adminApi.resolveReport', async () => {
            const { useResolveReport } = useAdmin()
            const mutation = useResolveReport()

            vi.mocked(adminApi.resolveReport).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ reportId: 1, data: { status: 'RESOLVED' } })

            expect(adminApi.resolveReport).toHaveBeenCalledWith(1, { status: 'RESOLVED' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'reports'] })
        })

        it('useResolveReport invalidates reports even when resolve fails', async () => {
            const { useResolveReport } = useAdmin()
            const mutation = useResolveReport()
            const error = new Error('validation error')

            vi.mocked(adminApi.resolveReport).mockRejectedValue(error)

            await expect(mutation.mutateAsync({ reportId: 1, data: { status: 'RESOLVED' } })).rejects.toThrow(error)

            expect(adminApi.resolveReport).toHaveBeenCalledWith(1, { status: 'RESOLVED' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'reports'] })
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
            vi.mocked(adminApi.getIpBlocks).mockResolvedValueOnce({ data: { data: response } } as any)

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
            expect(query.placeholderData('prev-ip-blocks')).toBe('prev-ip-blocks')
        })

        it('useBlockIp calls adminApi.blockIp', async () => {
            const { useBlockIp } = useAdmin()
            const mutation = useBlockIp()

            vi.mocked(adminApi.blockIp).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ ipAddress: '192.168.1.1', reason: 'Spam' })

            expect(adminApi.blockIp).toHaveBeenCalledWith({ ipAddress: '192.168.1.1', reason: 'Spam' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'ip-blocks'] })
        })

        it('useUnblockIp calls adminApi.unblockIp', async () => {
            const { useUnblockIp } = useAdmin()
            const mutation = useUnblockIp()

            vi.mocked(adminApi.unblockIp).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync('192.168.1.1')

            expect(adminApi.unblockIp).toHaveBeenCalledWith('192.168.1.1')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'ip-blocks'] })
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
            vi.mocked(adminApi.getConfigs).mockResolvedValueOnce({ data: { data: [{ key: 'site.name' }] } } as any)

            useConfigs()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual([{ key: 'site.name' }])
        })

        it('useCreateConfig calls adminApi.createConfig', async () => {
            const { useCreateConfig } = useAdmin()
            const mutation = useCreateConfig()

            vi.mocked(adminApi.createConfig).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ key: 'test_key', value: 'test_value' })

            expect(adminApi.createConfig).toHaveBeenCalledWith({ key: 'test_key', value: 'test_value' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'configs'] })
        })

        it('useUpdateConfig calls adminApi.updateConfig', async () => {
            const { useUpdateConfig } = useAdmin()
            const mutation = useUpdateConfig()

            vi.mocked(adminApi.updateConfig).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ key: 'test_key', value: 'new_value', description: 'Updated' })

            expect(adminApi.updateConfig).toHaveBeenCalledWith('test_key', 'new_value', 'Updated')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'configs'] })
        })

        it('useDeleteConfig calls adminApi.deleteConfig', async () => {
            const { useDeleteConfig } = useAdmin()
            const mutation = useDeleteConfig()

            vi.mocked(adminApi.deleteConfig).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync('test_key')

            expect(adminApi.deleteConfig).toHaveBeenCalledWith('test_key')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'configs'] })
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
            vi.mocked(adminApi.getDashboardStats).mockResolvedValueOnce({ data: { data: { users: 10 } } } as any)

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
            vi.mocked(adminApi.getBoards).mockResolvedValueOnce({ data: { data: [{ boardUrl: 'free' }] } } as any)

            useAdminBoards()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual([{ boardUrl: 'free' }])
        })

        it('useCreateBoard calls adminApi.createBoard', async () => {
            const { useCreateBoard } = useAdmin()
            const mutation = useCreateBoard()

            vi.mocked(adminApi.createBoard).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ boardName: 'New Board', boardUrl: 'new-board' })

            expect(adminApi.createBoard).toHaveBeenCalledWith({ boardName: 'New Board', boardUrl: 'new-board' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'boards'] })
        })

        it('useUpdateBoard calls adminApi.updateBoard', async () => {
            const { useUpdateBoard } = useAdmin()
            const mutation = useUpdateBoard()

            vi.mocked(adminApi.updateBoard).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ boardUrl: 'test-board', data: { boardName: 'Updated Name' } })

            expect(adminApi.updateBoard).toHaveBeenCalledWith('test-board', { boardName: 'Updated Name' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'boards'] })
        })

        it('useDeleteBoard calls adminApi.deleteBoard', async () => {
            const { useDeleteBoard } = useAdmin()
            const mutation = useDeleteBoard()

            vi.mocked(adminApi.deleteBoard).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync('test-board')

            expect(adminApi.deleteBoard).toHaveBeenCalledWith('test-board')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'boards'] })
        })

        it('useBoardManager queryFn returns manager payload', async () => {
            const { useBoardManager } = useAdmin()
            const boardId = ref(3)
            vi.mocked(adminApi.getBoardManager).mockResolvedValueOnce({ data: { data: { adminId: 99 } } } as any)

            useBoardManager(boardId)
            const query = mockQueryOptions.at(-1) as {
                queryKey: { value: unknown[] } | unknown[]
                queryFn: () => Promise<unknown>
            }
            await expect(query.queryFn()).resolves.toEqual({ adminId: 99 })
            const resolvedQueryKey = Array.isArray(query.queryKey) ? query.queryKey : query.queryKey.value
            expect(resolvedQueryKey).toEqual(['admin', 'board-manager', 3])
            expect(adminApi.getBoardManager).toHaveBeenCalledWith(3)
        })

        it('useUpdateBoardManager calls adminApi.updateBoardManager', async () => {
            const { useUpdateBoardManager } = useAdmin()
            const mutation = useUpdateBoardManager()

            vi.mocked(adminApi.updateBoardManager).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ boardId: 5, data: { loginId: 'manager' } })

            expect(adminApi.updateBoardManager).toHaveBeenCalledWith(5, { loginId: 'manager' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'board-manager', 5] })
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
            vi.mocked(adminApi.getErrorLogs).mockResolvedValueOnce({ data: { data: response } } as any)

            useErrorLogs(params)
            const query = mockQueryOptions.at(-1) as {
                queryFn: () => Promise<unknown>
                placeholderData: (prev: unknown) => unknown
            }

            await expect(query.queryFn()).resolves.toEqual(response)
            expect(adminApi.getErrorLogs).toHaveBeenCalledWith(params.value)
            expect(query.placeholderData('prev-error-logs')).toBe('prev-error-logs')
        })

        it('useErrorLog mutation returns detail payload', async () => {
            const { useErrorLog } = useAdmin()
            const detailResponse = { errorLogId: 1, stackTrace: 'stack trace' }
            vi.mocked(adminApi.getErrorLog).mockResolvedValueOnce({ data: { data: detailResponse } } as any)

            const mutation = useErrorLog()

            await expect(mutation.mutateAsync(1)).resolves.toEqual(detailResponse)
            expect(adminApi.getErrorLog).toHaveBeenCalledWith(1)
        })

        it('useResolveErrorLog calls adminApi.resolveErrorLog with memo', async () => {
            const { useResolveErrorLog } = useAdmin()
            const mutation = useResolveErrorLog()

            vi.mocked(adminApi.resolveErrorLog).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ errorLogId: 1, data: { memo: '확인 완료' } })

            expect(adminApi.resolveErrorLog).toHaveBeenCalledWith(1, { memo: '확인 완료' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'error-logs'] })
        })

        it('useResolveErrorLog calls adminApi.resolveErrorLog without memo', async () => {
            const { useResolveErrorLog } = useAdmin()
            const mutation = useResolveErrorLog()

            vi.mocked(adminApi.resolveErrorLog).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ errorLogId: 2, data: undefined })

            expect(adminApi.resolveErrorLog).toHaveBeenCalledWith(2, undefined)
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'error-logs'] })
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
            vi.mocked(adminApi.getErrorLogStats).mockResolvedValueOnce({ data: { data: statsResponse } } as any)

            useErrorLogStats()
            const query = mockQueryOptions.at(-1) as { queryFn: () => Promise<unknown> }
            await expect(query.queryFn()).resolves.toEqual(statsResponse)
        })
    })
})
