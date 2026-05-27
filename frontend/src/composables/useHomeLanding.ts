import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { emptyHomeLanding, postApi } from '@/api/post'
import { useAuthStore } from '@/stores/auth'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { toFeedPost, toFeedPosts } from '@/utils/postViewModel'
import type { HomeLandingPeriod } from '@/types'

export function useHomeLanding() {
    const authStore = useAuthStore()
    const selectedPeriod = ref<HomeLandingPeriod>('24h')
    const isReadyToFetch = computed(() => !authStore.isAuthenticated || authStore.user != null)
    const authCacheKey = computed(() => authStore.isAuthenticated ? (authStore.user?.userId ?? 'member') : 'guest')

    const landingQuery = useQuery({
        queryKey: computed(() => ['home', 'landing', selectedPeriod.value, authCacheKey.value]),
        enabled: isReadyToFetch,
        queryFn: async ({ queryKey }) => {
            const [, , period] = queryKey as ['home', 'landing', HomeLandingPeriod, string | number]
            const { data } = await postApi.getHomeLanding(period)
            return data.data
        },
        placeholderData: previousData => previousData,
        staleTime: QUERY_STALE_TIME.SHORT,
    })

    const landing = computed(() => landingQuery.data.value ?? emptyHomeLanding())
    const isPendingAuthHydration = computed(() => authStore.isAuthenticated && authStore.user == null)
    const isLoading = computed(() => landingQuery.isLoading.value || isPendingAuthHydration.value)
    const featuredPost = computed(() => landing.value.featuredPost ?? null)
    const editorPickPosts = computed(() => landing.value.editorPicks ?? [])
    const trendingPosts = computed(() => landing.value.trendingPosts ?? [])
    const liveActivityPosts = computed(() => landing.value.liveActivityPosts ?? [])
    const posts = computed(() => toFeedPosts([
        ...(featuredPost.value ? [featuredPost.value] : []),
        ...trendingPosts.value,
    ]))

    return {
        featured: computed(() => featuredPost.value ? toFeedPost(featuredPost.value) : null),
        editorPicks: computed(() => toFeedPosts(editorPickPosts.value)),
        trending: computed(() => toFeedPosts(trendingPosts.value)),
        liveActivity: computed(() => toFeedPosts(liveActivityPosts.value)),
        spotlightBoards: computed(() => landing.value.boards),
        boards: computed(() => landing.value.boards),
        stats: computed(() => landing.value.stats),
        selectedPeriod,
        setPeriod: (period: HomeLandingPeriod) => {
            selectedPeriod.value = period
        },
        posts,
        isLoading,
        isFetching: computed(() => landingQuery.isFetching.value),
        isError: computed(() => landingQuery.isError.value),
        isBoardsLoading: isLoading,
        isBoardsError: computed(() => landingQuery.isError.value),
        error: computed(() => landingQuery.error.value ?? null),
        refetch: async () => {
            await landingQuery.refetch()
        },
    }
}
