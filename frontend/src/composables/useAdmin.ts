import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { adminApi } from '@/api/admin'
import { computed, type Ref } from 'vue'
import type { SanctionData, PageResponse, User, Report, ErrorLogItem, ErrorLogSearchParams, ErrorLogStats } from '@/types'

// Admin specific types
interface AdminCreateData {
    loginId: string
    boardId?: number
    role?: string
}

interface UserSearchParams {
    page?: number
    size?: number
    q?: string
}

interface ReportSearchParams {
    page?: number
    size?: number
}

interface ReportResolveData {
    status: 'RESOLVED' | 'REJECTED'
}

interface IpBlockData {
    ipAddress: string
    reason: string
}

interface ConfigCreateData {
    key: string
    value: string
    description?: string
}

interface BoardCreateData {
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
}

interface BoardUpdateData {
    boardName?: string
    boardUrl?: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
    isActive?: boolean
}

interface BoardManagerData {
    loginId: string
}

export function useAdmin() {
    const queryClient = useQueryClient()

    // --- Admin Management ---
    const useAdmins = () => {
        return useQuery({
            queryKey: ['admin', 'admins'],
            queryFn: async () => {
                const { data } = await adminApi.getAdmins()
                return data?.data ?? []
            }
        })
    }

    const useCreateAdmin = () => {
        return useMutation({
            mutationFn: (data: AdminCreateData) => adminApi.createAdmin(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'admins'] })
        })
    }

    const useUpdateAdminStatus = () => {
        return useMutation({
            mutationFn: ({ adminId, action }: { adminId: string | number, action: 'activate' | 'deactivate' }) => {
                if (action === 'activate') return adminApi.activateAdmin(adminId)
                return adminApi.deactivateAdmin(adminId)
            },
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'admins'] })
        })
    }

    const useSuperAdmins = () => {
        return useQuery({
            queryKey: ['admin', 'super'],
            queryFn: async () => {
                const { data } = await adminApi.getSuperAdmin()
                return Array.isArray(data?.data) ? data.data : []
            }
        })
    }

    const useUpdateSuperAdminStatus = () => {
        return useMutation({
            mutationFn: ({ loginId, action }: { loginId: string, action: 'activate' | 'deactivate' }) => {
                if (action === 'activate') return adminApi.activeSuperAdmin({ loginId })
                return adminApi.deactiveSuperAdmin({ loginId })
            },
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'super'] })
        })
    }

    // --- User Management ---
    const useUsers = (params: Ref<UserSearchParams>) => {
        return useQuery({
            queryKey: ['admin', 'users', params],
            queryFn: async () => {
                const { data } = await adminApi.getUsers(params.value)
                return data.data as PageResponse<User>
            },
            placeholderData: (previousData) => previousData
        })
    }

    const useUpdateUserStatus = () => {
        return useMutation({
            mutationFn: ({ userId, status }: { userId: string | number, status: string }) => adminApi.updateUserStatus(userId, status),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
        })
    }

    const useSanctionUser = () => {
        return useMutation({
            mutationFn: (data: SanctionData) => adminApi.sanctionUser(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
        })
    }

    // --- Report Management ---
    const useReports = (params: Ref<ReportSearchParams>) => {
        return useQuery({
            queryKey: ['admin', 'reports', params],
            queryFn: async () => {
                const { data } = await adminApi.getReports(params.value)
                return data.data as PageResponse<Report>
            },
            placeholderData: (previousData) => previousData
        })
    }

    const useResolveReport = () => {
        return useMutation({
            mutationFn: ({ reportId, data }: { reportId: string | number, data: ReportResolveData }) => adminApi.resolveReport(reportId, data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'reports'] })
        })
    }

    // --- IP Block Management ---
    const useIpBlocks = () => {
        return useQuery({
            queryKey: ['admin', 'ip-blocks'],
            queryFn: async () => {
                const { data } = await adminApi.getIpBlocks()
                return data.data
            }
        })
    }

    const useBlockIp = () => {
        return useMutation({
            mutationFn: (data: IpBlockData) => adminApi.blockIp(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'ip-blocks'] })
        })
    }

    const useUnblockIp = () => {
        return useMutation({
            mutationFn: (ipAddress: string) => adminApi.unblockIp(ipAddress),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'ip-blocks'] })
        })
    }

    // --- Config Management ---
    const useConfigs = () => {
        return useQuery({
            queryKey: ['admin', 'configs'],
            queryFn: async () => {
                const { data } = await adminApi.getConfigs()
                return data.data
            }
        })
    }

    const useUpdateConfig = () => {
        return useMutation({
            mutationFn: ({ key, value, description }: { key: string, value: string, description: string }) => adminApi.updateConfig(key, value, description),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'configs'] })
        })
    }

    const useCreateConfig = () => {
        return useMutation({
            mutationFn: (data: ConfigCreateData) => adminApi.createConfig(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'configs'] })
        })
    }

    const useDeleteConfig = () => {
        return useMutation({
            mutationFn: (key: string) => adminApi.deleteConfig(key),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'configs'] })
        })
    }

    // --- Dashboard Stats ---
    const useDashboardStats = () => {
        return useQuery({
            queryKey: ['admin', 'stats'],
            queryFn: async () => {
                const { data } = await adminApi.getDashboardStats()
                return data.data
            }
        })
    }

    // --- Board Management (Admin) ---
    const useAdminBoards = () => {
        return useQuery({
            queryKey: ['admin', 'boards'],
            queryFn: async () => {
                const { data } = await adminApi.getBoards()
                return data.data
            }
        })
    }

    const useCreateBoard = () => {
        return useMutation({
            mutationFn: (data: BoardCreateData) => adminApi.createBoard(data),
            onSuccess: () => {
                // Invalidate both admin boards and general boards list to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: ['admin', 'boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => adminApi.updateBoard(boardUrl, data),
            onSuccess: (_, { boardUrl, data }) => {
                // Invalidate both admin boards and general boards list to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: ['admin', 'boards'] })
                queryClient.invalidateQueries({ queryKey: ['board', boardUrl] })
                if (data.boardUrl && data.boardUrl !== boardUrl) {
                    queryClient.invalidateQueries({ queryKey: ['board', data.boardUrl] })
                }
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    const useDeleteBoard = () => {
        return useMutation({
            mutationFn: (boardUrl: string) => adminApi.deleteBoard(boardUrl),
            onSuccess: () => {
                // Invalidate both admin boards and general boards list to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: ['admin', 'boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    const useBoardManager = (boardId: Ref<number | null>) => {
        return useQuery({
            queryKey: ['admin', 'board-manager', boardId],
            queryFn: async () => {
                if (!boardId.value) return null
                const { data } = await adminApi.getBoardManager(boardId.value)
                return data?.data ?? null
            },
            enabled: computed(() => boardId.value !== null)
        })
    }

    const useUpdateBoardManager = () => {
        return useMutation({
            mutationFn: ({ boardId, data }: { boardId: number, data: BoardManagerData }) =>
                adminApi.updateBoardManager(boardId, data),
            onSuccess: (_, { boardId }) => {
                queryClient.invalidateQueries({ queryKey: ['admin', 'board-manager', boardId] })
                queryClient.invalidateQueries({ queryKey: ['admin', 'boards'] })
                queryClient.invalidateQueries({ queryKey: ['admin', 'admins'] })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
            }
        })
    }

    // --- Error Log Management ---
    const useErrorLogs = (params: Ref<ErrorLogSearchParams>) => {
        return useQuery({
            queryKey: ['admin', 'error-logs', params],
            queryFn: async () => {
                const { data } = await adminApi.getErrorLogs(params.value)
                return data.data as PageResponse<ErrorLogItem>
            },
            placeholderData: (previousData) => previousData
        })
    }

    const useResolveErrorLog = () => {
        return useMutation({
            mutationFn: ({ errorLogId, data }: { errorLogId: number, data?: { memo?: string } }) => adminApi.resolveErrorLog(errorLogId, data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'error-logs'] })
        })
    }

    const useErrorLogStats = () => {
        return useQuery({
            queryKey: ['admin', 'error-log-stats'],
            queryFn: async () => {
                const { data } = await adminApi.getErrorLogStats()
                return data.data as ErrorLogStats
            }
        })
    }

    return {
        useAdmins,
        useCreateAdmin,
        useUpdateAdminStatus,
        useSuperAdmins,
        useUpdateSuperAdminStatus,
        useUsers,
        useUpdateUserStatus,
        useSanctionUser,
        useReports,
        useResolveReport,
        useIpBlocks,
        useBlockIp,
        useUnblockIp,
        useConfigs,
        useUpdateConfig,
        useCreateConfig,
        useDeleteConfig,
        useDashboardStats,
        useAdminBoards,
        useCreateBoard,
        useUpdateBoard,
        useDeleteBoard,
        useBoardManager,
        useUpdateBoardManager,
        useErrorLogs,
        useResolveErrorLog,
        useErrorLogStats
    }
}
