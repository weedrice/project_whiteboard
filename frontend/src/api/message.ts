import api from './index'

interface MessageParams {
    page?: number
    size?: number
}

export const messageApi = {
    sendMessage: (receiverId: string | number, content: string) => api.post(`/messages`, { receiverId, content }),
    getReceivedMessages: (params: MessageParams) => api.get('/messages/received', { params }),
    getSentMessages: (params: MessageParams) => api.get('/messages/sent', { params }),
    getUnreadCount: () => api.get('/messages/unread-count'),
    getMessage: (messageId: string | number) => api.get(`/messages/${messageId}`),
    deleteMessage: (messageId: string | number) => api.delete(`/messages/${messageId}`),
    deleteMessages: (messageIds: (string | number)[]) => api.delete('/messages', { data: messageIds }),
}
