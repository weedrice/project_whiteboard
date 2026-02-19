import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type { LoginCredentials, SignupData, ApiResponse, LoginResponse, User } from '@/types'

export interface VerifyCodeResponse {
    verified: boolean
    loginId?: string
    isReregister: boolean
    /** Jackson이 isReregister를 reregister로 직렬화할 수 있음 */
    reregister?: boolean
}

export interface ReregisterCheckResponse {
    canReregister: boolean
    maskedLoginId?: string
}

interface PasswordResetData {
    email: string
    code: string
    newPassword: string
}

export const authApi = {
    // Login
    login: (credentials: LoginCredentials) => api.post<ApiResponse<LoginResponse>>('/auth/login', credentials, { skipGlobalErrorHandler: true }),

    // Signup
    signup: (data: SignupData) => api.post<ApiResponse<User>>('/auth/signup', data, { skipGlobalErrorHandler: true }),

    // Logout
    logout: (refreshToken: string) => api.post<ApiResponse<void>>('/auth/logout', { refreshToken }),

    // Refresh Token
    refreshToken: (refreshToken: string) => api.post<ApiResponse<{ accessToken: string, refreshToken: string }>>('/auth/refresh', { refreshToken }),

    // Get Current User
    getMe: (config?: AxiosRequestConfig) => api.get<ApiResponse<User>>('/users/me', config),

    // Email Verification (forSignup: true면 회원가입용, 이미 가입된 이메일이면 서버에서 중복 에러)
    sendVerificationCode: (email: string, forSignup?: boolean) =>
        api.post<ApiResponse<void>>('/auth/email/send-verification', { email, forSignup: forSignup ?? false }, { skipAuthRefresh: true }),
    verifyCode: (email: string, code: string) =>
        api.post<ApiResponse<VerifyCodeResponse>>('/auth/email/verify', { email, code }, {
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true
        }),

    // 재가입: 탈퇴 계정 이메일 확인 (마스킹된 loginId 반환)
    checkEmailForReregister: (email: string) =>
        api.get<ApiResponse<ReregisterCheckResponse>>('/auth/reregister/check-email', {
            params: { email },
            skipAuthRefresh: true
        } as AxiosRequestConfig),

    // Find ID
    findId: (email: string) => api.post<ApiResponse<{ loginId: string }>>('/auth/find-id', { email }, {
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true
    }),

    // Password Reset (FindAccountPage - code 방식)
    sendPasswordReset: (email: string) => api.post<ApiResponse<void>>('/auth/password/send-reset-link', { email }, { skipAuthRefresh: true }),
    resetPassword: (data: PasswordResetData) => api.post<ApiResponse<void>>('/auth/password/reset-by-code', data, {
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true
    }),

    // Password Reset (링크 방식 - 이메일 입력 → 해당 이메일로 ID+링크 발송)
    sendPasswordResetLinkByEmail: (email: string) =>
        api.post<ApiResponse<void>>('/auth/password/send-reset-link-by-email', { email }, {
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true
        }),
    resetPasswordWithToken: (token: string, newPassword: string) =>
        api.post<ApiResponse<void>>('/auth/password/reset', { token, newPassword }, { skipAuthRefresh: true }),
}

