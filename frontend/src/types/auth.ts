import type { User } from './user'

// 로그인 관련 타입
export interface LoginCredentials {
    loginId: string
    password: string
}

export interface LoginResponse {
    accessToken: string
    expiresIn: number
    user: User
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

export interface OAuthSignupTicket {
    email: string
    name: string
    provider: string
}
