// 신고 관련 타입
export interface Report {
    reportId: number
    reporterDisplayName: string
    targetType: 'POST' | 'COMMENT' | 'USER'
    targetId: number
    reason: string
    status: 'PENDING' | 'RESOLVED' | 'REJECTED'
    createdAt: string
}
