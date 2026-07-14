import type { User } from './user'

// 로그인 관련 타입
export interface LoginCredentials {
    loginId: string
    password: string
}

export interface LoginUser {
    userId: number
    loginId: string
    displayName: string
    profileImageUrl?: string
    role?: User['role']
    theme?: User['theme']
    points?: number
    isEmailVerified?: boolean
    /** 이전 백엔드 직렬화 필드와의 호환용 */
    emailVerified?: boolean
}

export interface LoginResponse {
    accessToken: string
    expiresIn: number
    user: LoginUser
}

// 회원가입 관련 타입
export interface SignupData {
    loginId: string
    password: string
    email: string
    verificationTicket: string
    displayName: string
    oauthRegistrationTicket?: string | null
}

export interface SignupResponse {
    userId: number
    loginId: string
    email: string
    displayName: string
}

export interface OAuthSignupTicket {
    email: string
    name: string
    provider: string
}
