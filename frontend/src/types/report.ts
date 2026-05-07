// ?좉퀬 愿?????(API ReportResponse? ?숈씪)
export interface Report {
    reportId: number
    reporterId?: number
    reporterDisplayName: string
    targetType: 'POST' | 'COMMENT' | 'USER'
    targetId: number
    targetUserId?: number | null
    /** ?좉퀬 ?ъ쑀 ?좏삎 (SPAM, ABUSE, ADULT ?? */
    reasonType: string
    /** Report creation remark, such as a legacy user report link. */
    remark?: string | null
    /** Admin processing note. */
    processedRemark?: string | null
    status: 'PENDING' | 'RESOLVED' | 'REJECTED'
    /** ?좉퀬 ?곸꽭 ?댁슜(?ъ슜???낅젰) */
    contents?: string | null
    /** ????쒖떆紐?(USER?????됰꽕?? */
    targetDisplayName?: string | null
    /** ???濡쒓렇??ID (USER???? ?됰꽕??ID ?쒓린?? */
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
