import api from './index'
import { mapApiDataResponse } from '@/api/response'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import type {
    ApiResponse,
    DraftPost,
    HomeLandingPeriod,
    HomeLandingResponse,
    HomeLandingStats,
    PageResponse,
    Post,
    PostSummary
} from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

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
    poll?: PollPayload | null
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
    poll?: PollPayload | null
}

export interface PollPayload {
    question: string
    options: string[]
    multipleChoiceEnabled?: boolean
    anonymousEnabled?: boolean
    closesAt?: string | null
}

export interface PollVotePayload {
    optionIds: number[]
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

export interface PostCreateResponse {
    postId: number
    earnedPoints?: number | null
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

const emptyStats = (): HomeLandingStats => ({
    boardCount: 0,
    postCount: 0,
    liveCount: 0,
    onlineCount: 0,
    postsToday: 0,
    postsTodayDeltaPercent: null,
    activeBoardCount: 0,
    newMembersLast24Hours: 0,
    commentsToday: 0,
})

export const emptyHomeLanding = (): HomeLandingResponse => ({
    curatedPosts: [],
    latestPosts: [],
    boards: [],
    stats: emptyStats(),
})

function normalizeHomeLanding(landing: HomeLandingResponse | null | undefined): HomeLandingResponse {
    if (!landing) {
        return emptyHomeLanding()
    }
    return {
        curatedPosts: Array.isArray(landing.curatedPosts) ? landing.curatedPosts : [],
        latestPosts: Array.isArray(landing.latestPosts) ? landing.latestPosts : [],
        boards: landing.boards ?? [],
        stats: landing.stats ?? emptyStats(),
    }
}

function mapHomeLandingResponse(
    response: AxiosResponse<ApiResponse<HomeLandingResponse>>
): AxiosResponse<ApiResponse<HomeLandingResponse>> {
    return mapApiDataResponse(response, normalizeHomeLanding, { mapNullish: true })
}

export const postApi = {
    // Create a new post
    createPost: (boardUrl: string, data: PostCreateData) => api.post<ApiResponse<PostCreateResponse>>(`/boards/${encodePathSegment(boardUrl)}/posts`, data),

    // Get post details
    getPost: (postId: string | number, config?: AxiosRequestConfig) => api.get<ApiResponse<Post>>(`/posts/${encodePathSegment(postId)}`, config),

    // Update post
    updatePost: (postId: string | number, data: PostUpdateData) => api.put<ApiResponse<number>>(`/posts/${encodePathSegment(postId)}`, data),

    // Delete post
    deletePost: (postId: string | number) => api.delete<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}`),

    // Increment post view count
    incrementView: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/view`),

    // Like post
    likePost: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/like`),

    // Unlike post
    unlikePost: (postId: string | number) => api.delete<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/like`),

    votePoll: (postId: string | number, data: PollVotePayload) =>
        api.post<ApiResponse<Post['poll']>>(`/posts/${encodePathSegment(postId)}/poll/vote`, data),

    deletePollVote: (postId: string | number) =>
        api.delete<ApiResponse<Post['poll']>>(`/posts/${encodePathSegment(postId)}/poll/vote`),

    // Scrap post
    scrapPost: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/scrap`),

    // Unscrap post
    unscrapPost: (postId: string | number) => api.delete<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/scrap`),

    // Get trending posts
    getTrendingPosts: (page: number = 0, size: number = 10, period: HomeLandingPeriod = '24h') => api.get<ApiResponse<BackendPageResponse<PostSummary>>>('/posts/trending', { params: { page, size, period } }),

    // Get home landing data
    getHomeLanding: (period: HomeLandingPeriod = '24h', config?: AxiosRequestConfig) => api.get<ApiResponse<HomeLandingResponse>>('/home/landing', {
        ...config,
        params: { ...config?.params, period }
    }).then(mapHomeLandingResponse),

    // Draft APIs
    getDraft: (draftId: string | number) => api.get<ApiResponse<DraftPost>>(`/drafts/${encodePathSegment(draftId)}`),
    saveDraft: (data: PostDraftData) => api.post<ApiResponse<DraftPost>>('/drafts', data),
    deleteDraft: (draftId: string | number) => api.delete<ApiResponse<void>>(`/drafts/${encodePathSegment(draftId)}`),

    // Report post
    reportPost: (data: ReportData) => api.post<ApiResponse<number>>('/reports/posts', data),
}

