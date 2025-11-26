import api from './index'

export const commentApi = {
    // Get comments for a post
    getComments: (postId, params) => api.get(`/posts/${postId}/comments`, { params }),

    // Create a new comment
    createComment: (postId, data) => api.post(`/posts/${postId}/comments`, data),

    // Delete a comment (placeholder for future)
    deleteComment: (commentId) => api.delete(`/comments/${commentId}`),
}
