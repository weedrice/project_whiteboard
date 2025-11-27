import api from './index'

export const searchApi = {
    // General search
    search: (params) => api.get('/search', { params }),

    // Search posts
    searchPosts: (params) => api.get('/search/posts', { params }),
}
