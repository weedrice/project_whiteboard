import api from './index'
import type { ApiResponse, PageResponse, Comment, CommentPayload } from '@/types'
export type { Comment, CommentPayload }

export interface CommentParams {
    page?: number;
    size?: number;
    sort?: string;
}

export const commentApi = {
    // Get comments for a post
    getComments: (postId: string | number, params: CommentParams) => api.get<ApiResponse<PageResponse<Comment>>>(`/posts/${postId}/comments`, { params }),

    // Create a new comment
    createComment: (postId: string | number, data: CommentPayload) => api.post<ApiResponse<Comment>>(`/posts/${postId}/comments`, data),

    // Delete a comment
    deleteComment: (commentId: string | number) => api.delete<ApiResponse<void>>(`/comments/${commentId}`),

    // Update a comment
    updateComment: (commentId: string | number, data: CommentPayload) => api.put<ApiResponse<Comment>>(`/comments/${commentId}`, data),

    getComment: (commentId: string | number) => api.get<ApiResponse<Comment>>(`/comments/${commentId}`)
}
