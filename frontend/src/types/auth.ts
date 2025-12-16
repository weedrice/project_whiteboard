import type { User } from './user'

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
