import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type { LoginCredentials, SignupData, ApiResponse, LoginResponse, User } from '@/types'

export type VerificationPurpose = 'SIGNUP' | 'FIND_ID' | 'PASSWORD_RESET' | 'CHANGE_EMAIL'

export interface VerifyCodeResponse {
    verified: boolean
    verificationTicket?: string
    loginId?: string
    isReregister: boolean
}

export interface ReregisterCheckResponse {
    canReregister: boolean
    maskedLoginId?: string
}

interface PasswordResetData {
    email: string
    verificationTicket: string
    newPassword: string
}

export const authApi = {
    login: (credentials: LoginCredentials) =>
        api.post<ApiResponse<LoginResponse>>('/auth/login', credentials, { skipGlobalErrorHandler: true }),

    signup: (data: SignupData) =>
        api.post<ApiResponse<User>>('/auth/signup', data, { skipGlobalErrorHandler: true }),

    logout: () => api.post<ApiResponse<void>>(
        '/auth/logout',
        undefined,
        { skipAuthRefresh: true, skipGlobalErrorHandler: true },
    ),

    refreshToken: (config?: AxiosRequestConfig) => config
        ? api.post<ApiResponse<{ accessToken: string, expiresIn: number }>>('/auth/refresh', undefined, config)
        : api.post<ApiResponse<{ accessToken: string, expiresIn: number }>>('/auth/refresh'),

    getMe: (config?: AxiosRequestConfig) => api.get<ApiResponse<User>>('/users/me', config),

    sendVerificationCode: (email: string, purpose: VerificationPurpose) =>
        api.post<ApiResponse<void>>(
            '/auth/email/send-verification',
            { email, purpose },
            { skipAuthRefresh: true },
        ),

    verifyCode: (email: string, code: string, purpose: VerificationPurpose) =>
        api.post<ApiResponse<VerifyCodeResponse>>(
            '/auth/email/verify',
            { email, code, purpose },
            {
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            },
        ),

    checkEmailForReregister: (email: string) =>
        api.get<ApiResponse<ReregisterCheckResponse>>('/auth/reregister/check-email', {
            params: { email },
            skipAuthRefresh: true,
        } as AxiosRequestConfig),

    findId: (email: string, verificationTicket: string) =>
        api.post<ApiResponse<{ loginId: string }>>(
            '/auth/find-id',
            { email, verificationTicket },
            {
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            },
        ),

    sendPasswordReset: (email: string, verificationTicket: string) =>
        api.post<ApiResponse<void>>(
            '/auth/password/send-reset-link',
            { email, verificationTicket },
            { skipAuthRefresh: true },
        ),

    resetPassword: (data: PasswordResetData) =>
        api.post<ApiResponse<void>>('/auth/password/reset-by-code', data, {
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true,
        }),

    sendPasswordResetLinkByEmail: (email: string, verificationTicket: string) =>
        api.post<ApiResponse<void>>(
            '/auth/password/send-reset-link-by-email',
            { email, verificationTicket },
            {
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            },
        ),

    resetPasswordWithToken: (token: string, newPassword: string) =>
        api.post<ApiResponse<void>>('/auth/password/reset', { token, newPassword }, {
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true,
        }),
}
