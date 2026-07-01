import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type {
    AdminBoard,
    AdminInquirySummary,
    ApiResponse,
    BoardCreateData,
    BoardDetail,
    BoardUpdateData,
    PageResponse,
    Post,
} from '@/types'
import { encodePathSegment } from '@/utils/urlPath'
import { getWithOptionalConfig, type PaginationParams } from '@/api/adminTypes'

export const adminBoardApi = {
    getInquiryPosts(params: PaginationParams & { sort?: string }, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<AdminInquirySummary>>>('/admin/inquiries', { ...config, params })
    },
    getInquiryPost(postId: string | number, config?: AxiosRequestConfig) {
        return config
            ? api.get<ApiResponse<Post>>(`/admin/inquiries/${encodePathSegment(postId)}`, config)
            : api.get<ApiResponse<Post>>(`/admin/inquiries/${encodePathSegment(postId)}`)
    },
    getBoards(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<AdminBoard[]>>('/boards/all', config)
    },
    createBoard(data: BoardCreateData) {
        return api.post<ApiResponse<BoardDetail>>('/boards', data)
    },
    updateBoard(boardUrl: string, data: BoardUpdateData) {
        return api.put<ApiResponse<BoardDetail>>(`/boards/${encodePathSegment(boardUrl)}`, data)
    },
    deleteBoard(boardUrl: string) {
        return api.delete<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}`)
    },
}
