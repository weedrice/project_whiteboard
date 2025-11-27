import api from './index'

export const boardApi = {
    // Get all boards
    getBoards: () => api.get('/boards'),

    // Get board details
    getBoard: (boardId) => api.get(`/boards/${boardId}`),

    // Create a new board
    createBoard: (data) => api.post('/boards', data),

    // Get posts in a board
    getPosts: (boardId, params) => api.get(`/boards/${boardId}/posts`, { params }),

    // Get board categories
    getCategories: (boardId) => api.get(`/boards/${boardId}/categories`),

    // Update board
    updateBoard: (boardId, data) => api.put(`/boards/${boardId}`, data),

    // Delete board
    deleteBoard: (boardId) => api.delete(`/boards/${boardId}`),

    // Create category
    createCategory: (boardId, data) => api.post(`/boards/${boardId}/categories`, data),

    // Update category
    updateCategory: (boardId, categoryId, data) => api.put(`/boards/categories/${categoryId}`, data),

    // Delete category
    deleteCategory: (boardId, categoryId) => api.delete(`/boards/categories/${categoryId}`),

    // Subscribe to board
    subscribeBoard: (boardId) => api.post(`/boards/${boardId}/subscribe`),

    // Unsubscribe from board
    unsubscribeBoard: (boardId) => api.delete(`/boards/${boardId}/subscribe`),
}
