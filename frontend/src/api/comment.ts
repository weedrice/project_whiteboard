import api from './index'
import type { ApiResponse, PageResponse, Comment, CommentListResponse, CommentPayload } from '@/types'
import type { AxiosRequestConfig } from 'axios'
import { encodePathSegment } from '@/utils/urlPath'
export type { Comment, CommentPayload }
export type CommentWithNavigation = Comment & {
    boardUrl?: string
    postId?: number
}

export interface CommentCreateResponse {
    commentId: number
    earnedPoints?: number | null
}

export interface CommentParams {
    page?: number;
    size?: number;
    sort?: string;
}

export const commentApi = {
    // Get comments for a post
    getComments: (postId: string | number, params: CommentParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<Comment>>>(`/posts/${encodePathSegment(postId)}/comments`, { ...config, params }),

    getReplies: (commentId: string | number, params: CommentParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<CommentListResponse>>(`/comments/${encodePathSegment(commentId)}/replies`, { ...config, params }),

    getBestComments: (postId: string | number, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<Comment[]>>(`/posts/${encodePathSegment(postId)}/comments/best`, config),

    // Create a new comment
    createComment: (postId: string | number, data: CommentPayload) => api.post<ApiResponse<CommentCreateResponse>>(`/posts/${encodePathSegment(postId)}/comments`, data),

    // Delete a comment
    deleteComment: (commentId: string | number) => api.delete<ApiResponse<void>>(`/comments/${encodePathSegment(commentId)}`),

    // Update a comment
    updateComment: (commentId: string | number, data: CommentPayload) => api.put<ApiResponse<number>>(`/comments/${encodePathSegment(commentId)}`, data),

    getComment: (commentId: string | number, config?: AxiosRequestConfig) => config
        ? api.get<ApiResponse<CommentWithNavigation>>(`/comments/${encodePathSegment(commentId)}`, config)
        : api.get<ApiResponse<CommentWithNavigation>>(`/comments/${encodePathSegment(commentId)}`)
}
