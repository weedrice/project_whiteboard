import api from './index'

export const authApi = {
    // Login
    login: (credentials: any) => api.post('/auth/login', credentials, { skipGlobalErrorHandler: true }),

    // Signup
    signup: (data: any) => api.post('/auth/signup', data, { skipGlobalErrorHandler: true }),

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
    resetPassword: (data: any) => api.post('/auth/password/reset-by-code', data),
}
