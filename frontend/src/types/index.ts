// API 공통 응답 타입
export interface ApiResponse<T> {
    success: boolean
    data: T
    error?: {
        code: string
        message: string
    }
}

// 페이지네이션 응답
export interface PageResponse<T> {
    content: T[]
    totalElements: number
    totalPages: number
    size: number
    number: number
    first: boolean
    last: boolean
    empty: boolean
}

// 사용자 관련 타입
export interface User {
    userId: number
    loginId: string
    displayName: string
    email: string
    role: 'USER' | 'ADMIN' | 'SUPER_ADMIN' | 'BOARD_ADMIN'
    status: 'ACTIVE' | 'INACTIVE' | 'SANCTIONED' | 'DELETED'
    bio?: string
    profileImageUrl?: string
    theme?: 'LIGHT' | 'DARK'
    createdAt: string
    modifiedAt?: string
}

export interface UserSummary {
    userId: number
    displayName: string
    profileImageUrl?: string
}

// 게시판 관련 타입
export interface Board {
    boardId: number
    boardName: string
    boardUrl: string
    description?: string
    iconUrl?: string
    sortOrder: number
    isActive: boolean
    allowNsfw: boolean
    subscriberCount?: number
    isAdmin?: boolean
    isSubscribed?: boolean
    createdAt: string
    modifiedAt?: string
}

export interface Category {
    categoryId: number
    name: string
    sortOrder: number
    isActive: boolean
    minWriteRole: string
}

// 게시글 관련 타입
export interface Post {
    postId: number
    title: string
    contents: string
    viewCount: number
    likeCount: number
    commentCount: number
    isNotice: boolean
    isNsfw: boolean
    isSpoiler: boolean
    author: UserSummary
    board: {
        boardId: number
        boardName: string
        boardUrl: string
        iconUrl?: string
    }
    category?: Category
    tags?: string[]
    liked?: boolean
    scrapped?: boolean
    createdAt: string
    modifiedAt?: string
}

export interface PostSummary {
    postId: number
    title: string
    viewCount: number
    likeCount: number
    commentCount: number
    isNotice: boolean
    isNsfw: boolean
    isSpoiler: boolean
    author: UserSummary
    category?: Category
    thumbnailUrl?: string
    createdAt: string
}

// 댓글 관련 타입
export interface Comment {
    commentId: number
    content: string
    author: UserSummary
    parentId?: number
    likeCount: number
    liked?: boolean
    isDeleted: boolean
    children?: Comment[]
    createdAt: string
    modifiedAt?: string
}

export interface CommentPayload {
    content: string
    parentId?: number | null
}

// 메시지 관련 타입
export interface Message {
    messageId: number
    content: string
    partner: UserSummary
    read: boolean
    createdAt: string
}

// 알림 관련 타입
export interface Notification {
    notificationId: number
    type: string
    message: string
    targetUrl?: string
    read: boolean
    createdAt: string
}

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

// 로그인 관련 타입
export interface LoginCredentials {
    loginId: string
    password: string
}

export interface LoginResponse {
    accessToken: string
    refreshToken: string
    user: User
}

// 회원가입 관련 타입
export interface SignupData {
    loginId: string
    password: string
    email: string
    displayName: string
}

// 사용자 설정 타입
export interface UserSettings {
    theme: 'LIGHT' | 'DARK'
    language: 'KO' | 'EN'
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
