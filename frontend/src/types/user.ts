// 사용자 관련 타입
export interface User {
    userId: number
    loginId: string
    displayName: string
    email: string
    role?: 'USER' | 'ADMIN' | 'SUPER_ADMIN' | 'BOARD_ADMIN' | 'MODERATOR'
    isSuperAdmin?: boolean
    status: 'ACTIVE' | 'INACTIVE' | 'SANCTIONED' | 'SUSPENDED' | 'DELETED'
    bio?: string
    profileImageUrl?: string
    theme?: 'LIGHT' | 'DARK'
    isEmailVerified?: boolean
    createdAt: string
    modifiedAt?: string
    lastLoginAt?: string
    points?: number
}

export interface UserSummary {
    userId: number
    displayName: string
    profileImageUrl?: string
}

// 사용자 설정 타입
export interface UserSettings {
    theme: 'LIGHT' | 'DARK'
    language: 'KO' | 'EN'
    timezone?: string
    hideNsfw?: boolean
    emailNotification: boolean
    pushNotification: boolean
}

// 포인트 내역 타입
export interface PointHistory {
    pointHistoryId: number
    points: number
    description: string
    createdAt: string
}

// 제재 관련 타입
export interface SanctionData {
    userId: number
    type: 'BAN' | 'MUTE'
    reason: string
}

export interface AdminUserRecentLogin {
    ipAddress: string
    userAgent?: string
    loggedAt: string
}

export interface AdminUserSanctionSummary {
    count: number
    recentType?: string
    recentRemark?: string
    recentStartDate?: string
    recentEndDate?: string
}

export interface AdminUserReportSummary {
    totalCount: number
    pendingCount: number
}

export interface AdminUserDetail extends User {
    deletedAt?: string
    postCount: number
    commentCount: number
    subscriptionCount: number
    recentLogin?: AdminUserRecentLogin
    sanctionSummary?: AdminUserSanctionSummary
    reportSummary?: AdminUserReportSummary
}
