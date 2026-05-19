import { useQuery } from '@tanstack/vue-query'
import { searchApi } from '@/api/search'
import type { SearchParams } from '@/types'
import { computed, type Ref } from 'vue'
import { QUERY_STALE_TIME } from '@/utils/constants'

const hasSearchText = (value?: string) => !!value?.trim()

export function useSearch() {

    const useSearchPosts = (params: Ref<SearchParams>) => {
        return useQuery({
            queryKey: ['search', 'posts', params],
            queryFn: async () => {
                const { data } = await searchApi.searchPosts(params.value)
                return data.data
            },
            enabled: computed(() => hasSearchText(params.value.q) || hasSearchText(params.value.keyword)),
            placeholderData: (previousData) => previousData // keepPreviousData renamed/changed in v5
        })
    }

    const useIntegratedSearch = (params: Ref<SearchParams>) => {
        return useQuery({
            queryKey: ['search', 'integrated', params],
            queryFn: async () => {
                const { data } = await searchApi.search(params.value)
                return data.data
            },
            enabled: computed(() => hasSearchText(params.value.q)),
            placeholderData: (previousData) => previousData
        })
    }

    const usePopularKeywords = () => {
        return useQuery({
            queryKey: ['search', 'popular'],
            queryFn: async () => {
                const { data } = await searchApi.getPopularKeywords()
                return data.data
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
