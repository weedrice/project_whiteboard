import api from '@/api'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, TagResponse } from '@/types'
import { mapApiDataResponse } from '@/api/response'
import { normalizePostSummaryPage, type PostSummaryWire } from '@/api/postContract'
import type { PageResponseRaw } from '@/utils/pageResponse'
import { encodePathSegment } from '@/utils/urlPath'

export const tagApi = {
    getPopularTags(config?: AxiosRequestConfig) {
        return api.get<ApiResponse<TagResponse>>('/tags', config)
    },
    getPostsByTagName(tagName: string, params: { page?: number; size?: number; sort?: string }, config?: AxiosRequestConfig) {
        return api.get<ApiResponse<PageResponseRaw<PostSummaryWire>>>(`/tags/${encodePathSegment(tagName)}/posts`, {
            ...config,
            params,
        }).then((response) => mapApiDataResponse(response, normalizePostSummaryPage))
    },
}
