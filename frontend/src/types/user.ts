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
