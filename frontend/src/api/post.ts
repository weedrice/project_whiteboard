import api from './index'

export const postApi = {
    // Create a new post
    createPost: (boardUrl: string, data: any) => api.post(`/boards/${boardUrl}/posts`, data),

    // Get post details (placeholder for future)
    getPost: (postId: string | number, config?: any) => api.get(`/posts/${postId}`, config),

    // Update post
    updatePost: (postId: string | number, data: any) => api.put(`/posts/${postId}`, data),

    // Delete post
    deletePost: (postId: string | number) => api.delete(`/posts/${postId}`),

    // Like post
    likePost: (postId: string | number) => api.post(`/posts/${postId}/like`),

    // Unlike post
    unlikePost: (postId: string | number) => api.delete(`/posts/${postId}/like`),

    // Scrap post
    scrapPost: (postId: string | number) => api.post(`/posts/${postId}/scrap`),

    // Unscrap post
    unscrapPost: (postId: string | number) => api.delete(`/posts/${postId}/scrap`),

    // Get trending posts
    getTrendingPosts: (limit: number) => api.get('/posts/trending', { params: { limit } }),
}
