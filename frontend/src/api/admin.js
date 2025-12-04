import api from '@/api'

export const adminApi = {
    // 관리자 관리
    getAdmins() {
        return api.get('/admin/admins')
    },
    createAdmin(data) {
        return api.post('/admin/admins', data)
    },
    deactivateAdmin(adminId) {
        return api.put(`/admin/admins/${adminId}/deactivate`)
    },
    activateAdmin(adminId) {
        return api.put(`/admin/admins/${adminId}/activate`)
    },
    getSuperAdmin() {
        return api.get('/admin/super')
    },
    activeSuperAdmin(data) {
        return api.put('/admin/super/active', data)
    },
    deactiveSuperAdmin(data) {
        return api.put('/admin/super/deactive', data)
    },

    // IP 차단 관리
    getIpBlocks() {
        return api.get('/admin/ip-blocks')
    },
    blockIp(data) {
        return api.post('/admin/ip-blocks', data)
    },
    unblockIp(ipAddress) {
        return api.delete(`/admin/ip-blocks/${ipAddress}`)
    },

    // 사용자 관리
    getUsers(params) {
        return api.get('/admin/users', { params })
    },
    updateUserStatus(userId, status) {
        return api.put(`/admin/users/${userId}/status`, { status })
    },
    sanctionUser(data) {
        return api.post('/admin/sanctions', data)
    },

    // 신고 관리
    getReports(params) {
        return api.get('/admin/reports', { params })
    },
    resolveReport(reportId, data) {
        return api.put(`/admin/reports/${reportId}`, data)
    },

    // 전역 설정
    getConfigs() {
        return api.get('/admin/configs')
    },
    updateConfig(key, value) {
        return api.put('/admin/configs', { key, value })
    },

    // 대시보드 통계
    getDashboardStats() {
        return api.get('/admin/stats')
    },

    // 게시판 관리
    getBoards() {
        return api.get('/boards/all')
    },
    createBoard(data) {
        return api.post('/boards', data)
    },
    updateBoard(boardUrl, data) {
        return api.put(`/boards/${boardUrl}`, data)
    },
    deleteBoard(boardUrl) {
        return api.delete(`/boards/${boardUrl}`)
    }
}
