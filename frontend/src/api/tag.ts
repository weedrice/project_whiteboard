import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, PageResponse, PostSummary, TagResponse } from '@/types'
import { encodePathSegment } from '@/utils/urlPath'

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
}
