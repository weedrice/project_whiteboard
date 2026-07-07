import api from './index'
import { mapApiDataResponse } from '@/api/response'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    PageResponse,
    PostSummary,
    PopularKeyword,
    RecentSearchResponse,
    SearchParams,
    SemanticSearchResult,
    IntegratedSearchResponse
} from '@/types'

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

    // Semantic search
    semanticSearch: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponse<SemanticSearchResult>>>('/search/semantic', { ...config, params }),

    // Get popular keywords
    async getPopularKeywords(config?: AxiosRequestConfig) {
        const response = config
            ? await api.get<ApiResponse<PopularKeywordResponse>>('/search/popular', config)
            : await api.get<ApiResponse<PopularKeywordResponse>>('/search/popular')
        return mapApiDataResponse(response, toPopularKeywords)
    },

    // Get recent search logs
    getRecentSearches: (params: Pick<SearchParams, 'page' | 'size'>, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<RecentSearchResponse>>('/search/recent', { ...config, params }),

    deleteRecentSearch: (logId: number | string) =>
        api.delete<ApiResponse<void>>(`/search/recent/${encodeURIComponent(String(logId))}`),

    deleteAllRecentSearches: () =>
        api.delete<ApiResponse<void>>('/search/recent'),
}
