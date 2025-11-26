import api from './index'

export const searchApi = {
    // Search posts
    searchPosts: (params) => api.get('/search/posts', { params }),
}
