import type { AxiosRequestConfig } from 'axios'
import api from './index'
import type { ApiResponse, PageResponse, Message } from '@/types'

interface MessageParams {
    page?: number
    size?: number
}

const BLOCKED_BY_USER_CODE = 'U009'

export const messageApi = {
    sendMessage: (receiverId: string | number, content: string, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<number>>('/messages', { receiverId, content }, config),
    getReceivedMessages: (params: MessageParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<Message>>>('/messages/received', { ...config, params }),
    getSentMessages: (params: MessageParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<Message>>>('/messages/sent', { ...config, params }),
    getUnreadCount: () => api.get<ApiResponse<number>>('/messages/unread-count'),
    getMessage: (messageId: string | number, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<Message>>(`/messages/${messageId}`, config),
    markAsRead: (messageId: string | number, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<void>>(`/messages/${messageId}/read`, null, config),
    deleteMessage: (messageId: string | number) => api.delete<ApiResponse<void>>(`/messages/${messageId}`),
    deleteMessages: (messageIds: (string | number)[]) => api.delete<ApiResponse<void>>('/messages', { data: messageIds }),
}

export { BLOCKED_BY_USER_CODE }
