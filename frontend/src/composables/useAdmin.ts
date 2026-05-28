import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { adminApi, type AdminRole } from '@/api/admin'
import { computed, type Ref } from 'vue'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'
import type {
    SanctionData,
    ApiResponse,
    PageResponse,
    User,
    Report,
    ErrorLogDetail,
    ErrorLogListItem,
    ErrorLogSearchParams,
    ErrorLogStats,
    AdminUserDetail,
    AdminUserPostItem,
    AdminUserCommentItem,
    AdminUserSubscriptionItem,
    AdminBoard,
    BoardAdminInfo,
    SuperAdminInfo,
    IpBlock
} from '@/types'

function unwrapAdminPageResponse<T>(response: ApiResponse<PageResponse<T> | PageResponseRaw<T>>): PageResponse<T> {
    return normalizePageResponse(response.data as PageResponseRaw<T>)
}

// Admin specific types
interface AdminCreateData {
    loginId: string
    boardId: number
    role: AdminRole
}

interface UserSearchParams {
    page?: number
    size?: number
    q?: string
    status?: string
    role?: string
    isEmailVerified?: boolean
    isSuperAdmin?: boolean
    isWithdrawn?: boolean
    createdFrom?: string
    createdTo?: string
    lastLoginFrom?: string
    lastLoginTo?: string
    sort?: string
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
    agentUseYn?: boolean
    guidePrompt?: string
}

interface BoardUpdateData {
    boardName?: string
    boardUrl?: string
    description?: string
    iconUrl?: string
    sortOrder?: number
    allowNsfw?: boolean
    isActive?: boolean
    agentUseYn?: boolean
    guidePrompt?: string
}

interface BoardManagerData {
    loginId: string
}

export function useAdmin() {
    const queryClient = useQueryClient()

    // --- Admin Management ---
    const useAdmins = (params: Ref<{ page?: number, size?: number }>) => {
        return useQuery({
            queryKey: ['admin', 'admins', params],
            queryFn: async () => {
                const { data } = await adminApi.getAdmins(params.value)
                return unwrapAdminPageResponse<BoardAdminInfo>(data)
            },
            placeholderData: (previousData) => previousData
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
                return data.data as SuperAdminInfo[]
            }
        })
    }

    const useUpdateSuperAdminStatus = () => {
        return useMutation({
            mutationFn: ({ loginId, action }: { loginId: string, action: 'activate' | 'deactivate' }) => {
                if (action === 'activate') return adminApi.activeSuperAdmin({ loginId })
                return adminApi.deactivateSuperAdmin({ loginId })
            },
            onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'super'] })
        })
    }

    // --- User Management ---
    const useUsers = (params: Ref<UserSearchParams>, enabled?: Ref<boolean>) => {
        return useQuery({
            queryKey: ['admin', 'users', params],
            queryFn: async () => {
                const { data } = await adminApi.getUsers(params.value)
                return unwrapAdminPageResponse<User>(data)
            },
            enabled,
            placeholderData: (previousData) => previousData
        })
    }

    const useUpdateUserStatus = () => {
        return useMutation({
            mutationFn: ({ userId, status }: { userId: string | number, status: string }) => adminApi.updateUserStatus(userId, status),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
                queryClient.invalidateQueries({ queryKey: ['admin', 'users', 'detail'] })
            }
        })
    }

    const useAdminUserDetail = (userId: Ref<number | null>) => {
        return useQuery({
            queryKey: ['admin', 'users', 'detail', userId],
            queryFn: async () => {
                if (userId.value == null) return null
                const { data } = await adminApi.getUserDetail(userId.value)
                return data?.data as AdminUserDetail
            },
            enabled: computed(() => userId.value !== null)
        })
    }

    const useAdminUserPosts = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        return useQuery({
            queryKey: ['admin', 'users', 'detail', userId, 'posts', params],
            queryFn: async () => {
                if (userId.value == null) return null
                const { data } = await adminApi.getUserPosts(userId.value, params.value)
                return unwrapAdminPageResponse<AdminUserPostItem>(data)
            },
            enabled: computed(() => userId.value !== null),
            placeholderData: (previousData) => previousData
        })
    }

    const useAdminUserComments = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        return useQuery({
            queryKey: ['admin', 'users', 'detail', userId, 'comments', params],
            queryFn: async () => {
                if (userId.value == null) return null
                const { data } = await adminApi.getUserComments(userId.value, params.value)
                return unwrapAdminPageResponse<AdminUserCommentItem>(data)
            },
            enabled: computed(() => userId.value !== null),
            placeholderData: (previousData) => previousData
        })
    }

    const useAdminUserSubscriptions = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        return useQuery({
            queryKey: ['admin', 'users', 'detail', userId, 'subscriptions', params],
            queryFn: async () => {
                if (userId.value == null) return null
                const { data } = await adminApi.getUserSubscriptions(userId.value, params.value)
                return unwrapAdminPageResponse<AdminUserSubscriptionItem>(data)
            },
            enabled: computed(() => userId.value !== null),
            placeholderData: (previousData) => previousData
        })
    }

    const useSanctionUser = () => {
        return useMutation({
            mutationFn: (data: SanctionData) => adminApi.sanctionUser(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
                queryClient.invalidateQueries({ queryKey: ['admin', 'users', 'detail'] })
            }
        })
    }

    // --- Report Management ---
    const useReports = (params: Ref<ReportSearchParams>) => {
        return useQuery({
            queryKey: ['admin', 'reports', params],
            queryFn: async () => {
                const { data } = await adminApi.getReports(params.value)
                return unwrapAdminPageResponse<Report>(data)
            },
            placeholderData: (previousData) => previousData
        })
    }

    const useResolveReport = () => {
        return useMutation({
            mutationFn: ({ reportId, data }: { reportId: string | number, data: ReportResolveData }) => adminApi.resolveReport(reportId, data),
            onSettled: () => queryClient.invalidateQueries({ queryKey: ['admin', 'reports'] })
        })
    }

    // --- IP Block Management ---
    const useIpBlocks = (params: Ref<{ page?: number, size?: number }>) => {
        return useQuery({
            queryKey: ['admin', 'ip-blocks', params],
            queryFn: async () => {
                const { data } = await adminApi.getIpBlocks(params.value)
                return unwrapAdminPageResponse<IpBlock>(data)
            },
            placeholderData: (previousData) => previousData
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
                return data.data as AdminBoard[]
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
        const boardManagerQueryKey = computed(() => ['admin', 'board-manager', boardId.value])

        return useQuery({
            queryKey: boardManagerQueryKey,
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
                return unwrapAdminPageResponse<ErrorLogListItem>(data)
            },
            placeholderData: (previousData) => previousData
        })
    }

    const useErrorLog = () => {
        return useMutation({
            mutationFn: async (errorLogId: number) => {
                const { data } = await adminApi.getErrorLog(errorLogId)
                return data.data as ErrorLogDetail
            }
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
        useAdminUserDetail,
        useAdminUserPosts,
        useAdminUserComments,
        useAdminUserSubscriptions,
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
        useErrorLog,
        useResolveErrorLog,
        useErrorLogStats
    }
}
