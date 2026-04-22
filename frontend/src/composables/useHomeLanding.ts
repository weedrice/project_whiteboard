import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { postApi } from '@/api/post'
import { useAuthStore } from '@/stores/auth'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { FeedPost, HomeLandingResponse, PostSummary } from '@/types'

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

const emptyLanding = (): HomeLandingResponse => ({
    featuredPost: null,
    editorPicks: [],
    trendingPosts: [],
    liveActivity: [],
    boards: [],
    stats: {
        boardCount: 0,
        postCount: 0,
        liveCount: 0,
    },
})

export function useHomeLanding() {
    const authStore = useAuthStore()
    const landingQuery = useQuery({
        queryKey: computed(() => ['home', 'landing', authStore.isAuthenticated ? (authStore.user?.userId ?? 'auth') : 'guest']),
        queryFn: async () => {
            const { data } = await postApi.getHomeLanding()
            return data.data
        },
        placeholderData: undefined,
        staleTime: QUERY_STALE_TIME.SHORT,
    })

    const landing = computed(() => landingQuery.data.value ?? emptyLanding())
    const posts = computed(() => mapPosts([
        ...(landing.value.featuredPost ? [landing.value.featuredPost] : []),
        ...landing.value.editorPicks,
        ...landing.value.trendingPosts,
    ]))

    return {
        featured: computed(() => landing.value.featuredPost ? mapToFeedPost(landing.value.featuredPost) : null),
        editorPicks: computed(() => mapPosts(landing.value.editorPicks)),
        trending: computed(() => mapPosts(landing.value.trendingPosts)),
        liveActivity: computed(() => mapPosts(landing.value.liveActivity)),
        spotlightBoards: computed(() => landing.value.boards),
        boards: computed(() => landing.value.boards),
        stats: computed(() => landing.value.stats),
        posts,
        isLoading: computed(() => landingQuery.isLoading.value),
        isError: computed(() => landingQuery.isError.value),
        isBoardsLoading: computed(() => landingQuery.isLoading.value),
        isBoardsError: computed(() => landingQuery.isError.value),
        error: computed(() => landingQuery.error.value ?? null),
        refetch: async () => {
            await landingQuery.refetch()
        },
    }
}
