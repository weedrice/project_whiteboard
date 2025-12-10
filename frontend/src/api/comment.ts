import api from './index'

export interface CommentAuthor {
    userId: number;
    displayName: string;
    profileImageUrl?: string;
}

export interface Comment {
    commentId: number;
    content: string;
    postId: number;
    parentId?: number | null;
    author: CommentAuthor;
    createdAt: string;
    updatedAt?: string;
    isDeleted: boolean;
    children?: Comment[];
}

export interface CommentPayload {
    content: string;
    parentId?: number | null;
}

export interface CommentParams {
    page?: number;
    size?: number;
    sort?: string;
}

export const commentApi = {
    // Get comments for a post
    getComments: (postId: string | number, params: CommentParams) => api.get(`/posts/${postId}/comments`, { params }),

    // Create a new comment
    createComment: (postId: string | number, data: CommentPayload) => api.post(`/posts/${postId}/comments`, data),

    // Delete a comment
    deleteComment: (commentId: string | number) => api.delete(`/comments/${commentId}`),

    // Update a comment
    updateComment: (commentId: string | number, data: CommentPayload) => api.put(`/comments/${commentId}`, data),

    getComment: (commentId: string | number) => api.get(`/comments/${commentId}`)
}
