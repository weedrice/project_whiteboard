import api from './index'

export const messageApi = {
    sendMessage: (recipientId, content) => api.post(`/messages/send`, { recipientId, content }),
    // Other message related APIs can be added here
    // getConversations: () => api.get('/messages/conversations'),
    // getMessages: (conversationId) => api.get(`/messages/${conversationId}`),
}
