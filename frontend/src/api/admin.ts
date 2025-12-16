import api from '@/api'
import type { ApiResponse, PageResponse, User, Report, GlobalConfig, IpBlock, Board, BoardCreateData, BoardUpdateData, SanctionData, DashboardStats } from '@/types'

// Admin types
interface AdminCreateData {
    loginId: string
    boardId?: number
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

interface ReportResolveData {
    status: 'RESOLVED' | 'REJECTED'
}

interface ConfigCreateData {
    key: string
    value: string
    description?: string
}

export const adminApi = {
    // 관리자 관리
    getAdmins() {
        return api.get<ApiResponse<User[]>>('/admin/admins')
    },
    createAdmin(data: AdminCreateData) {
        return api.post<ApiResponse<void>>('/admin/admins', data)
    },
    deactivateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${adminId}/deactivate`)
    },
    activateAdmin(adminId: string | number) {
        return api.put<ApiResponse<void>>(`/admin/admins/${adminId}/activate`)
    },
    getSuperAdmin() {
        return api.get<ApiResponse<User>>('/admin/super')
    },
    activeSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/active', data)
    },
    deactiveSuperAdmin(data: SuperAdminData) {
        return api.put<ApiResponse<void>>('/admin/super/deactive', data)
    },

    // IP 차단 관리
    getIpBlocks() {
        return api.get<ApiResponse<IpBlock[]>>('/admin/ip-blocks')
    },
    blockIp(data: IpBlockData) {
        return api.post<ApiResponse<void>>('/admin/ip-blocks', data)
    },
    unblockIp(ipAddress: string) {
        return api.delete<ApiResponse<void>>(`/admin/ip-blocks/${ipAddress}`)
    },

    // 사용자 관리
    getUsers(params: PaginationParams) {
        return api.get<ApiResponse<PageResponse<User>>>('/admin/users', { params })
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
    }
}

