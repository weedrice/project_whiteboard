import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, PageResponse, Board, Category, PostSummary, BoardCreateData, BoardUpdateData } from '@/types'

interface PostsParams {
    page?: number
    size?: number
    categoryId?: number
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

export const boardApi = {
    // Get all boards
    getBoards: () => api.get<ApiResponse<Board[]>>('/boards'),

    // Get board details
    getBoard: (boardUrl: string, config?: AxiosRequestConfig) => api.get<ApiResponse<Board>>(`/boards/${boardUrl}`, config),

    // Create a new board
    createBoard: (data: BoardCreateData) => api.post<ApiResponse<Board>>('/boards', data),

    // Get posts in a board
    getPosts: (boardUrl: string, params: PostsParams) => api.get<ApiResponse<PageResponse<PostSummary>>>(`/boards/${boardUrl}/posts`, { params }),

    // Get board categories
    getCategories: (boardUrl: string) => api.get<ApiResponse<Category[]>>(`/boards/${boardUrl}/categories`),

    // Update board
    updateBoard: (boardUrl: string, data: BoardUpdateData) => api.put<ApiResponse<Board>>(`/boards/${boardUrl}`, data),

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
    subscribeBoard: (boardUrl: string) => api.post<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`),

    // Unsubscribe from board
    unsubscribeBoard: (boardUrl: string) => api.delete<ApiResponse<void>>(`/boards/${boardUrl}/subscribe`),

    // Update subscription order
    updateSubscriptionOrder: (boardUrls: string[]) => api.put<ApiResponse<void>>('/boards/subscriptions/order', boardUrls),
}

