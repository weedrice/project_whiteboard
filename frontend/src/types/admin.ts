// 설정 관련 타입
export interface GlobalConfig {
    configKey: string
    configValue: string
    description?: string
    createdAt: string
    modifiedAt?: string
}

// IP 차단 관련 타입
export interface IpBlock {
    ipAddress: string
    reason: string
    adminId: number
    createdAt: string
}

export interface DashboardStats {
    totalUsers: number
    totalPosts: number
    totalComments: number
    totalReports: number
    [key: string]: number // Allow other fields
}

// 에러 로그 관련 타입
export interface ErrorLogItem {
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
    stackTrace: string | null
    isResolved: string
    resolvedBy: number | null
    resolvedAt: string | null
    resolvedMemo: string | null
    createdAt: string
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
