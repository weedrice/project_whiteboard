import api from '@/api'
import type {
    ApiResponse,
    PageResponse,
    User,
    Report,
    GlobalConfig,
    IpBlock,
    Board,
    BoardCreateData,
    BoardUpdateData,
    SanctionData,
    DashboardStats,
    ErrorLogItem,
    ErrorLogSearchParams,
    ErrorLogStats,
    BoardAdminInfo,
    SuperAdminInfo,
    AdminUserDetail,
    Post,
    PostSummary,
    MyComment
} from '@/types'

export type AdminRole = 'BOARD_ADMIN' | 'MODERATOR'

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
    // 관리자 관리
    getAdmins(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<BoardAdminInfo>>>('/admin/admins', { params })
    },
    createAdmin(data: AdminCreateData) {
        return api.post<ApiResponse<BoardAdminInfo>>('/admin/admins', data)
    },
    deactivateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${adminId}/deactivate`)
    },
    activateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${adminId}/activate`)
    },
    getBoardManager(boardId: number) {
        return api.get<ApiResponse<BoardAdminInfo | null>>(`/admin/boards/${boardId}/manager`)
    },
    updateBoardManager(boardId: number, data: BoardManagerUpdateData) {
        return api.put<ApiResponse<BoardAdminInfo>>(`/admin/boards/${boardId}/manager`, data)
    },
    getSuperAdmin() {
        return api.get<ApiResponse<SuperAdminInfo[]>>('/admin/super')
    },
    activeSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/active', data)
    },
    deactivateSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/deactive', data)
    },

    // IP 차단 관리
    getIpBlocks(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<IpBlock>>>('/admin/ip-blocks', { params })
    },
    blockIp(data: IpBlockData) {
        return api.post<ApiResponse<IpBlock>>('/admin/ip-blocks', data)
    },
    unblockIp(ipAddress: string) {
        return api.delete<ApiResponse<void>>(`/admin/ip-blocks/${ipAddress}`)
    },

    // 사용자 관리
    getUsers(params: UserSearchParams) {
        return api.get<ApiResponse<PageResponse<User>>>('/admin/users', { params })
    },
    getUserDetail(userId: string | number) {
        return api.get<ApiResponse<AdminUserDetail>>(`/admin/users/${userId}`)
    },
    getUserPosts(userId: string | number, params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>(`/admin/users/${userId}/posts`, { params })
    },
    getUserComments(userId: string | number, params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<MyComment>>>(`/admin/users/${userId}/comments`, { params })
    },
    getUserSubscriptions(userId: string | number, params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<Board>>>(`/admin/users/${userId}/subscriptions`, { params })
    },
    updateUserStatus(userId: string | number, status: string) {
        return api.put<ApiResponse<void>>(`/admin/users/${userId}/status`, { status })
    },
    sanctionUser(data: SanctionData) {
        return api.post<ApiResponse<void>>('/admin/sanctions', data)
    },

    // 신고 관리
    getReports(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<Report>>>('/admin/reports', { params })
    },
    resolveReport(reportId: string | number, data: ReportResolveData) {
        return api.put<ApiResponse<void>>(`/admin/reports/${reportId}`, data)
    },

    // 전역 설정
    getConfigs() {
        return api.get<ApiResponse<GlobalConfig[]>>('/admin/configs')
    },

    updateConfig(key: string, value: string, description: string) {
        return api.put<ApiResponse<GlobalConfig>>(`/admin/configs/${key}`, { value, description })
    },

    createConfig(data: ConfigCreateData) {
        return api.post<ApiResponse<GlobalConfig>>('/admin/configs', data)
    },

    deleteConfig(key: string) {
        return api.delete<ApiResponse<void>>(`/admin/configs/${key}`)
    },

    // 대시보드 통계
    getDashboardStats() {
        return api.get<ApiResponse<DashboardStats>>('/admin/stats')
    },
    getInquiryPosts(params: PaginationParams & { sort?: string }) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>('/admin/inquiries', { params })
    },
    getInquiryPost(postId: string | number) {
        return api.get<ApiResponse<Post>>(`/admin/inquiries/${postId}`)
    },

    // 게시판 관리
    getBoards() {
        return api.get<ApiResponse<Board[]>>('/boards/all')
    },
    createBoard(data: BoardCreateData) {
        return api.post<ApiResponse<Board>>('/boards', data)
    },
    updateBoard(boardUrl: string, data: BoardUpdateData) {
        return api.put<ApiResponse<Board>>(`/boards/${boardUrl}`, data)
    },
    deleteBoard(boardUrl: string) {
        return api.delete<ApiResponse<void>>(`/boards/${boardUrl}`)
    },

    // 에러 로그 관리
    getErrorLogs(params: ErrorLogSearchParams) {
        return api.get<ApiResponse<PageResponse<ErrorLogItem>>>('/admin/error-logs', { params })
    },
    getErrorLog(errorLogId: number) {
        return api.get<ApiResponse<ErrorLogItem>>(`/admin/error-logs/${errorLogId}`)
    },
    resolveErrorLog(errorLogId: number, data?: { memo?: string }) {
        return api.put<ApiResponse<void>>(`/admin/error-logs/${errorLogId}/resolve`, data)
    },
    getErrorLogStats() {
        return api.get<ApiResponse<ErrorLogStats>>('/admin/error-logs/stats')
    }
}

