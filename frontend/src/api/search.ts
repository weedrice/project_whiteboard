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
    search: (params: SearchParams) => api.get<ApiResponse<IntegratedSearchResponse>>('/search', { params }),

    // Search posts
    searchPosts: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<PostSummary>>>('/search/posts', { ...config, params }),

    // Get popular keywords
    async getPopularKeywords() {
        const response = await api.get<ApiResponse<PopularKeywordResponse>>('/search/popular')
        return mapApiDataResponse(response, toPopularKeywords)
    }
}
