import { searchApi } from '@/api/search'
import type { BoardSearchItem, IntegratedSearchResponse, IntegratedSearchResultGroup, PostSummary, SearchParams } from '@/types'
import { computed, type Ref } from 'vue'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { searchQueryKeys } from '@/composables/searchQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'

const hasSearchText = (value?: string) => !!value?.trim()

interface SearchPageResultPage {
    totalElements: number
    totalPages: number
    page: number
    size: number
    hasMore: boolean
}

export interface SearchPageViewModel {
    keyword: string
    postResults: PostSummary[]
    boardResults: BoardSearchItem[]
    postPage: SearchPageResultPage
}

const toSearchPageResultPage = <T>(resultGroup: IntegratedSearchResultGroup<T>): SearchPageResultPage => ({
    totalElements: resultGroup.totalElements,
    totalPages: resultGroup.totalPages,
    page: resultGroup.page,
    size: resultGroup.size,
    hasMore: resultGroup.hasMore
})

export const toSearchPageViewModel = (response: IntegratedSearchResponse): SearchPageViewModel => ({
    keyword: response.keyword,
    postResults: response.postResults.items,
    boardResults: response.boardResults,
    postPage: toSearchPageResultPage(response.postResults)
})

export function useSearch() {

    const useSearchPosts = (params: Ref<SearchParams>) => {
        return useApiPageQuery<PostSummary>({
            queryKey: searchQueryKeys.posts(params),
            request: () => searchApi.searchPosts(params.value),
            enabled: computed(() => hasSearchText(params.value.q) || hasSearchText(params.value.keyword)),
        })
    }

    const useIntegratedSearch = (params: Ref<SearchParams>) => {
        return useApiQuery<IntegratedSearchResponse, SearchPageViewModel>({
            queryKey: searchQueryKeys.integrated(params),
            request: () => searchApi.search(params.value),
            selectData: toSearchPageViewModel,
            enabled: computed(() => hasSearchText(params.value.q)),
            keepPreviousData: true,
        })
    }

    const usePopularKeywords = () => {
        return useApiQuery({
            queryKey: searchQueryKeys.popular,
            request: () => searchApi.getPopularKeywords(),
            staleTime: QUERY_STALE_TIME.MEDIUM // 5 minutes
        })
    }

    return {
        useSearchPosts,
        useIntegratedSearch,
        usePopularKeywords
    }
}
