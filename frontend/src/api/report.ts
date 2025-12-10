import api from './index'

export const reportApi = {
    reportUser: (targetUserId: string | number, reason: string, link: string) => api.post(`/reports/users`, { targetUserId, reason, link }),
    reportPost: (targetPostId: string | number, reason: string) => api.post(`/reports/posts`, { targetPostId, reason }),
    reportComment: (targetCommentId: string | number, reason: string) => api.post(`/reports/comments`, { targetCommentId, reason }),
    getMyReports: (params: any) => api.get('/reports/me', { params }),
}
