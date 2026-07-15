import api from './index'
import { mapApiDataResponse } from '@/api/response'
import {
    normalizePostSummary,
    normalizePostSummaryPage,
    type PostSummaryWire,
} from '@/api/postContract'
import type { AxiosRequestConfig } from 'axios'
import type {
    ApiResponse,
    PopularKeyword,
    RecentSearchResponse,
    SearchParams,
    SemanticSearchResult,
    IntegratedSearchResponse
} from '@/types'
import type { PageResponseRaw } from '@/utils/pageResponse'

interface PopularKeywordResponse {
    keywords: Array<{
        keyword: string
        count: number
    }>
}

const toPopularKeywords = (response: PopularKeywordResponse): PopularKeyword[] =>
    response.keywords.map(({ keyword, count }) => ({ keyword, count }))

type IntegratedSearchResponseWire = Omit<IntegratedSearchResponse, 'postResults'> & {
    postResults: Omit<IntegratedSearchResponse['postResults'], 'items'> & {
        items: PostSummaryWire[]
    }
}

const normalizeIntegratedSearch = (response: IntegratedSearchResponseWire): IntegratedSearchResponse => ({
    ...response,
    postResults: {
        ...response.postResults,
        items: response.postResults.items.map(normalizePostSummary),
    },
})

export const searchApi = {
    // General search
    search: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<IntegratedSearchResponseWire>>('/search', { ...config, params })
            .then((response) => mapApiDataResponse(response, normalizeIntegratedSearch)),

    // Search posts
    searchPosts: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponseRaw<PostSummaryWire>>>('/search/posts', { ...config, params })
            .then((response) => mapApiDataResponse(response, normalizePostSummaryPage)),

    // Semantic search
    semanticSearch: (params: SearchParams, config?: AxiosRequestConfig) =>
        api.get<ApiResponse<PageResponseRaw<SemanticSearchResult>>>('/search/semantic', { ...config, params }),

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
