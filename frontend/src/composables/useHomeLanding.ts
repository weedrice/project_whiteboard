import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { postApi } from '@/api/post'
import { useAuthStore } from '@/stores/auth'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { FeedPost, HomeLandingPeriod, HomeLandingResponse, HomeLandingStats, PostSummary } from '@/types'

const EDITOR_PICK_START_INDEX = 1
const EDITOR_PICK_END_INDEX = 4
const TRENDING_START_INDEX = 1
const TRENDING_END_INDEX = 10
const LIVE_ACTIVITY_END_INDEX = 6

type LegacyHomeLandingResponse = Omit<HomeLandingResponse, 'posts'> & {
    posts?: PostSummary[]
    latestPosts?: PostSummary[]
    featuredPost?: PostSummary | null
    editorPicks?: PostSummary[]
    trendingPosts?: PostSummary[]
}

const mapToFeedPost = (post: PostSummary): FeedPost | null => {
    if (
        post.postId == null ||
        post.boardUrl == null ||
        post.boardName == null ||
        (post.authorName == null && post.author?.displayName == null)
    ) {
        return null
    }

    return {
        ...post,
        boardUrl: post.boardUrl,
        boardName: post.boardName,
        boardIconUrl: post.boardIconUrl,
        authorName: post.authorName ?? post.author?.displayName ?? '',
        liked: post.liked ?? false,
        scrapped: post.scrapped ?? false,
        subscribed: post.subscribed ?? false,
    }
}

const mapPosts = (posts: PostSummary[] | undefined): FeedPost[] => {
    return (posts ?? [])
        .map(mapToFeedPost)
        .filter((post): post is FeedPost => post != null)
}

const emptyStats = (): HomeLandingStats => ({
    boardCount: 0,
    postCount: 0,
    liveCount: 0,
    onlineCount: 0,
    postsToday: 0,
    postsTodayDeltaPercent: null,
    activeBoardCount: 0,
    newMembersLast24Hours: 0,
    commentsToday: 0,
})

const emptyLanding = (): HomeLandingResponse => ({
    posts: [],
    latestPosts: [],
    boards: [],
    stats: emptyStats(),
})

const normalizeLanding = (landing: HomeLandingResponse | LegacyHomeLandingResponse | null | undefined): HomeLandingResponse => {
    if (!landing) {
        return emptyLanding()
    }

    if (Array.isArray(landing.posts)) {
        return {
            posts: landing.posts,
            latestPosts: landing.latestPosts ?? [],
            boards: landing.boards ?? [],
            stats: landing.stats ?? emptyStats(),
        }
    }

    const legacyLanding = landing as LegacyHomeLandingResponse
    return {
        posts: [
            ...(legacyLanding.featuredPost ? [legacyLanding.featuredPost] : []),
            ...(legacyLanding.editorPicks ?? []),
            ...(legacyLanding.trendingPosts ?? []),
        ],
        latestPosts: legacyLanding.latestPosts ?? [],
        boards: legacyLanding.boards ?? [],
        stats: legacyLanding.stats ?? emptyStats(),
    }
}

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

    const landing = computed(() => normalizeLanding(landingQuery.data.value))
    const isPendingAuthHydration = computed(() => authStore.isAuthenticated && authStore.user == null)
    const isLoading = computed(() => landingQuery.isLoading.value || isPendingAuthHydration.value)
    const sourcePosts = computed(() => landing.value.posts ?? [])
    const sourceLatestPosts = computed(() => landing.value.latestPosts?.length
        ? landing.value.latestPosts
        : sourcePosts.value.slice(0, LIVE_ACTIVITY_END_INDEX))
    const featuredPost = computed(() => sourcePosts.value[0] ?? null)
    const editorPickPosts = computed(() => sourcePosts.value.slice(EDITOR_PICK_START_INDEX, EDITOR_PICK_END_INDEX))
    const trendingPosts = computed(() => sourcePosts.value.slice(TRENDING_START_INDEX, TRENDING_END_INDEX))
    const liveActivityPosts = computed(() => sourceLatestPosts.value.slice(0, LIVE_ACTIVITY_END_INDEX))
    const posts = computed(() => mapPosts(sourcePosts.value.slice(0, TRENDING_END_INDEX)))

    return {
        featured: computed(() => featuredPost.value ? mapToFeedPost(featuredPost.value) : null),
        editorPicks: computed(() => mapPosts(editorPickPosts.value)),
        trending: computed(() => mapPosts(trendingPosts.value)),
        liveActivity: computed(() => mapPosts(liveActivityPosts.value)),
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
