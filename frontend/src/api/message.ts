import api from './index'

export const messageApi = {
    sendMessage: (receiverId: string | number, content: string) => api.post(`/messages`, { receiverId, content }),
    getReceivedMessages: (params: any) => api.get('/messages/received', { params }),
    getSentMessages: (params: any) => api.get('/messages/sent', { params }),
    getUnreadCount: () => api.get('/messages/unread-count'),
    getMessage: (messageId: string | number) => api.get(`/messages/${messageId}`),
    deleteMessage: (messageId: string | number) => api.delete(`/messages/${messageId}`),
    deleteMessages: (messageIds: (string | number)[]) => api.delete('/messages', { data: messageIds }),
}
