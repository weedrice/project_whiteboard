import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useQuery } from '@tanstack/vue-query'
import { useHomeLanding } from '../useHomeLanding'
import { postApi } from '@/api/post'

const queryData = vi.hoisted(() => ({
    current: null as unknown,
}))

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options: Record<string, unknown>) => ({
        data: ref(queryData.current),
        isLoading: ref(false),
        isFetching: ref(false),
        isError: ref(false),
        error: ref(null),
        refetch: vi.fn(),
        options,
    })),
}))

vi.mock('@/api/post', () => ({
    postApi: {
        getHomeLanding: vi.fn(),
    },
}))

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({
        isAuthenticated: false,
        user: null,
    }),
}))

const makePost = (postId: number, title: string, authorName = `Author ${postId}`) => ({
    postId,
    boardUrl: 'free',
    boardName: 'Free',
    authorName,
    title,
    viewCount: postId,
    likeCount: 0,
    commentCount: 0,
    isNotice: false,
    isNsfw: false,
    isSpoiler: false,
    createdAt: '2025-01-01',
})

const makeStats = () => ({
    boardCount: 1,
    postCount: 2,
    liveCount: 1,
    onlineCount: 4,
    postsToday: 2,
    postsTodayDeltaPercent: null,
    activeBoardCount: 1,
    newMembersLast24Hours: 0,
    commentsToday: 1,
})

describe('useHomeLanding', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        queryData.current = {
            posts: [
                makePost(1, 'Featured', 'A'),
                makePost(2, 'Pick', 'B'),
                makePost(3, 'Live', 'C'),
                makePost(4, 'Pick 3', 'D'),
                makePost(5, 'Trend 1', 'E'),
                makePost(6, 'Trend 2', 'F'),
            ],
            boards: [{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10, postCount: 24 }],
            stats: makeStats(),
        }
        vi.mocked(postApi.getHomeLanding).mockResolvedValue({
            data: { data: null },
        } as never)
    })

    it('maps the aggregated home landing contract into feed-ready sections', () => {
        const landing = useHomeLanding()

        expect(landing.isError.value).toBe(false)
        expect(landing.featured.value?.postId).toBe(1)
        expect(landing.editorPicks.value.map(post => post.postId)).toEqual([2, 3, 4])
        expect(landing.trending.value.map(post => post.postId)).toEqual([5, 6])
        expect(landing.liveActivity.value.map(post => post.postId)).toEqual([1, 2, 3, 4, 5, 6])
        expect(landing.posts.value.map(post => post.postId)).toEqual([1, 2, 3, 4, 5, 6])
        expect(landing.spotlightBoards.value[0]?.boardUrl).toBe('free')
        expect(landing.stats.value.boardCount).toBe(1)
        expect(landing.selectedPeriod.value).toBe('24h')
        expect(vi.mocked(useQuery).mock.calls[0]?.[0]).toEqual(expect.objectContaining({
            placeholderData: expect.any(Function),
        }))
    })

    it('normalizes the previous section-shaped contract during frontend-first rollout', () => {
        queryData.current = {
            featuredPost: makePost(11, 'Featured legacy'),
            editorPicks: [
                makePost(12, 'Pick legacy 1'),
                makePost(13, 'Pick legacy 2'),
                makePost(14, 'Pick legacy 3'),
            ],
            trendingPosts: [
                makePost(15, 'Trend legacy 1'),
                makePost(16, 'Trend legacy 2'),
            ],
            liveActivity: [
                makePost(11, 'Featured legacy'),
                makePost(12, 'Pick legacy 1'),
            ],
            boards: [{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10, postCount: 24 }],
            stats: makeStats(),
        }

        const landing = useHomeLanding()

        expect(landing.featured.value?.postId).toBe(11)
        expect(landing.editorPicks.value.map(post => post.postId)).toEqual([12, 13, 14])
        expect(landing.trending.value.map(post => post.postId)).toEqual([15, 16])
        expect(landing.liveActivity.value.map(post => post.postId)).toEqual([11, 12, 13, 14, 15, 16])
    })

    it('forwards the selected period into the landing query function', async () => {
        const landing = useHomeLanding()
        const queryOptions = vi.mocked(useQuery).mock.calls[0]?.[0] as unknown as {
            queryFn: (context: { queryKey: ['home', 'landing', '24h' | '7d' | '30d', string | number] }) => Promise<unknown>
        }

        await queryOptions.queryFn({ queryKey: ['home', 'landing', '24h', 'guest'] })
        expect(postApi.getHomeLanding).toHaveBeenLastCalledWith('24h')

        landing.setPeriod('7d')
        await queryOptions.queryFn({ queryKey: ['home', 'landing', '7d', 'guest'] })
        expect(postApi.getHomeLanding).toHaveBeenLastCalledWith('7d')
    })
})
