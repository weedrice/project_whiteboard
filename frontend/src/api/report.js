import api from './index'

export const reportApi = {
    reportUser: (targetUserId, reason, link) => api.post(`/reports/users`, { targetUserId, reason, link }),
    reportPost: (targetPostId, reason) => api.post(`/reports/posts`, { targetPostId, reason }),
    reportComment: (targetCommentId, reason) => api.post(`/reports/comments`, { targetCommentId, reason }),
    getMyReports: (params) => api.get('/reports/me', { params }),
}
