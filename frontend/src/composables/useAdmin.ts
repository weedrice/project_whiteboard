import { useQuery, useMutation, useQueryClient, type QueryKey } from '@tanstack/vue-query'
import { adminApi, type AdminRole } from '@/api/admin'
import { computed, type ComputedRef, type Ref } from 'vue'
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

type AdminPageApiResponse<T> = ApiResponse<PageResponse<T> | PageResponseRaw<T>>
type AdminPageFetcher<T> = () => Promise<{ data: AdminPageApiResponse<T> }>
type AdminPageQueryOptions = {
    enabled?: Ref<boolean> | ComputedRef<boolean>
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

const adminQueryKeys = {
    adminsRoot: ['admin', 'admins'] as const,
    admins: (params: Ref<{ page?: number, size?: number }>) => ['admin', 'admins', params] as const,
    superAdmins: ['admin', 'super'] as const,
    usersRoot: ['admin', 'users'] as const,
    users: (params: Ref<UserSearchParams>) => ['admin', 'users', params] as const,
    userDetailRoot: ['admin', 'users', 'detail'] as const,
    userDetail: (userId: Ref<number | null>) => ['admin', 'users', 'detail', userId] as const,
    userPosts: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) =>
        ['admin', 'users', 'detail', userId, 'posts', params] as const,
    userComments: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) =>
        ['admin', 'users', 'detail', userId, 'comments', params] as const,
    userSubscriptions: (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) =>
        ['admin', 'users', 'detail', userId, 'subscriptions', params] as const,
    reportsRoot: ['admin', 'reports'] as const,
    reports: (params: Ref<ReportSearchParams>) => ['admin', 'reports', params] as const,
    ipBlocksRoot: ['admin', 'ip-blocks'] as const,
    ipBlocks: (params: Ref<{ page?: number, size?: number }>) => ['admin', 'ip-blocks', params] as const,
    configs: ['admin', 'configs'] as const,
    stats: ['admin', 'stats'] as const,
    boards: ['admin', 'boards'] as const,
    boardManager: (boardId: Ref<number | null>) => computed(() => ['admin', 'board-manager', boardId.value] as const),
    boardManagerById: (boardId: number) => ['admin', 'board-manager', boardId] as const,
    errorLogsRoot: ['admin', 'error-logs'] as const,
    errorLogs: (params: Ref<ErrorLogSearchParams>) => ['admin', 'error-logs', params] as const,
    errorLogStats: ['admin', 'error-log-stats'] as const,
}

