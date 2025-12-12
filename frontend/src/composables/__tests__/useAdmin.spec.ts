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
        deactiveSuperAdmin: vi.fn(),
        getUsers: vi.fn(),
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
        deleteBoard: vi.fn()
    }
}))

// Mock vue-query
const mockInvalidateQueries = vi.fn()
vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options) => {
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
                const result = await options.mutationFn(variables)
                options.onSuccess?.(result, variables)
                return result
            },
            mutateAsync: async (variables: unknown) => {
                const result = await options.mutationFn(variables)
                options.onSuccess?.(result, variables)
                return result
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
    })

    describe('Admin Management', () => {
        it('useAdmins returns query hooks', () => {
            const { useAdmins } = useAdmin()
            const result = useAdmins()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
        })

        it('useCreateAdmin calls adminApi.createAdmin', async () => {
            const { useCreateAdmin } = useAdmin()
            const mutation = useCreateAdmin()

            vi.mocked(adminApi.createAdmin).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ loginId: 'newadmin', boardId: 1 })

            expect(adminApi.createAdmin).toHaveBeenCalledWith({ loginId: 'newadmin', boardId: 1 })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'admins'] })
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

            vi.mocked(adminApi.deactiveSuperAdmin).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ loginId: 'superadmin', action: 'deactivate' })

            expect(adminApi.deactiveSuperAdmin).toHaveBeenCalledWith({ loginId: 'superadmin' })
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

        it('useUpdateUserStatus calls adminApi', async () => {
            const { useUpdateUserStatus } = useAdmin()
            const mutation = useUpdateUserStatus()

            vi.mocked(adminApi.updateUserStatus).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ userId: 1, status: 'ACTIVE' })

            expect(adminApi.updateUserStatus).toHaveBeenCalledWith(1, 'ACTIVE')
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'users'] })
        })

        it('useSanctionUser calls adminApi.sanctionUser', async () => {
            const { useSanctionUser } = useAdmin()
            const mutation = useSanctionUser()

            vi.mocked(adminApi.sanctionUser).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ userId: 1, type: 'BAN', reason: 'Violation' })

            expect(adminApi.sanctionUser).toHaveBeenCalledWith({ userId: 1, type: 'BAN', reason: 'Violation' })
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

        it('useResolveReport calls adminApi.resolveReport', async () => {
            const { useResolveReport } = useAdmin()
            const mutation = useResolveReport()

            vi.mocked(adminApi.resolveReport).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({ reportId: 1, data: { status: 'RESOLVED' } })

            expect(adminApi.resolveReport).toHaveBeenCalledWith(1, { status: 'RESOLVED' })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['admin', 'reports'] })
        })
    })

    describe('IP Block Management', () => {
        it('useIpBlocks returns query hooks', () => {
            const { useIpBlocks } = useAdmin()
            const result = useIpBlocks()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
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
    })

    describe('Board Management', () => {
        it('useAdminBoards returns query hooks', () => {
            const { useAdminBoards } = useAdmin()
            const result = useAdminBoards()

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
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
    })
})
