import api from './index'
import { mapApiDataResponse } from '@/api/response'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, PageResponse, PostSummary, PopularKeyword, SearchParams, IntegratedSearchResponse } from '@/types'

interface PopularKeywordResponse {
    keywords: Array<{
        keyword: string
        count: number
    }>
}

const toPopularKeywords = (response: PopularKeywordResponse): PopularKeyword[] =>
    response.keywords.map(({ keyword, count }) => ({ keyword, count }))

export const searchApi = {
    // General search
    search: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<IntegratedSearchResponse>>('/search', { ...config, params }),

    // Search posts
    searchPosts: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<PostSummary>>>('/search/posts', { ...config, params }),

    // Get popular keywords
    async getPopularKeywords(config?: AxiosRequestConfig) {
        const response = config
            ? await api.get<ApiResponse<PopularKeywordResponse>>('/search/popular', config)
            : await api.get<ApiResponse<PopularKeywordResponse>>('/search/popular')
        return mapApiDataResponse(response, toPopularKeywords)
    }
}
