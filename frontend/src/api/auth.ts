import api from './index'
import type { LoginCredentials, SignupData } from '@/types'

interface PasswordResetData {
    email: string
    code: string
    newPassword: string
}

export const authApi = {
    // Login
    login: (credentials: LoginCredentials) => api.post('/auth/login', credentials, { skipGlobalErrorHandler: true }),

    // Signup
    signup: (data: SignupData) => api.post('/auth/signup', data, { skipGlobalErrorHandler: true }),

    // Logout
    logout: (refreshToken: string) => api.post('/auth/logout', { refreshToken }),

    // Refresh Token
    refreshToken: (refreshToken: string) => api.post('/auth/refresh', { refreshToken }),

    // Get Current User
    getMe: () => api.get('/users/me'),

    // Email Verification
    sendVerificationCode: (email: string) => api.post('/auth/email/send-verification', { email }),
    verifyCode: (email: string, code: string) => api.post('/auth/email/verify', { email, code }),

    // Find ID
    findId: (email: string) => api.post('/auth/find-id', { email }),

    // Password Reset
    sendPasswordReset: (email: string) => api.post('/auth/password/forgot', { email }),
    resetPassword: (data: PasswordResetData) => api.post('/auth/password/reset-by-code', data),
}

