// 신고 관련 타입 (API ReportResponse와 동일)
export interface Report {
    reportId: number
    reporterId?: number
    reporterDisplayName: string
    targetType: 'POST' | 'COMMENT' | 'USER'
    targetId: number
    targetUserId?: number | null
    /** 신고 사유 유형 (SPAM, ABUSE, ADULT 등) */
    reasonType: string
    /** Report creation remark, such as a legacy user report link. */
    remark?: string | null
    /** Admin processing note. */
    processedRemark?: string | null
    status: 'PENDING' | 'RESOLVED' | 'REJECTED'
    /** 신고 상세 내용 (사용자 입력) */
    contents?: string | null
    /** 대상 표시명 (USER 대상의 닉네임) */
    targetDisplayName?: string | null
    /** 대상 로그인 ID (USER 대상의 로그인 ID 표시용) */
    targetLoginId?: string | null
    createdAt: string
    updatedAt?: string
    adminId?: number | null
    processorUserId?: number | null
}

export interface MyReport {
    reportId: number
    targetType: 'POST' | 'COMMENT' | 'USER'
    reasonType: string
    status: 'PENDING' | 'RESOLVED' | 'REJECTED'
    contents?: string | null
    targetDisplayName?: string | null
    createdAt: string
    updatedAt?: string
}
