import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    DraftPost,
    HomeLandingPeriod,
    HomeLandingResponse,
    PageResponse,
    Post,
    PostSummary
} from '@/types'

export interface PostCreateData {
    title: string
    contents: string
    categoryId?: number
    isNotice?: boolean
    isNsfw?: boolean
    isSpoiler?: boolean
    isSecret?: boolean
    tags?: string[]
    draftId?: number
    fileIds?: number[]
}

export interface PostUpdateData {
    title?: string
    contents?: string
    categoryId?: number
    isNotice?: boolean
    isNsfw?: boolean
    isSpoiler?: boolean
    isSecret?: boolean
    tags?: string[]
    draftId?: number
    fileIds?: number[]
}

export interface PostDraftData {
    draftId?: number
    boardUrl: string
    title?: string
    contents?: string
    categoryId?: number | null
    tags?: string[]
    isNotice?: boolean
    isNsfw?: boolean
    isSpoiler?: boolean
    isSecret?: boolean
    fileIds?: number[]
    updatedAt?: string
    originalPostId?: number
}

export interface ReportData {
    targetPostId: string | number
    reason: string
}

export type BackendPageResponse<T> = Partial<PageResponse<T>> & {
    content: T[]
    page?: number
    hasNext?: boolean
    hasPrevious?: boolean
}

export const postApi = {
    // Create a new post
    createPost: (boardUrl: string, data: PostCreateData) => api.post<ApiResponse<number>>(`/boards/${boardUrl}/posts`, data),

    // Get post details
    getPost: (postId: string | number, config?: AxiosRequestConfig) => api.get<ApiResponse<Post>>(`/posts/${postId}`, config),

    // Update post
    updatePost: (postId: string | number, data: PostUpdateData) => api.put<ApiResponse<number>>(`/posts/${postId}`, data),

    // Delete post
    deletePost: (postId: string | number) => api.delete<ApiResponse<void>>(`/posts/${postId}`),

    // Increment post view count
    incrementView: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${postId}/view`),

    // Like post
    likePost: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${postId}/like`),

    // Unlike post
    unlikePost: (postId: string | number) => api.delete<ApiResponse<void>>(`/posts/${postId}/like`),

    // Scrap post
    scrapPost: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${postId}/scrap`),

    // Unscrap post
    unscrapPost: (postId: string | number) => api.delete<ApiResponse<void>>(`/posts/${postId}/scrap`),

    // Get trending posts
    getTrendingPosts: (page: number = 0, size: number = 10, period: HomeLandingPeriod = '24h') => api.get<ApiResponse<BackendPageResponse<PostSummary>>>('/posts/trending', { params: { page, size, period } }),

    // Get home landing data
    getHomeLanding: (period: HomeLandingPeriod = '24h') => api.get<ApiResponse<HomeLandingResponse>>('/home/landing', {
        params: { period }
    }),

    // Draft APIs
    getDraft: (draftId: string | number) => api.get<ApiResponse<DraftPost>>(`/drafts/${draftId}`),
    saveDraft: (data: PostDraftData) => api.post<ApiResponse<DraftPost>>('/drafts', data),
    deleteDraft: (draftId: string | number) => api.delete<ApiResponse<void>>(`/drafts/${draftId}`),

    // Report post
    reportPost: (data: ReportData) => api.post<ApiResponse<number>>('/reports/posts', data),
}

