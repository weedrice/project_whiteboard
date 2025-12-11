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
}
