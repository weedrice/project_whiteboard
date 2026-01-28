// 신고 관련 타입 (API ReportResponse와 동일)
export interface Report {
    reportId: number
    reporterId?: number
    reporterDisplayName: string
    targetType: 'POST' | 'COMMENT' | 'USER'
    targetId: number
    /** 신고 사유 유형 (SPAM, ABUSE, ADULT 등) */
    reasonType: string
    /** 관리자 처리 비고 */
    remark?: string | null
    status: 'PENDING' | 'RESOLVED' | 'REJECTED'
    /** 신고 상세 내용(사용자 입력) */
    contents?: string | null
    /** 대상 표시명 (USER일 때 닉네임) */
    targetDisplayName?: string | null
    /** 대상 로그인 ID (USER일 때, 닉네임/ID 표기용) */
    targetLoginId?: string | null
    createdAt: string
    updatedAt?: string
    adminId?: number | null
}
