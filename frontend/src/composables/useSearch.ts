import { searchApi } from '@/api/search'
import type {
    BoardSearchItem,
    IntegratedSearchResponse,
    IntegratedSearchResultGroup,
    PostSummary,
    RecentSearchResponse,
    SearchParams,
    SemanticSearchResult
} from '@/types'
import { computed, type ComputedRef, type Ref } from 'vue'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { searchQueryKeys } from '@/composables/searchQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'

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
            queryKey: computed(() => searchQueryKeys.posts(params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => searchApi.searchPosts(params.value),
                (config) => searchApi.searchPosts(params.value, config),
            ),
            enabled: computed(() => hasSearchText(params.value.q) || hasSearchText(params.value.keyword)),
        })
    }

    const useSemanticSearch = (params: Ref<SearchParams>) => {
        return useApiPageQuery<SemanticSearchResult>({
            queryKey: computed(() => searchQueryKeys.semantic(params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => searchApi.semanticSearch(params.value),
                (config) => searchApi.semanticSearch(params.value, config),
            ),
            enabled: computed(() => hasSearchText(params.value.q)),
        })
    }

    const useIntegratedSearch = (params: Ref<SearchParams>) => {
        return useApiQuery<IntegratedSearchResponse, SearchPageViewModel>({
            queryKey: computed(() => searchQueryKeys.integrated(params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => searchApi.search(params.value),
                (config) => searchApi.search(params.value, config),
            ),
            selectData: toSearchPageViewModel,
            enabled: computed(() => hasSearchText(params.value.q)),
            keepPreviousData: true,
        })
    }

    const usePopularKeywords = () => {
        return useApiQuery({
            queryKey: searchQueryKeys.popular,
            request: (context) => callWithOptionalQuerySignal(
                context,
                searchApi.getPopularKeywords,
                searchApi.getPopularKeywords,
            ),
            staleTime: QUERY_STALE_TIME.MEDIUM // 5 minutes
        })
    }

    const useRecentSearches = (enabled: Ref<boolean> | ComputedRef<boolean>) => {
        return useApiQuery<RecentSearchResponse>({
            queryKey: searchQueryKeys.recent,
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => searchApi.getRecentSearches({ page: 0, size: 8 }),
                (config) => searchApi.getRecentSearches({ page: 0, size: 8 }, config),
            ),
            enabled,
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    return {
        useSearchPosts,
        useIntegratedSearch,
        useSemanticSearch,
        usePopularKeywords,
        useRecentSearches,
    }
}
