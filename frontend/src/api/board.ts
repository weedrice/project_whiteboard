import api from './index'

export const boardApi = {
    // Get all boards
    getBoards: () => api.get('/boards'),

    // Get board details
    getBoard: (boardUrl: string, config?: any) => api.get(`/boards/${boardUrl}`, config),

    // Create a new board
    createBoard: (data: any) => api.post('/boards', data),

    // Get posts in a board
    getPosts: (boardUrl: string, params: any) => api.get(`/boards/${boardUrl}/posts`, { params }),

    // Get board categories
    getCategories: (boardUrl: string) => api.get(`/boards/${boardUrl}/categories`),

    // Update board
    updateBoard: (boardUrl: string, data: any) => api.put(`/boards/${boardUrl}`, data),

    // Delete board
    deleteBoard: (boardUrl: string) => api.delete(`/boards/${boardUrl}`),

    // Create category
    createCategory: (boardUrl: string, data: any) => api.post(`/boards/${boardUrl}/categories`, data),

    // Update category
    updateCategory: (boardUrl: string, categoryId: string | number, data: any) => api.put(`/boards/categories/${categoryId}`, data),

    // Delete category
    deleteCategory: (boardUrl: string, categoryId: string | number) => api.delete(`/boards/categories/${categoryId}`),

    // Get board notices
    getNotices: (boardUrl: string) => api.get(`/boards/${boardUrl}/notices`),

    // Subscribe to board
    subscribeBoard: (boardUrl: string) => api.post(`/boards/${boardUrl}/subscribe`),

    // Unsubscribe from board
    unsubscribeBoard: (boardUrl: string) => api.delete(`/boards/${boardUrl}/subscribe`),

    // Update subscription order
    updateSubscriptionOrder: (boardUrls: string[]) => api.put('/boards/subscriptions/order', boardUrls),
}
