import api from './index'

export const boardApi = {
    // Get all boards
    getBoards: () => api.get('/boards'),

    // Get board details
    getBoard: (boardUrl) => api.get(`/boards/${boardUrl}`),

    // Create a new board
    createBoard: (data) => api.post('/boards', data),

    // Get posts in a board
    getPosts: (boardUrl, params) => api.get(`/boards/${boardUrl}/posts`, { params }),

    // Get board categories
    getCategories: (boardUrl) => api.get(`/boards/${boardUrl}/categories`),

    // Update board
    updateBoard: (boardUrl, data) => api.put(`/boards/${boardUrl}`, data),

    // Delete board
    deleteBoard: (boardUrl) => api.delete(`/boards/${boardUrl}`),

    // Create category
    createCategory: (boardUrl, data) => api.post(`/boards/${boardUrl}/categories`, data),

    // Update category
    updateCategory: (boardUrl, categoryId, data) => api.put(`/boards/categories/${categoryId}`, data),

    // Delete category
    deleteCategory: (boardUrl, categoryId) => api.delete(`/boards/categories/${categoryId}`),

    // Get board notices
    getNotices: (boardUrl) => api.get(`/boards/${boardUrl}/notices`),

    // Subscribe to board
    subscribeBoard: (boardUrl) => api.post(`/boards/${boardUrl}/subscribe`),

    // Unsubscribe from board
    unsubscribeBoard: (boardUrl) => api.delete(`/boards/${boardUrl}/subscribe`),

    // Update subscription order
    updateSubscriptionOrder: (boardUrls) => api.put('/boards/subscriptions/order', boardUrls),
}
