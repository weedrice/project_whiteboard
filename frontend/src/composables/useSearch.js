import { useQuery } from '@tanstack/vue-query'
import { searchApi } from '@/api/search'
import { computed } from 'vue'

export function useSearch() {

    const useSearchPosts = (params) => {
        return useQuery({
            queryKey: ['search', 'posts', params],
            queryFn: async () => {
                const { data } = await searchApi.searchPosts(params.value)
                return data.data
            },
            enabled: computed(() => !!params.value.q),
            keepPreviousData: true
        })
    }

    // Mock implementation for popular keywords until backend is ready
    const usePopularKeywords = () => {
        return useQuery({
            queryKey: ['search', 'popular'],
            queryFn: async () => {
                // Simulate API call
                await new Promise(resolve => setTimeout(resolve, 500))
                return [
                    { keyword: 'Vue 3', count: 120 },
                    { keyword: 'Tailwind', count: 95 },
                    { keyword: 'Vite', count: 80 },
                    { keyword: 'Pinia', count: 65 },
                    { keyword: 'JavaScript', count: 50 }
                ]
            },
            staleTime: 1000 * 60 * 5 // 5 minutes
        })
    }

    return {
        useSearchPosts,
        usePopularKeywords
    }
}
