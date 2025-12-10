import api from './index'

export interface SearchParams {
    q?: string;
    keyword?: string;
    page?: number;
    size?: number;
    type?: string;
    sort?: string;
    boardUrl?: string;
}

export interface PopularKeyword {
    keyword: string;
    count: number;
}

export const searchApi = {
    // General search
    search: (params: SearchParams) => api.get('/search', { params }),

    // Search posts
    searchPosts: (params: SearchParams) => api.get('/search/posts', { params }),

    // Get popular keywords
    getPopularKeywords: () => api.get('/search/popular-keywords')
}
