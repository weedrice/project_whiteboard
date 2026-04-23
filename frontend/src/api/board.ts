import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    PageResponse,
    BoardCreateData,
    BoardDetail,
    BoardListItem,
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
}

interface CategoryUpdateData {
    name?: string
    sortOrder?: number
    isActive?: boolean
    minWriteRole?: string
}

interface BoardManagerTransferData {
    loginId: string
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
        api.post<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`, undefined, config),

    // Unsubscribe from board
    unsubscribeBoard: (boardUrl: string, config?: AxiosRequestConfig) =>
        api.delete<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`, config),

    // Update subscription order
    updateSubscriptionOrder: (boardUrls: string[]) => api.put<ApiResponse<void>>('/boards/subscriptions/order', { boardUrls }),
}

