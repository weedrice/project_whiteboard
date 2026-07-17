import api from './index'
import type { AxiosRequestConfig, AxiosResponse } from 'axios'
import { mapApiDataResponse } from '@/api/response'
import {
    normalizePostSummaryList,
    normalizePostSummaryPage,
    normalizeBoardDetail,
    type BoardDetailWire,
    type PostSummaryWire,
} from '@/api/postContract'
import type {
    ApiResponse,
    BoardCreateData,
    BoardDetail,
    BoardListItem,
    BoardManagerCandidate,
    BoardRecentUpdate,
    BoardUpdateData,
    Category,
    ModerationAuditLog,
    ModerationAuditSearchParams,
} from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'

interface PostsParams {
    page?: number
    size?: number
    categoryId?: number
    minLikes?: number
    sort?: string
}

export interface CategoryWriteData {
    name: string
    sortOrder: number
    minWriteRole?: string
    isDefault?: boolean
}

export interface CategoryOrderData {
    categoryIds: number[]
}

const mapBoardDetailResponse = (
    response: AxiosResponse<ApiResponse<BoardDetailWire>>,
): AxiosResponse<ApiResponse<BoardDetail>> => mapApiDataResponse(response, normalizeBoardDetail)

interface BoardManagerTransferData {
    loginId: string
}

interface BoardManagerCandidateParams {
    page?: number
    size?: number
    q?: string
}

export const boardApi = {
    // Get all boards
    getBoards: (config?: AxiosRequestConfig) =>
        config
            ? api.get<ApiResponse<BoardListItem[]>>('/boards', config)
            : api.get<ApiResponse<BoardListItem[]>>('/boards'),

    getBoardRecommendations: (topics: string[] = [], config?: AxiosRequestConfig) =>
        api.get<ApiResponse<BoardListItem[]>>('/boards/recommendations', {
            ...config,
            params: {
                ...config?.params,
                topics
            }
        }),

    getRecentBoardUpdates: (boardUrls: string[], config?: AxiosRequestConfig) =>
        api.get<ApiResponse<BoardRecentUpdate[]>>('/boards/recent-updates', {
            ...config,
            params: {
                ...config?.params,
                boardUrls,
            },
        }),

    // Get board details
    getBoard: (boardUrl: string, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<BoardDetailWire>>(`/boards/${encodePathSegment(boardUrl)}`, config).then(mapBoardDetailResponse),

    // Create a new board
    createBoard: (data: BoardCreateData, config?: AxiosRequestConfig) =>
        (config
            ? api.post<ApiResponse<BoardDetailWire>>('/boards', data, config)
            : api.post<ApiResponse<BoardDetailWire>>('/boards', data)
        ).then(mapBoardDetailResponse),

    // Ensure inquiry board exists (create if absent)
    ensureInquiryBoard: (boardUrl?: string, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<void>>('/boards/inquiry/ensure', null, {
            ...config,
            params: {
                ...config?.params,
                ...(boardUrl ? { boardUrl } : {})
            }
        }),

    // Get posts in a board
    getPosts: (boardUrl: string, params: PostsParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponseRaw<PostSummaryWire>>>(`/boards/${encodePathSegment(boardUrl)}/posts`, { ...config, params })
            .then((response) => mapApiDataResponse(response, normalizePostSummaryPage)),

    // Get board categories
    getCategories: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.get<ApiResponse<Category[]>>(`/boards/${encodePathSegment(boardUrl)}/categories`, config)
            : api.get<ApiResponse<Category[]>>(`/boards/${encodePathSegment(boardUrl)}/categories`),

    // Update board
    updateBoard: (boardUrl: string, data: BoardUpdateData, config?: AxiosRequestConfig) =>
        (config
            ? api.put<ApiResponse<BoardDetailWire>>(`/boards/${encodePathSegment(boardUrl)}`, data, config)
            : api.put<ApiResponse<BoardDetailWire>>(`/boards/${encodePathSegment(boardUrl)}`, data)
        ).then(mapBoardDetailResponse),

    // Transfer board manager
    updateBoardManager: (boardUrl: string, data: BoardManagerTransferData) =>
        api.put<ApiResponse<BoardDetailWire>>(`/boards/${encodePathSegment(boardUrl)}/manager`, data).then(mapBoardDetailResponse),

    // Get board manager candidates
    getBoardManagerCandidates: (boardUrl: string, params: BoardManagerCandidateParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponseRaw<BoardManagerCandidate>>>(`/boards/${encodePathSegment(boardUrl)}/manager-candidates`, { ...config, params }),

    getManagerAudits: (boardUrl: string, params: ModerationAuditSearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponseRaw<ModerationAuditLog>>>(`/boards/${encodePathSegment(boardUrl)}/manager/audits`, { ...config, params }),

    // Delete board
    deleteBoard: (boardUrl: string) => api.delete<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}`),

    // Create category
    createCategory: (boardUrl: string, data: CategoryWriteData, config?: AxiosRequestConfig) => config
        ? api.post<ApiResponse<Category>>(`/boards/${encodePathSegment(boardUrl)}/categories`, data, config)
        : api.post<ApiResponse<Category>>(`/boards/${encodePathSegment(boardUrl)}/categories`, data),

    reorderCategories: (boardUrl: string, data: CategoryOrderData, config?: AxiosRequestConfig) => config
        ? api.put<ApiResponse<Category[]>>(`/boards/${encodePathSegment(boardUrl)}/categories/order`, data, config)
        : api.put<ApiResponse<Category[]>>(`/boards/${encodePathSegment(boardUrl)}/categories/order`, data),

    // Update category
    updateCategory: (_boardUrl: string, categoryId: string | number, data: CategoryWriteData, config?: AxiosRequestConfig) => config
        ? api.put<ApiResponse<Category>>(`/boards/categories/${encodePathSegment(categoryId)}`, data, config)
        : api.put<ApiResponse<Category>>(`/boards/categories/${encodePathSegment(categoryId)}`, data),

    // Delete category
    deleteCategory: (_boardUrl: string, categoryId: string | number, config?: AxiosRequestConfig) => config
        ? api.delete<ApiResponse<void>>(`/boards/categories/${encodePathSegment(categoryId)}`, config)
        : api.delete<ApiResponse<void>>(`/boards/categories/${encodePathSegment(categoryId)}`),

    // Get board notices
    getNotices: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.get<ApiResponse<PostSummaryWire[]>>(`/boards/${encodePathSegment(boardUrl)}/notices`, config)
                .then((response) => mapApiDataResponse(response, normalizePostSummaryList))
            : api.get<ApiResponse<PostSummaryWire[]>>(`/boards/${encodePathSegment(boardUrl)}/notices`)
                .then((response) => mapApiDataResponse(response, normalizePostSummaryList)),

    // Subscribe to board
    subscribeBoard: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.post<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}/subscribe`, undefined, config)
            : api.post<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}/subscribe`),

    // Unsubscribe from board
    unsubscribeBoard: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.delete<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}/subscribe`, config)
            : api.delete<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}/subscribe`),

    // Update subscription order
    updateSubscriptionOrder: (boardUrls: string[], config?: AxiosRequestConfig) =>
        config
            ? api.put<ApiResponse<void>>('/boards/subscriptions/order', { boardUrls }, config)
            : api.put<ApiResponse<void>>('/boards/subscriptions/order', { boardUrls }),
}

