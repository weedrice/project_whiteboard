import api from './index'
import type { AxiosRequestConfig } from 'axios'

export interface PostCreateData {
    title: string
    contents: string
    categoryId?: number
    isNotice?: boolean
    isNsfw?: boolean
    isSpoiler?: boolean
    tags?: string[]
}

export interface PostUpdateData {
    title?: string
    contents?: string
    categoryId?: number
    isNotice?: boolean
    isNsfw?: boolean
    isSpoiler?: boolean
    tags?: string[]
}

export interface ReportData {
    targetPostId: string | number
    reason: string
}

export const postApi = {
    // Create a new post
    createPost: (boardUrl: string, data: PostCreateData) => api.post(`/boards/${boardUrl}/posts`, data),

    // Get post details (placeholder for future)
    getPost: (postId: string | number, config?: AxiosRequestConfig) => api.get(`/posts/${postId}`, config),

    // Update post
    updatePost: (postId: string | number, data: PostUpdateData) => api.put(`/posts/${postId}`, data),

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
    getTrendingPosts: (page: number = 0, size: number = 10) => api.get('/posts/trending', { params: { page, size } }),

    // Report post
    reportPost: (data: ReportData) => api.post('/reports/posts', data),
}

