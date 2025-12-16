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
