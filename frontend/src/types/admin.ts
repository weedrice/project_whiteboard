// 설정 관련 타입
export interface GlobalConfig {
    key: string
    value: string
    description?: string
}

// IP 차단 관련 타입
export interface IpBlock {
    ipAddress: string
    reason: string
    startDate: string
    endDate?: string | null
    admin: {
        adminId: number
        displayName: string
    }
}

export interface DashboardStats {
    totalUsers: number
    totalPosts: number
    pendingReports: number
    activeUsers: number
    [key: string]: number // Allow other fields
}

export interface DeepDashboardStats {
    days: 30 | 90
    daily: Array<{
        date: string
        signups: number
        posts: number
        comments: number
        reports: number
    }>
    topBoards: Array<{
        boardId: number
        boardName: string
        boardUrl: string
        activityCount: number
    }>
    moderation: {
        pendingReports: number
        resolvedReports: number
        rejectedReports: number
        autoBlinds: number
        managerBlinds: number
    }
}

export interface ModerationAuditLog {
    auditId: number
    actorType: 'USER' | 'SYSTEM' | string
    actorUserId: number | null
    actorDisplayName: string | null
    adminId: number | null
    action: string
    targetType: string
    targetId: number
    boardId: number | null
    boardName: string | null
    boardUrl: string | null
    reason: string | null
    createdAt: string
}

export interface ModerationAuditSearchParams {
    page?: number
    size?: number
    action?: string
    actorType?: string
    boardId?: number
    boardUrl?: string
    boardName?: string
    actorUserId?: number
    actorName?: string
    startDate?: string
    endDate?: string
    sort?: string
}

export interface BoardAdminInfo {
    adminId: number
    role: string
    isActive: boolean
    createdAt: string
    user: {
        userId: number
        loginId: string
        displayName: string
    }
    board: {
        boardId: number
        boardName: string
    } | null
}

export interface SuperAdminInfo {
    userId: number
    loginId: string
    displayName: string
    isSuperAdmin: boolean
    createdAt: string
}

// 에러 로그 관련 타입
export interface ErrorLogListItem {
    errorLogId: number
    errorCode: string | null
    errorType: string
    httpStatus: number
    message: string
    requestUri: string
    requestMethod: string
    userId: number | null
    ipAddress: string
    userAgent: string | null
    isResolved: string
    resolvedBy: number | null
    resolvedAt: string | null
    resolvedMemo: string | null
    createdAt: string
}

export interface ErrorLogDetail extends ErrorLogListItem {
    stackTrace: string | null
}

export interface ErrorLogSearchParams {
    page?: number
    size?: number
    errorType?: string
    errorCode?: string
    httpStatus?: number
    isResolved?: string
    startDate?: string
    endDate?: string
    requestUri?: string
}

export interface ErrorLogStats {
    totalCount: number
    unresolvedCount: number
    resolvedCount: number
    serverErrorCount?: number
    clientErrorCount?: number
}

export interface AdminShopItem {
    itemId: number
    itemName: string
    description: string | null
    price: number
    itemType: string
    targetId: number | null
    imageUrl: string | null
    isActive: boolean
    isSaleEnabled: boolean
    purchasable: boolean
    createdAt: string
    modifiedAt: string
}

export interface AdminShopItemSearchParams {
    page?: number
    size?: number
    q?: string
    itemType?: string
    isActive?: boolean
    isSaleEnabled?: boolean
    sort?: string
}
