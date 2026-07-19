import api from './index'
import { mapApiDataResponse } from '@/api/response'
import {
    normalizePostDetail,
    normalizePostSummaryList,
    normalizePostSummaryPage,
    type PostDetailWire,
    type PostSummaryWire,
} from '@/api/postContract'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import type {
    ApiResponse,
    DraftPost,
    HomeLandingPeriod,
    HomeLandingResponse,
    HomeLandingStats,
    Post
} from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import type { PageResponseRaw } from '@/utils/pageResponse'

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
    seriesId?: number
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
    seriesId?: number | null
}

export interface PollPayload {
    question: string
    options: string[]
    multipleChoiceEnabled?: boolean
    anonymousEnabled?: boolean
    closesAt?: string | null
}

export interface DraftPollPayload {
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
    poll?: DraftPollPayload | null
    seriesId?: number | null
    updatedAt?: string
    originalPostId?: number
}

export interface PostCreateResponse {
    postId: number
    earnedPoints?: number | null
}

export interface ViewHistoryPayload {
    lastReadCommentId?: number
    durationMs?: number
}

export interface PostVersion {
    versionId: number
    actionType: string
    title: string
    createdAt: string
}

export interface ScheduledPostData extends PostCreateData {
    scheduledAt: string
}

export interface ScheduledPost {
    scheduledPostId: number
    status: 'SCHEDULED' | 'PUBLISHING' | 'PUBLISHED' | 'CANCELED' | 'FAILED'
    userId: number
    boardId: number
    boardUrl: string
    boardName: string
    categoryId?: number | null
    title: string
    scheduledAt: string
    publishedPostId?: number | null
    failureReason?: string | null
    publishedAt?: string | null
    canceledAt?: string | null
    createdAt: string
    modifiedAt?: string
}

export interface ScheduledPostDetail extends ScheduledPost {
    contents: string
    tags?: string[] | null
    isNotice: boolean
    isNsfw: boolean
    isSpoiler: boolean
    isSecret: boolean
    fileIds?: number[] | null
    poll?: PollPayload | null
    seriesId?: number | null
    draftId?: number | null
}

export interface ReportData {
    targetPostId: string | number
    reason: string
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

    createScheduledPost: (boardUrl: string, data: ScheduledPostData) =>
        api.post<ApiResponse<ScheduledPost>>(`/boards/${encodePathSegment(boardUrl)}/scheduled-posts`, data),

    getMyScheduledPosts: (params?: Record<string, unknown>, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponseRaw<ScheduledPost>>>('/users/me/scheduled-posts', {
            ...config,
            params: { ...params, ...config?.params },
        }),

    getScheduledPost: (scheduledPostId: string | number) =>
        api.get<ApiResponse<ScheduledPostDetail>>(`/scheduled-posts/${encodePathSegment(scheduledPostId)}`),

    updateScheduledPost: (scheduledPostId: string | number, data: ScheduledPostData) =>
        api.put<ApiResponse<ScheduledPost>>(`/scheduled-posts/${encodePathSegment(scheduledPostId)}`, data),

    cancelScheduledPost: (scheduledPostId: string | number, config?: AxiosRequestConfig) =>
        config
            ? api.delete<ApiResponse<void>>(`/scheduled-posts/${encodePathSegment(scheduledPostId)}`, config)
            : api.delete<ApiResponse<void>>(`/scheduled-posts/${encodePathSegment(scheduledPostId)}`),

    // Get post details
    getPost: (postId: string | number, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PostDetailWire>>(`/posts/${encodePathSegment(postId)}`, config)
            .then((response) => mapApiDataResponse(response, normalizePostDetail)),

    getRelatedPosts: (postId: string | number, size = 5, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PostSummaryWire[]>>(`/posts/${encodePathSegment(postId)}/related`, {
            ...config,
            params: { ...config?.params, size },
        }).then((response) => mapApiDataResponse(response, normalizePostSummaryList)),

    pinPostByManager: (postId: string | number) =>
        api.post<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/manager/pin`),

    unpinPostByManager: (postId: string | number) =>
        api.delete<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/manager/pin`),

    blindPostByManager: (postId: string | number, reason?: string) =>
        api.post<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/manager/blind`, reason ? { reason } : undefined),

    unblindPostByManager: (postId: string | number) =>
        api.delete<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/manager/blind`),

    // Update post
    updatePost: (postId: string | number, data: PostUpdateData) => api.put<ApiResponse<number>>(`/posts/${encodePathSegment(postId)}`, data),

    // Delete post
    deletePost: (postId: string | number, config?: AxiosRequestConfig) => {
        const path = `/posts/${encodePathSegment(postId)}`
        return config
            ? api.delete<ApiResponse<void>>(path, config)
            : api.delete<ApiResponse<void>>(path)
    },

    // Increment post view count
    incrementView: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/view`),

    updateViewHistory: (
        postId: string | number,
        data: ViewHistoryPayload,
        config?: AxiosRequestConfig,
    ) => api.put<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/history`, data, config),

    getPostVersions: (postId: string | number, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PostVersion[]>>(`/posts/${encodePathSegment(postId)}/versions`, config),

    // Like post
    likePost: (postId: string | number) => api.post<ApiResponse<number>>(`/posts/${encodePathSegment(postId)}/like`),

    // Unlike post
    unlikePost: (postId: string | number) => api.delete<ApiResponse<number>>(`/posts/${encodePathSegment(postId)}/like`),

    votePoll: (postId: string | number, data: PollVotePayload) =>
        api.post<ApiResponse<Post['poll']>>(`/posts/${encodePathSegment(postId)}/poll/vote`, data),

    deletePollVote: (postId: string | number) =>
        api.delete<ApiResponse<Post['poll']>>(`/posts/${encodePathSegment(postId)}/poll/vote`),

    // Scrap post
    scrapPost: (postId: string | number) => api.post<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/scrap`),

    // Unscrap post
    unscrapPost: (postId: string | number) => api.delete<ApiResponse<void>>(`/posts/${encodePathSegment(postId)}/scrap`),

    // Get trending posts
    getTrendingPosts: (page: number = 0, size: number = 10, period: HomeLandingPeriod = '24h') =>
        api.get<ApiResponse<PageResponseRaw<PostSummaryWire>>>('/posts/trending', { params: { page, size, period } })
            .then((response) => mapApiDataResponse(response, normalizePostSummaryPage)),

    // Get home landing data
    getHomeLanding: (period: HomeLandingPeriod = '24h', config?: AxiosRequestConfig) => api.get<ApiResponse<HomeLandingResponse>>('/home/landing', {
        ...config,
        params: { ...config?.params, period }
    }).then(mapHomeLandingResponse),

    // Draft APIs
    getDraft: (draftId: string | number) => api.get<ApiResponse<DraftPost>>(`/drafts/${encodePathSegment(draftId)}`),
    saveDraft: (data: PostDraftData, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<DraftPost>>('/drafts', data, config),
    deleteDraft: (draftId: string | number, config?: AxiosRequestConfig) =>
        api.delete<ApiResponse<void>>(`/drafts/${encodePathSegment(draftId)}`, config),

    // Report post
    reportPost: (data: ReportData) => api.post<ApiResponse<number>>('/reports/posts', data),
}

