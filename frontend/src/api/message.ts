import api from './index'
import type { ApiResponse, PageResponse, Message } from '@/types'

interface MessageParams {
    page?: number
    size?: number
}

export const messageApi = {
    sendMessage: (receiverId: string | number, content: string) => api.post<ApiResponse<Message>>(`/messages`, { receiverId, content }),
    getReceivedMessages: (params: MessageParams) => api.get<ApiResponse<PageResponse<Message>>>('/messages/received', { params }),
    getSentMessages: (params: MessageParams) => api.get<ApiResponse<PageResponse<Message>>>('/messages/sent', { params }),
    getUnreadCount: () => api.get<ApiResponse<number>>('/messages/unread-count'),
    getMessage: (messageId: string | number) => api.get<ApiResponse<Message>>(`/messages/${messageId}`),
    deleteMessage: (messageId: string | number) => api.delete<ApiResponse<void>>(`/messages/${messageId}`),
    deleteMessages: (messageIds: (string | number)[]) => api.delete<ApiResponse<void>>('/messages', { data: messageIds }),
}
