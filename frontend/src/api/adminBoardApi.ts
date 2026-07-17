import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import { mapApiDataResponse } from '@/api/response'
import {
    normalizeBoardDetail,
    normalizePostDetail,
    type BoardDetailWire,
    type PostDetailWire,
} from '@/api/postContract'
import type {
    AdminBoard,
    AdminInquirySummary,
    ApiResponse,
    BoardCreateData,
    BoardUpdateData,
} from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'
import { getWithOptionalConfig, type PaginationParams } from '@/api/adminTypes'

export const adminBoardApi = {
    getInquiryPosts(params: PaginationParams & { sort?: string }, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<AdminInquirySummary>>>('/admin/inquiries', { ...config, params })
    },
    getInquiryPost(postId: string | number, config?: AxiosRequestConfig) {
        const request = config
            ? api.get<ApiResponse<PostDetailWire>>(`/admin/inquiries/${encodePathSegment(postId)}`, config)
            : api.get<ApiResponse<PostDetailWire>>(`/admin/inquiries/${encodePathSegment(postId)}`)
        return request.then((response) => mapApiDataResponse(response, normalizePostDetail))
    },
    getBoards(config?: AxiosRequestConfig) {
        return getWithOptionalConfig<ApiResponse<AdminBoard[]>>('/boards/all', config)
    },
    createBoard(data: BoardCreateData) {
        return api.post<ApiResponse<BoardDetailWire>>('/boards', data)
            .then((response) => mapApiDataResponse(response, normalizeBoardDetail))
    },
    updateBoard(boardUrl: string, data: BoardUpdateData) {
        return api.put<ApiResponse<BoardDetailWire>>(`/boards/${encodePathSegment(boardUrl)}`, data)
            .then((response) => mapApiDataResponse(response, normalizeBoardDetail))
    },
    reorderBoards(boardIds: number[]) {
        return api.put<ApiResponse<AdminBoard[]>>('/admin/boards/order', { boardIds })
    },
    deleteBoard(boardUrl: string) {
        return api.delete<ApiResponse<void>>(`/boards/${encodePathSegment(boardUrl)}`)
    },
}
