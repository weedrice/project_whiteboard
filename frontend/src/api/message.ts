import type { AxiosRequestConfig } from 'axios'
import api from './index'
import { API_ERROR_CODES } from '@/api/errorCodes'
import type { ApiResponse, MessageResponse, MessageSummaryDto } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

interface MessageParams {
    page?: number
    size?: number
    sort?: string
}

const BLOCKED_BY_USER_CODE = API_ERROR_CODES.BLOCKED_BY_USER

export const messageApi = {
    sendMessage: (receiverId: string | number, content: string, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<number>>('/messages', { receiverId, content }, config),
    getReceivedMessages: (params: MessageParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<MessageResponse>>('/messages/received', { ...config, params }),
    getSentMessages: (params: MessageParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<MessageResponse>>('/messages/sent', { ...config, params }),
    getConversations: (params: MessageParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<MessageResponse>>('/messages/conversations', { ...config, params }),
    getConversation: (partnerId: string | number, params: MessageParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<MessageResponse>>(`/messages/conversations/${encodePathSegment(partnerId)}`, { ...config, params }),
    getUnreadCount: () => api.get<ApiResponse<number>>('/messages/unread-count'),
    getMessage: (messageId: string | number, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<MessageSummaryDto>>(`/messages/${encodePathSegment(messageId)}`, config),
    markAsRead: (messageId: string | number, config?: AxiosRequestConfig) =>
        api.post<ApiResponse<void>>(`/messages/${encodePathSegment(messageId)}/read`, null, config),
    deleteMessage: (messageId: string | number) => api.delete<ApiResponse<void>>(`/messages/${encodePathSegment(messageId)}`),
    deleteMessages: (messageIds: (string | number)[], config?: AxiosRequestConfig) =>
        api.delete<ApiResponse<void>>('/messages', { ...config, data: messageIds }),
}

export { BLOCKED_BY_USER_CODE }
