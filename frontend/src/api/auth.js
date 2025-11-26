import api from './index'

export const authApi = {
    // Login
    login: (credentials) => api.post('/auth/login', credentials),

    // Signup
    signup: (data) => api.post('/auth/signup', data),

    // Logout
    logout: (refreshToken) => api.post('/auth/logout', { refreshToken }),

    // Refresh Token
    refreshToken: (refreshToken) => api.post('/auth/refresh', { refreshToken }),

    // Get Current User
    getMe: () => api.get('/users/me'),
}
