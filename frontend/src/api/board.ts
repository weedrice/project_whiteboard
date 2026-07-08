import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    PageResponse,
    BoardCreateData,
    BoardDetail,
    BoardListItem,
    BoardManagerCandidate,
    BoardUpdateData,
    Category,
    PostSummary
} from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

interface PostsParams {
    page?: number
    size?: number
    categoryId?: number
    minLikes?: number
    sort?: string
}

interface CategoryCreateData {
    name: string
    sortOrder?: number
    minWriteRole?: string
    isDefault?: boolean
}

interface CategoryUpdateData {
    name?: string
    sortOrder?: number
    isActive?: boolean
    minWriteRole?: string
    isDefault?: boolean
}

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

    // Get board details
    getBoard: (boardUrl: string, config?: AxiosRequestConfig) => api.get<ApiResponse<BoardDetail>>(`/boards/${encodePathSegment(boardUrl)}`, config),

    // Create a new board
    createBoard: (data: BoardCreateData) => api.post<ApiResponse<BoardDetail>>('/boards', data),

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
        api.get<ApiResponse<PageResponse<PostSummary>>>(`/boards/${encodePathSegment(boardUrl)}/posts`, { ...config, params }),

    // Get board categories
    getCategories: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.get<ApiResponse<Category[]>>(`/boards/${encodePathSegment(boardUrl)}/categories`, config)
            : api.get<ApiResponse<Category[]>>(`/boards/${encodePathSegment(boardUrl)}/categories`),

    // Update board
    updateBoard: (boardUrl: string, data: BoardUpdateData) => api.put<ApiResponse<BoardDetail>>(`/boards/${encodePathSegment(boardUrl)}`, data),

    // Transfer board manager
    updateBoardManager: (boardUrl: string, data: BoardManagerTransferData) =>
        api.put<ApiResponse<BoardDetail>>(`/boards/${encodePathSegment(boardUrl)}/manager`, data),

    // Get board manager candidates
    getBoardManagerCandidates: (boardUrl: string, params: BoardManagerCandidateParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<BoardManagerCandidate>>>(`/boards/${encodePathSegment(boardUrl)}/manager-candidates`, { ...config, params }),

    // Delete board
    deleteBoard: (boardUrl: string) => api.delete<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}`),

    // Create category
    createCategory: (boardUrl: string, data: CategoryCreateData) => api.post<ApiResponse<Category>>(`/boards/${encodePathSegment(boardUrl)}/categories`, data),

    // Update category
    updateCategory: (_boardUrl: string, categoryId: string | number, data: CategoryUpdateData) => api.put<ApiResponse<Category>>(`/boards/categories/${encodePathSegment(categoryId)}`, data),

    // Delete category
    deleteCategory: (_boardUrl: string, categoryId: string | number) => api.delete<ApiResponse<void>>(`/boards/categories/${encodePathSegment(categoryId)}`),

    // Get board notices
    getNotices: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.get<ApiResponse<PostSummary[]>>(`/boards/${encodePathSegment(boardUrl)}/notices`, config)
            : api.get<ApiResponse<PostSummary[]>>(`/boards/${encodePathSegment(boardUrl)}/notices`),

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
    updateSubscriptionOrder: (boardUrls: string[]) => api.put<ApiResponse<void>>('/boards/subscriptions/order', { boardUrls }),
}

