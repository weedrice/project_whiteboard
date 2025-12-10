import api from '@/api'

export const adminApi = {
    // 관리자 관리
    getAdmins() {
        return api.get('/admin/admins')
    },
    createAdmin(data: any) {
        return api.post('/admin/admins', data)
    },
    deactivateAdmin(adminId: string | number) {
        return api.put(`/admin/admins/${adminId}/deactivate`)
    },
    activateAdmin(adminId: string | number) {
        return api.put(`/admin/admins/${adminId}/activate`)
    },
    getSuperAdmin() {
        return api.get('/admin/super')
    },
    activeSuperAdmin(data: any) {
        return api.put('/admin/super/active', data)
    },
    deactiveSuperAdmin(data: any) {
        return api.put('/admin/super/deactive', data)
    },

    // IP 차단 관리
    getIpBlocks() {
        return api.get('/admin/ip-blocks')
    },
    blockIp(data: any) {
        return api.post('/admin/ip-blocks', data)
    },
    unblockIp(ipAddress: string) {
        return api.delete(`/admin/ip-blocks/${ipAddress}`)
    },

    // 사용자 관리
    getUsers(params: any) {
        return api.get('/admin/users', { params })
    },
    updateUserStatus(userId: string | number, status: string) {
        return api.put(`/admin/users/${userId}/status`, { status })
    },
    sanctionUser(data: any) {
        return api.post('/admin/sanctions', data)
    },

    // 신고 관리
    getReports(params: any) {
        return api.get('/admin/reports', { params })
    },
    resolveReport(reportId: string | number, data: any) {
        return api.put(`/admin/reports/${reportId}`, data)
    },

    // 전역 설정
    getConfigs() {
        return api.get('/admin/configs')
    },

    updateConfig(key: string, value: string, description: string) {
        return api.put(`/admin/configs/${key}`, { value, description })
    },

    createConfig(data: any) {
        return api.post('/admin/configs', data)
    },

    deleteConfig(key: string) {
        return api.delete(`/admin/configs/${key}`)
    },

    // 대시보드 통계
    getDashboardStats() {
        return api.get('/admin/stats')
    },

    // 게시판 관리
    getBoards() {
        return api.get('/boards/all')
    },
    createBoard(data: any) {
        return api.post('/boards', data)
    },
    updateBoard(boardUrl: string, data: any) {
        return api.put(`/boards/${boardUrl}`, data)
    },
    deleteBoard(boardUrl: string) {
        return api.delete(`/boards/${boardUrl}`)
    }
}
