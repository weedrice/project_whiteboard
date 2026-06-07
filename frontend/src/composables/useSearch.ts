import { useQuery } from '@tanstack/vue-query'
import { searchApi } from '@/api/search'
import type { BoardSearchItem, IntegratedSearchResponse, IntegratedSearchResultGroup, PostSummary, SearchParams } from '@/types'
import { computed, type Ref } from 'vue'
import { unwrapAxiosApiData } from '@/api/response'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { searchQueryKeys } from '@/composables/searchQueryKeys'

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
        return useQuery({
            queryKey: searchQueryKeys.posts(params),
            queryFn: async () => {
                return unwrapAxiosApiData(await searchApi.searchPosts(params.value))
            },
            enabled: computed(() => hasSearchText(params.value.q) || hasSearchText(params.value.keyword)),
            placeholderData: (previousData) => previousData // keepPreviousData renamed/changed in v5
        })
    }

    const useIntegratedSearch = (params: Ref<SearchParams>) => {
        return useQuery({
            queryKey: searchQueryKeys.integrated(params),
            queryFn: async () => {
                return toSearchPageViewModel(unwrapAxiosApiData(await searchApi.search(params.value)))
            },
            enabled: computed(() => hasSearchText(params.value.q)),
            placeholderData: (previousData) => previousData
        })
    }

    const usePopularKeywords = () => {
        return useQuery({
            queryKey: searchQueryKeys.popular,
            queryFn: async () => {
                return unwrapAxiosApiData(await searchApi.getPopularKeywords())
            },
            staleTime: QUERY_STALE_TIME.MEDIUM // 5 minutes
        })
    }

    return {
        useSearchPosts,
        useIntegratedSearch,
        usePopularKeywords
    }
}
