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
    getBoards: () => api.get<ApiResponse<BoardListItem[]>>('/boards'),

    // Get board details
    getBoard: (boardUrl: string, config?: AxiosRequestConfig) => api.get<ApiResponse<BoardDetail>>(`/boards/${boardUrl}`, config),

    // Create a new board
    createBoard: (data: BoardCreateData) => api.post<ApiResponse<BoardDetail>>('/boards', data),

    // Ensure inquiry board exists (create if absent)
    ensureInquiryBoard: (boardUrl?: string) =>
        api.post<ApiResponse<void>>('/boards/inquiry/ensure', null, { params: boardUrl ? { boardUrl } : undefined }),

    // Get posts in a board
    getPosts: (boardUrl: string, params: PostsParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<PostSummary>>>(`/boards/${boardUrl}/posts`, { ...config, params }),

    // Get board categories
    getCategories: (boardUrl: string) => api.get<ApiResponse<Category[]>>(`/boards/${boardUrl}/categories`),

    // Update board
    updateBoard: (boardUrl: string, data: BoardUpdateData) => api.put<ApiResponse<BoardDetail>>(`/boards/${boardUrl}`, data),

    // Transfer board manager
    updateBoardManager: (boardUrl: string, data: BoardManagerTransferData) =>
        api.put<ApiResponse<BoardDetail>>(`/boards/${boardUrl}/manager`, data),

    // Get board manager candidates
    getBoardManagerCandidates: (boardUrl: string, params: BoardManagerCandidateParams) =>
        api.get<ApiResponse<PageResponse<BoardManagerCandidate>>>(`/boards/${boardUrl}/manager-candidates`, { params }),

    // Delete board
    deleteBoard: (boardUrl: string) => api.delete<ApiResponse<void>>(`/boards/${boardUrl}`),

    // Create category
    createCategory: (boardUrl: string, data: CategoryCreateData) => api.post<ApiResponse<Category>>(`/boards/${boardUrl}/categories`, data),

    // Update category
    updateCategory: (boardUrl: string, categoryId: string | number, data: CategoryUpdateData) => api.put<ApiResponse<Category>>(`/boards/categories/${categoryId}`, data),

    // Delete category
    deleteCategory: (boardUrl: string, categoryId: string | number) => api.delete<ApiResponse<void>>(`/boards/categories/${categoryId}`),

    // Get board notices
    getNotices: (boardUrl: string) => api.get<ApiResponse<PostSummary[]>>(`/boards/${boardUrl}/notices`),

    // Subscribe to board
    subscribeBoard: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.post<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`, undefined, config)
            : api.post<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`),

    // Unsubscribe from board
    unsubscribeBoard: (boardUrl: string, config?: AxiosRequestConfig) =>
        config
            ? api.delete<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`, config)
            : api.delete<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`),

    // Update subscription order
    updateSubscriptionOrder: (boardUrls: string[]) => api.put<ApiResponse<void>>('/boards/subscriptions/order', { boardUrls }),
}

