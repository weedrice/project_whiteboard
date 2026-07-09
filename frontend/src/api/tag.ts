import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, PageResponse, PostSummary, TagResponse } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

export interface TagSuggestionRequest {
    title?: string
    contents?: string
    boardUrl?: string
    existingTags?: string[]
}

export interface TagSuggestionResponse {
    suggestions: string[]
}

export const tagApi = {
    getPopularTags(config?: AxiosRequestConfig) {
        return api.get<ApiResponse<TagResponse>>('/tags', config)
    },
    getPostsByTagName(tagName: string, params: { page?: number; size?: number; sort?: string }, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponse<PostSummary>>>(`/tags/${encodePathSegment(tagName)}/posts`, {
            ...config,
            params,
        })
    },
    suggestTags(data: TagSuggestionRequest, config?: AxiosRequestConfig) {
        return api.post<ApiResponse<TagSuggestionResponse>>('/tags/suggestions', data, config)
    },
}
