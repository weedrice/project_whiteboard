import api from './index'
import type { ApiResponse, PageResponse, PostSummary, PopularKeyword, SearchParams } from '@/types'

export const searchApi = {
    // General search
    search: (params: SearchParams) => api.get<ApiResponse<PageResponse<PostSummary>>>('/search', { params }),

    // Search posts
    searchPosts: (params: SearchParams) => api.get<ApiResponse<PageResponse<PostSummary>>>('/search/posts', { params }),

    // Get popular keywords
    getPopularKeywords: () => api.get<ApiResponse<PopularKeyword[]>>('/search/popular-keywords')
}
