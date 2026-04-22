import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { postApi } from '@/api/post'
import { useBoard } from '@/composables/useBoard'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { FeedPost, PageResponse, PostSummary } from '@/types'

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

const unwrapTrendingResponse = (payload: PageResponse<PostSummary> | PostSummary[]): PostSummary[] => {
    if (Array.isArray(payload)) {
        return payload
    }

    return payload.content ?? []
}

export function useHomeLanding() {
    const { useBoards } = useBoard()
    const trendingQuery = useQuery({
        queryKey: ['home', 'landing', 'trending'],
        queryFn: async () => {
            const { data } = await postApi.getTrendingPosts(0, 16)
            return unwrapTrendingResponse(data.data)
        },
        staleTime: QUERY_STALE_TIME.SHORT,
    })

    const boardsQuery = useBoards()

    const posts = computed<FeedPost[]>(() => {
        return (trendingQuery.data.value ?? [])
            .map(mapToFeedPost)
            .filter((post): post is FeedPost => post != null)
    })

    const boards = computed(() => boardsQuery.data.value ?? [])

    const featured = computed(() => posts.value[0] ?? null)
    const editorPicks = computed(() => posts.value.slice(1, 4))
    const trending = computed(() => posts.value.slice(4, 10))
    const liveActivity = computed(() => posts.value.slice(0, 6))
    const spotlightBoards = computed(() => {
        if (boardsQuery.isError.value) return []
        return boards.value.slice(0, 6)
    })

    const isLoading = computed(() => trendingQuery.isLoading.value)
    const isError = computed(() => trendingQuery.isError.value)
    const error = computed(() => trendingQuery.error.value ?? null)
    const isBoardsLoading = computed(() => boardsQuery.isLoading.value)
    const isBoardsError = computed(() => boardsQuery.isError.value)

    return {
        featured,
        editorPicks,
        trending,
        liveActivity,
        spotlightBoards,
        posts,
        boards,
        isLoading,
        isError,
        isBoardsLoading,
        isBoardsError,
        error,
        refetch: async () => {
            await Promise.all([trendingQuery.refetch(), boardsQuery.refetch()])
        },
    }
}
