import api from './index'

export const postApi = {
    // Create a new post
    createPost: (boardId, data) => api.post(`/boards/${boardId}/posts`, data),

    // Get post details (placeholder for future)
    getPost: (postId) => api.get(`/posts/${postId}`),

    // Update post
    updatePost: (postId, data) => api.put(`/posts/${postId}`, data),

    // Delete post
    deletePost: (postId) => api.delete(`/posts/${postId}`),

    // Like post
    likePost: (postId) => api.post(`/posts/${postId}/like`),

    // Unlike post
    unlikePost: (postId) => api.delete(`/posts/${postId}/like`),

    // Scrap post
    scrapPost: (postId) => api.post(`/posts/${postId}/scrap`),

    // Unscrap post
    unscrapPost: (postId) => api.delete(`/posts/${postId}/scrap`),
}
