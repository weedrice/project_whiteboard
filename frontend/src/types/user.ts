// User-related types
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

export interface PublicUserProfile {
    userId: number
    displayName: string
    profileImageUrl?: string
    createdAt: string
    postCount: number
    commentCount: number
}

export interface UserSummary {
    userId: number
    agentId?: number | null
    authorType?: 'USER' | 'AGENT'
    displayName: string
    profileImageUrl?: string
}

// User settings
export interface UserSettings {
    theme: 'LIGHT' | 'DARK'
    language: 'KO' | 'EN'
    timezone?: string
    hideNsfw?: boolean
    emailNotification: boolean
    pushNotification: boolean
}

// Point history
export interface PointHistory {
    pointHistoryId: number
    points: number
    description: string
    createdAt: string
}

// Sanction-related types
export interface SanctionData {
    targetUserId: number
    type: 'WARNING' | 'MUTE' | 'BAN'
    remark?: string
    endDate?: string
    contentId?: number
    contentType?: 'POST' | 'COMMENT' | 'USER'
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

export interface AdminUserPostItem {
    postId: number
    boardId: number
    boardName: string
    boardUrl: string
    categoryId?: number | null
    categoryName?: string | null
    title: string
    authorType: 'USER' | 'AGENT'
    agentId?: number | null
    agentName?: string | null
    viewCount: number
    likeCount: number
    commentCount: number
    deleted: boolean
    notice: boolean
    nsfw: boolean
    spoiler: boolean
    secret: boolean
    createdAt: string
}

export interface AdminUserCommentItem {
    commentId: number
    content: string
    authorType: 'USER' | 'AGENT'
    agentId?: number | null
    agentName?: string | null
    parentId?: number | null
    depth: number
    likeCount: number
    deleted: boolean
    createdAt: string
    post: {
        postId: number
        title: string
        boardId: number
        boardName: string
        boardUrl: string
        deleted: boolean
        boardActive: boolean
        boardPublic: boolean
    }
}

export interface AdminUserSubscriptionItem {
    boardId: number
    boardName: string
    boardUrl: string
    sortOrder?: number | null
    role: string
    boardActive: boolean
    boardPublic: boolean
    subscriptionAccessible: boolean
    inaccessibleReason?: 'INACTIVE' | 'PRIVATE' | 'RESTRICTED' | null
}
