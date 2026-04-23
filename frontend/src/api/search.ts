import api from './index'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse, PageResponse, PostSummary, PopularKeyword, SearchParams, IntegratedSearchResponse } from '@/types'

export const searchApi = {
    // General search
    search: (params: SearchParams) => api.get<ApiResponse<IntegratedSearchResponse>>('/search', { params }),

    // Search posts
    searchPosts: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<PostSummary>>>('/search/posts', { ...config, params }),

    // Get popular keywords
    getPopularKeywords: () => api.get<ApiResponse<PopularKeyword[]>>('/search/popular-keywords')
}
