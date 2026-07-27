import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type { LoginCredentials, SignupData, SignupResponse, ApiResponse, LoginResponse, OAuthSignupTicket, User } from '@/types'

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
    login: (credentials: LoginCredentials, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<LoginResponse>>('/auth/login', credentials, {
            ...config,
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true,
        }),

    signup: (data: SignupData, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<SignupResponse>>('/auth/signup', data, {
            ...config,
            skipGlobalErrorHandler: true,
        }),

    logout: () => api.post<ApiResponse<void>>(
        '/auth/logout',
        undefined,
        { skipAuthRefresh: true, skipGlobalErrorHandler: true },
    ),

    refreshToken: (config?: AxiosRequestConfig) => config
        ? api.post<ApiResponse<{ accessToken: string, expiresIn: number }>>('/auth/refresh', undefined, config)
        : api.post<ApiResponse<{ accessToken: string, expiresIn: number }>>('/auth/refresh'),

    getMe: (config?: AxiosRequestConfig) => api.get<ApiResponse<User>>('/users/me', config),

    sendVerificationCode: (email: string, purpose: VerificationPurpose, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<void>>(
            '/auth/email/send-verification',
            { email, purpose },
            { ...config, skipAuthRefresh: true, skipGlobalErrorHandler: true },
        ),

    verifyCode: (email: string, code: string, purpose: VerificationPurpose, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<VerifyCodeResponse>>(
            '/auth/email/verify',
            { email, code, purpose },
            {
                ...config,
                skipAuthRefresh: true,
                skipGlobalErrorHandler: true,
            },
        ),

    checkEmailForReregister: (email: string) =>
        api.get<ApiResponse<ReregisterCheckResponse>>('/auth/reregister/check-email', {
            params: { email },
            skipAuthRefresh: true,
        } as AxiosRequestConfig),

    getOAuthSignupTicket: (config?: AxiosRequestConfig) =>
        api.get<ApiResponse<OAuthSignupTicket>>('/auth/oauth/signup-ticket', {
            ...config,
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true,
        } as AxiosRequestConfig),

    deleteOAuthSignupTicket: (config?: AxiosRequestConfig) =>
        api.delete<ApiResponse<void>>('/auth/oauth/signup-ticket', {
            ...config,
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true,
        }),

    findId: (email: string, verificationTicket: string, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<{ loginId: string }>>(
            '/auth/find-id',
            { email, verificationTicket },
            {
                ...config,
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

    resetPassword: (data: PasswordResetData, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<void>>('/auth/password/reset-by-code', data, {
            ...config,
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

    resetPasswordWithToken: (token: string, newPassword: string, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<void>>('/auth/password/reset', { token, newPassword }, {
            ...config,
            skipAuthRefresh: true,
            skipGlobalErrorHandler: true,
        }),
}
