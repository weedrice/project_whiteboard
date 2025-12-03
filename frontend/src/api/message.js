import api from './index'

export const messageApi = {
    sendMessage: (receiverId, content) => api.post(`/messages`, { receiverId, content }),
    getReceivedMessages: (params) => api.get('/messages/received', { params }),
    getSentMessages: (params) => api.get('/messages/sent', { params }),
    getUnreadCount: () => api.get('/messages/unread-count'),
    getMessage: (messageId) => api.get(`/messages/${messageId}`),
    deleteMessage: (messageId) => api.delete(`/messages/${messageId}`),
    deleteMessages: (messageIds) => api.delete('/messages', { data: messageIds }),
}
