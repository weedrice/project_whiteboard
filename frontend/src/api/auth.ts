import api from './index'
import type { LoginCredentials, SignupData, ApiResponse, LoginResponse, User } from '@/types'

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
    getMe: (config?: any) => api.get<ApiResponse<User>>('/users/me', config),

    // Email Verification
    sendVerificationCode: (email: string) => api.post<ApiResponse<void>>('/auth/email/send-verification', { email }),
    verifyCode: (email: string, code: string) => api.post<ApiResponse<boolean>>('/auth/email/verify', { email, code }),

    // Find ID
    findId: (email: string) => api.post<ApiResponse<{ loginId: string }>>('/auth/find-id', { email }),

    // Password Reset
    sendPasswordReset: (email: string) => api.post<ApiResponse<void>>('/auth/password/forgot', { email }),
    resetPassword: (data: PasswordResetData) => api.post<ApiResponse<void>>('/auth/password/reset-by-code', data),
}

