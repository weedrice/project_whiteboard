import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { emptyHomeLanding, postApi } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import { useAuthStore } from '@/stores/auth'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { toFeedPost, toFeedPosts } from '@/utils/postViewModel'
import type { HomeLandingPeriod } from '@/types'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { optionalQuerySignal } from '@/utils/querySignal'
import { AUTH_SCOPED_QUERY_META, currentSessionQueryKey } from '@/queryAuthScope'

export function useHomeLanding() {
    const authStore = useAuthStore()
    const selectedPeriod = ref<HomeLandingPeriod>('24h')
    const isReadyToFetch = computed(() => !authStore.isAuthenticated || authStore.user != null)

    const landingQuery = useQuery({
        queryKey: computed(() => currentSessionQueryKey(
            authStore,
            homeQueryKeys.landing(selectedPeriod.value, 'viewer'),
        )),
        enabled: isReadyToFetch,
        queryFn: async ({ signal }) => {
            const requestConfig = optionalQuerySignal(undefined, { signal })
            const response = requestConfig
                ? await postApi.getHomeLanding(selectedPeriod.value, requestConfig)
                : await postApi.getHomeLanding(selectedPeriod.value)
            return unwrapAxiosApiData(response)
        },
        staleTime: QUERY_STALE_TIME.SHORT,
        meta: AUTH_SCOPED_QUERY_META,
    })

    const landing = computed(() => landingQuery.data.value ?? emptyHomeLanding())
    const isPendingAuthHydration = computed(() => authStore.isAuthenticated && authStore.user == null)
    const isLoading = computed(() => landingQuery.isLoading.value || isPendingAuthHydration.value)
    const curatedPosts = computed(() => landing.value.curatedPosts ?? [])
    const latestPosts = computed(() => landing.value.latestPosts ?? [])
    const featuredPost = computed(() => curatedPosts.value[0] ?? null)
    const editorPickPosts = computed(() => curatedPosts.value.slice(1, 4))
    const trendingPosts = computed(() => curatedPosts.value.slice(1, 10))
    const liveActivityPosts = computed(() => latestPosts.value.slice(0, 6))
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
