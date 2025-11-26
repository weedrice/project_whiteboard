import api from './index'

export const userApi = {
    // Get current user profile
    getMyProfile: () => api.get('/users/me'),

    // Get posts created by current user
    getMyPosts: (params) => api.get('/users/me/posts', { params }),
}
