import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    PageResponse,
    User,
    Report,
    GlobalConfig,
    IpBlock,
    AdminBoard,
    BoardDetail,
    BoardCreateData,
    BoardUpdateData,
    SanctionData,
    DashboardStats,
    ErrorLogDetail,
    ErrorLogListItem,
    ErrorLogSearchParams,
    ErrorLogStats,
    BoardAdminInfo,
    SuperAdminInfo,
    AdminUserDetail,
    AdminUserPostItem,
    AdminUserCommentItem,
    AdminUserSubscriptionItem,
    AdminInquirySummary,
    Post
} from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

export type AdminRole = 'BOARD_ADMIN' | 'MODERATOR'

const getWithOptionalConfig = <T>(url: string, config?: AxiosRequestConfig) => (
    config ? api.get<T>(url, config) : api.get<T>(url)
)

// Admin types
interface AdminCreateData {
    loginId: string
    boardId: number
    role: AdminRole
}

interface SuperAdminData {
    loginId: string
}

interface IpBlockData {
    ipAddress: string
    reason: string
}

interface PaginationParams {
    page?: number
    size?: number
    q?: string
}

interface UserSearchParams extends PaginationParams {
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

interface ReportResolveData {
    status: 'RESOLVED' | 'REJECTED'
}

interface ConfigCreateData {
    key: string
    value: string
    description?: string
}

interface BoardManagerUpdateData {
    loginId: string
}

export const adminApi = {
    // Admin management
    getAdmins(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<BoardAdminInfo>>>('/admin/admins', { ...config, params })
    },
    createAdmin(data: AdminCreateData) {
        return api.post<ApiResponse<BoardAdminInfo>>('/admin/admins', data)
    },
    deactivateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${encodePathSegment(adminId)}/deactivate`)
    },
    activateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${encodePathSegment(adminId)}/activate`)
    },
    getBoardManager(boardId: number, config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<BoardAdminInfo | null>>(`/admin/boards/${encodePathSegment(boardId)}/manager`, config)
    },
    updateBoardManager(boardId: number, data: BoardManagerUpdateData) {
        return api.put<ApiResponse<BoardAdminInfo>>(`/admin/boards/${encodePathSegment(boardId)}/manager`, data)
    },
    getSuperAdmin(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<SuperAdminInfo[]>>('/admin/super', config)
    },
    activeSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/active', data)
    },
    deactivateSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/deactive', data)
    },

    // IP block management
    getIpBlocks(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<IpBlock>>>('/admin/ip-blocks', { ...config, params })
    },
    blockIp(data: IpBlockData) {
        return api.post<ApiResponse<IpBlock>>('/admin/ip-blocks', data)
    },
    unblockIp(ipAddress: string) {
        return api.delete<ApiResponse<void>>(`/admin/ip-blocks/${encodePathSegment(ipAddress)}`)
    },

    // User management
    getUsers(params: UserSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<User>>>('/admin/users', { ...config, params })
    },
    getUserDetail(userId: string | number, config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<AdminUserDetail>>(`/admin/users/${encodePathSegment(userId)}`, config)
    },
    getUserPosts(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<AdminUserPostItem>>>(`/admin/users/${encodePathSegment(userId)}/posts`, { ...config, params })
    },
    getUserComments(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<AdminUserCommentItem>>>(`/admin/users/${encodePathSegment(userId)}/comments`, { ...config, params })
    },
    getUserSubscriptions(userId: string | number, params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<AdminUserSubscriptionItem>>>(`/admin/users/${encodePathSegment(userId)}/subscriptions`, { ...config, params })
    },
    updateUserStatus(userId: string | number, status: string) {
        return api.put<ApiResponse<void>>(`/admin/users/${encodePathSegment(userId)}/status`, { status })
    },
    sanctionUser(data: SanctionData) {
        return api.post<ApiResponse<number>>('/admin/sanctions', data)
    },

    // Report management
    getReports(params: PaginationParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<Report>>>('/admin/reports', { ...config, params })
    },
    resolveReport(reportId: string | number, data: ReportResolveData) {
        return api.put<ApiResponse<void>>(`/admin/reports/${encodePathSegment(reportId)}`, data)
    },

    // Global settings
    getConfigs(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<GlobalConfig[]>>('/admin/configs', config)
    },

    updateConfig(key: string, value: string, description?: string) {
        return api.put<ApiResponse<GlobalConfig>>(`/admin/configs/${encodePathSegment(key)}`, { value, description })
    },

    createConfig(data: ConfigCreateData) {
        return api.post<ApiResponse<GlobalConfig>>('/admin/configs', data)
    },

    deleteConfig(key: string) {
        return api.delete<ApiResponse<void>>(`/admin/configs/${encodePathSegment(key)}`)
    },

    // Dashboard statistics
    getDashboardStats(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<DashboardStats>>('/admin/stats', config)
    },
    getInquiryPosts(params: PaginationParams & { sort?: string }, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<AdminInquirySummary>>>('/admin/inquiries', { ...config, params })
    },
    getInquiryPost(postId: string | number, config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<Post>>(`/admin/inquiries/${encodePathSegment(postId)}`, config)
            : api.get<ApiResponse<Post>>(`/admin/inquiries/${encodePathSegment(postId)}`)
    },

    // Board management
    getBoards(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<AdminBoard[]>>('/boards/all', config)
    },
    createBoard(data: BoardCreateData) {
        return api.post<ApiResponse<BoardDetail>>('/boards', data)
    },
    updateBoard(boardUrl: string, data: BoardUpdateData) {
        return api.put<ApiResponse<BoardDetail>>(`/boards/${encodePathSegment(boardUrl)}`, data)
    },
    deleteBoard(boardUrl: string) {
        return api.delete<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}`)
    },

    // Error log management
    getErrorLogs(params: ErrorLogSearchParams, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<ErrorLogListItem>>>('/admin/error-logs', { ...config, params })
    },
    getErrorLog(errorLogId: number, config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<ErrorLogDetail>>(`/admin/error-logs/${encodePathSegment(errorLogId)}`, config)
    },
    resolveErrorLog(errorLogId: number, data?: { memo?: string }) {
        return api.put<ApiResponse<void>>(`/admin/error-logs/${encodePathSegment(errorLogId)}/resolve`, data)
    },
    getErrorLogStats(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<ErrorLogStats>>('/admin/error-logs/stats', config)
    }
}

