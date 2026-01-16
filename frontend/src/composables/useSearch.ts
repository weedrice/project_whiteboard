import { useQuery } from '@tanstack/vue-query'
import { searchApi } from '@/api/search'
import type { SearchParams, PopularKeyword } from '@/types'
import { computed, type Ref } from 'vue'
import { QUERY_STALE_TIME } from '@/utils/constants'

export function useSearch() {

    const useSearchPosts = (params: Ref<SearchParams>) => {
        return useQuery({
            queryKey: ['search', 'posts', params],
            queryFn: async () => {
                const { data } = await searchApi.searchPosts(params.value)
                return data.data
            },
            enabled: computed(() => !!params.value.q || !!params.value.keyword),
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
            enabled: computed(() => !!params.value.q),
            placeholderData: (previousData) => previousData
        })
    }

    // Mock implementation for popular keywords until backend is ready
    const usePopularKeywords = () => {
        return useQuery({
            queryKey: ['search', 'popular'],
            queryFn: async () => {
                // Simulate API call
                await new Promise(resolve => setTimeout(resolve, 500))
                const mockData: PopularKeyword[] = [
                    { keyword: 'Vue 3', count: 120 },
                    { keyword: 'Tailwind', count: 95 },
                    { keyword: 'Vite', count: 80 },
                    { keyword: 'Pinia', count: 65 },
                    { keyword: 'JavaScript', count: 50 }
                ]
                return mockData
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