export function useAdmin() {
    const queryClient = useQueryClient()

    const adminPageQuery = <T>(
        queryKey: QueryKey,
        fetcher: AdminPageFetcher<T>,
        options: AdminPageQueryOptions = {}
    ) => useQuery({
        queryKey,
        queryFn: async () => {
            const { data } = await fetcher()
            return unwrapAdminPageResponse<T>(data)
        },
        enabled: options.enabled,
        placeholderData: (previousData) => previousData
    })

    const adminNullablePageQuery = <T>(
        queryKey: QueryKey,
        fetcher: () => ReturnType<AdminPageFetcher<T>> | null,
        enabled: Ref<boolean> | ComputedRef<boolean>
    ) => useQuery({
        queryKey,
        queryFn: async () => {
            const response = await fetcher()
            return response === null ? null : unwrapAdminPageResponse<T>(response.data)
        },
        enabled,
        placeholderData: (previousData) => previousData
    })

    // --- Admin Management ---
    const useAdmins = (params: Ref<{ page?: number, size?: number }>) => {
        return adminPageQuery<BoardAdminInfo>(
            adminQueryKeys.admins(params),
            () => adminApi.getAdmins(params.value)
        )
    }

    const useCreateAdmin = () => {
        return useMutation({
            mutationFn: (data: AdminCreateData) => adminApi.createAdmin(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.adminsRoot })
        })
    }

    const useUpdateAdminStatus = () => {
        return useMutation({
            mutationFn: ({ adminId, action }: { adminId: string | number, action: 'activate' | 'deactivate' }) => {
                if (action === 'activate') return adminApi.activateAdmin(adminId)
                return adminApi.deactivateAdmin(adminId)
            },
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.adminsRoot })
        })
    }

    const useSuperAdmins = () => {
        return useQuery({
            queryKey: adminQueryKeys.superAdmins,
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
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.superAdmins })
        })
    }

    // --- User Management ---
    const useUsers = (params: Ref<UserSearchParams>, enabled?: Ref<boolean>) => {
        return adminPageQuery<User>(
            adminQueryKeys.users(params),
            () => adminApi.getUsers(params.value),
            {
                enabled,
            }
        )
    }

    const useUpdateUserStatus = () => {
        return useMutation({
            mutationFn: ({ userId, status }: { userId: string | number, status: string }) => adminApi.updateUserStatus(userId, status),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.usersRoot })
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.userDetailRoot })
            }
        })
    }

    const useAdminUserDetail = (userId: Ref<number | null>) => {
        return useQuery({
            queryKey: adminQueryKeys.userDetail(userId),
            queryFn: async () => {
                if (userId.value == null) return null
                const { data } = await adminApi.getUserDetail(userId.value)
                return data?.data as AdminUserDetail
            },
            enabled: computed(() => userId.value !== null)
        })
    }

    const useAdminUserPosts = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return adminNullablePageQuery<AdminUserPostItem>(
            adminQueryKeys.userPosts(userId, params),
            () => userId.value == null ? null : adminApi.getUserPosts(userId.value, params.value),
            enabled
        )
    }

    const useAdminUserComments = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return adminNullablePageQuery<AdminUserCommentItem>(
            adminQueryKeys.userComments(userId, params),
            () => userId.value == null ? null : adminApi.getUserComments(userId.value, params.value),
            enabled
        )
    }

    const useAdminUserSubscriptions = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return adminNullablePageQuery<AdminUserSubscriptionItem>(
            adminQueryKeys.userSubscriptions(userId, params),
            () => userId.value == null ? null : adminApi.getUserSubscriptions(userId.value, params.value),
            enabled
        )
    }

    const useSanctionUser = () => {
        return useMutation({
            mutationFn: (data: SanctionData) => adminApi.sanctionUser(data),
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.usersRoot })
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.userDetailRoot })
            }
        })
    }

    // --- Report Management ---
    const useReports = (params: Ref<ReportSearchParams>) => {
        return adminPageQuery<Report>(
            adminQueryKeys.reports(params),
            () => adminApi.getReports(params.value)
        )
    }

    const useResolveReport = () => {
        return useMutation({
            mutationFn: ({ reportId, data }: { reportId: string | number, data: ReportResolveData }) => adminApi.resolveReport(reportId, data),
            onSettled: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.reportsRoot })
        })
    }

    // --- IP Block Management ---
    const useIpBlocks = (params: Ref<{ page?: number, size?: number }>) => {
        return adminPageQuery<IpBlock>(
            adminQueryKeys.ipBlocks(params),
            () => adminApi.getIpBlocks(params.value)
        )
    }

    const useBlockIp = () => {
        return useMutation({
            mutationFn: (data: IpBlockData) => adminApi.blockIp(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.ipBlocksRoot })
        })
    }

    const useUnblockIp = () => {
        return useMutation({
            mutationFn: (ipAddress: string) => adminApi.unblockIp(ipAddress),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.ipBlocksRoot })
        })
    }

    // --- Config Management ---
    const useConfigs = () => {
        return useQuery({
            queryKey: adminQueryKeys.configs,
            queryFn: async () => {
                const { data } = await adminApi.getConfigs()
                return data.data
            }
        })
    }

    const useUpdateConfig = () => {
        return useMutation({
            mutationFn: ({ key, value, description }: { key: string, value: string, description?: string }) => adminApi.updateConfig(key, value, description),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.configs })
        })
    }

    const useCreateConfig = () => {
        return useMutation({
            mutationFn: (data: ConfigCreateData) => adminApi.createConfig(data),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.configs })
        })
    }

    const useDeleteConfig = () => {
        return useMutation({
            mutationFn: (key: string) => adminApi.deleteConfig(key),
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.configs })
        })
    }

    // --- Dashboard Stats ---
    const useDashboardStats = () => {
        return useQuery({
            queryKey: adminQueryKeys.stats,
            queryFn: async () => {
                const { data } = await adminApi.getDashboardStats()
                return data.data
            }
        })
    }

    // --- Board Management (Admin) ---
    const useAdminBoards = () => {
        return useQuery({
            queryKey: adminQueryKeys.boards,
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
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
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
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
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
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
                queryClient.invalidateQueries({ queryKey: ['boards', 'subscriptions'] })
            }
        })
    }

    const useBoardManager = (boardId: Ref<number | null>) => {
        const boardManagerQueryKey = adminQueryKeys.boardManager(boardId)

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
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boardManagerById(boardId) })
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.adminsRoot })
                queryClient.invalidateQueries({ queryKey: ['boards'] })
            }
        })
    }

    // --- Error Log Management ---
    const useErrorLogs = (params: Ref<ErrorLogSearchParams>) => {
        return adminPageQuery<ErrorLogListItem>(
            adminQueryKeys.errorLogs(params),
            () => adminApi.getErrorLogs(params.value)
        )
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
            onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.errorLogsRoot })
        })
    }

    const useErrorLogStats = () => {
        return useQuery({
            queryKey: adminQueryKeys.errorLogStats,
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
