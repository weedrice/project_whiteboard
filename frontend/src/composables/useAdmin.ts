import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { adminApi, type AdminRole } from '@/api/admin'
import { unwrapAxiosApiData } from '@/api/response'
import { adminQueryKeys } from '@/composables/adminQueryKeys'
import { boardQueryKeys } from '@/composables/boardQueryKeys'
import { invalidateAdminBoardCaches, invalidateAdminUserCaches } from '@/composables/adminCacheInvalidation'
import { invalidateBoardListCaches } from '@/composables/boardCacheInvalidation'
import {
    useAdminDataQuery,
    useAdminNullableDataQuery,
    useAdminNullablePageQuery,
    useAdminPageQuery,
} from '@/composables/adminApiQuery'
import { computed, type Ref } from 'vue'
import type {
    SanctionData,
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
        return useAdminPageQuery<BoardAdminInfo>(
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
        return useAdminDataQuery<SuperAdminInfo[]>(adminQueryKeys.superAdmins, () => adminApi.getSuperAdmin())
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
        return useAdminPageQuery<User>(
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
                invalidateAdminUserCaches(queryClient)
            }
        })
    }

    const useAdminUserDetail = (userId: Ref<number | null>) => {
        const enabled = computed(() => userId.value !== null)
        return useAdminNullableDataQuery<AdminUserDetail>(
            adminQueryKeys.userDetail(userId),
            () => userId.value == null ? null : adminApi.getUserDetail(userId.value),
            enabled
        )
    }

    const useAdminUserPosts = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserPostItem>(
            adminQueryKeys.userPosts(userId, params),
            () => userId.value == null ? null : adminApi.getUserPosts(userId.value, params.value),
            enabled
        )
    }

    const useAdminUserComments = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserCommentItem>(
            adminQueryKeys.userComments(userId, params),
            () => userId.value == null ? null : adminApi.getUserComments(userId.value, params.value),
            enabled
        )
    }

    const useAdminUserSubscriptions = (userId: Ref<number | null>, params: Ref<{ page?: number, size?: number }>) => {
        const enabled = computed(() => userId.value !== null)

        return useAdminNullablePageQuery<AdminUserSubscriptionItem>(
            adminQueryKeys.userSubscriptions(userId, params),
            () => userId.value == null ? null : adminApi.getUserSubscriptions(userId.value, params.value),
            enabled
        )
    }

    const useSanctionUser = () => {
        return useMutation({
            mutationFn: (data: SanctionData) => adminApi.sanctionUser(data),
            onSuccess: () => {
                invalidateAdminUserCaches(queryClient)
            }
        })
    }

    // --- Report Management ---
    const useReports = (params: Ref<ReportSearchParams>) => {
        return useAdminPageQuery<Report>(
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
        return useAdminPageQuery<IpBlock>(
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
        return useAdminDataQuery(adminQueryKeys.configs, () => adminApi.getConfigs())
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
        return useAdminDataQuery(adminQueryKeys.stats, () => adminApi.getDashboardStats())
    }

    // --- Board Management (Admin) ---
    const useAdminBoards = () => {
        return useAdminDataQuery<AdminBoard[]>(adminQueryKeys.boards, () => adminApi.getBoards())
    }

    const useCreateBoard = () => {
        return useMutation({
            mutationFn: (data: BoardCreateData) => adminApi.createBoard(data),
            onSuccess: () => {
                // Invalidate both admin boards and general boards list to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useUpdateBoard = () => {
        return useMutation({
            mutationFn: ({ boardUrl, data }: { boardUrl: string, data: BoardUpdateData }) => adminApi.updateBoard(boardUrl, data),
            onSuccess: (_, { boardUrl, data }) => {
                // Invalidate both admin boards and general boards list to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(boardUrl) })
                if (data.boardUrl && data.boardUrl !== boardUrl) {
                    queryClient.invalidateQueries({ queryKey: boardQueryKeys.detail(data.boardUrl) })
                }
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useDeleteBoard = () => {
        return useMutation({
            mutationFn: (boardUrl: string) => adminApi.deleteBoard(boardUrl),
            onSuccess: () => {
                // Invalidate both admin boards and general boards list to refresh header dropdowns
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boards })
                invalidateBoardListCaches(queryClient)
            }
        })
    }

    const useBoardManager = (boardId: Ref<number | null>) => {
        const boardManagerQueryKey = adminQueryKeys.boardManager(boardId)
        const enabled = computed(() => boardId.value !== null)

        return useAdminNullableDataQuery<BoardAdminInfo | null>(
            boardManagerQueryKey,
            () => !boardId.value ? null : adminApi.getBoardManager(boardId.value),
            enabled
        )
    }

    const useUpdateBoardManager = () => {
        return useMutation({
            mutationFn: ({ boardId, data }: { boardId: number, data: BoardManagerData }) =>
                adminApi.updateBoardManager(boardId, data),
            onSuccess: (_, { boardId }) => {
                queryClient.invalidateQueries({ queryKey: adminQueryKeys.boardManagerById(boardId) })
                invalidateAdminBoardCaches(queryClient)
                queryClient.invalidateQueries({ queryKey: boardQueryKeys.all })
            }
        })
    }

    // --- Error Log Management ---
    const useErrorLogs = (params: Ref<ErrorLogSearchParams>) => {
        return useAdminPageQuery<ErrorLogListItem>(
            adminQueryKeys.errorLogs(params),
            () => adminApi.getErrorLogs(params.value)
        )
    }

    const useErrorLog = () => {
        return useMutation({
            mutationFn: async (errorLogId: number) => {
                return unwrapAxiosApiData(await adminApi.getErrorLog(errorLogId)) as ErrorLogDetail
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
        return useAdminDataQuery<ErrorLogStats>(adminQueryKeys.errorLogStats, () => adminApi.getErrorLogStats())
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
