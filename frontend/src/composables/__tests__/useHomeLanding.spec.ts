import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useQuery } from '@tanstack/vue-query'
import { useHomeLanding } from '../useHomeLanding'
import { postApi } from '@/api/post'

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options: Record<string, unknown>) => ({
        data: ref({
            featuredPost: { postId: 1, boardUrl: 'free', boardName: 'Free', authorName: 'A', title: 'Featured', viewCount: 1, likeCount: 0, commentCount: 0, isNotice: false, isNsfw: false, isSpoiler: false, createdAt: '2025-01-01' },
            editorPicks: [
                { postId: 2, boardUrl: 'free', boardName: 'Free', authorName: 'B', title: 'Pick', viewCount: 2, likeCount: 0, commentCount: 0, isNotice: false, isNsfw: false, isSpoiler: false, createdAt: '2025-01-01' },
            ],
            trendingPosts: [],
            liveActivity: [
                { postId: 3, boardUrl: 'free', boardName: 'Free', authorName: 'C', title: 'Live', viewCount: 3, likeCount: 1, commentCount: 1, isNotice: false, isNsfw: false, isSpoiler: false, createdAt: '2025-01-01' },
            ],
            boards: [{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10, postCount: 24 }],
            stats: { boardCount: 1, postCount: 2, liveCount: 1, onlineCount: 4, postsToday: 2, postsTodayDeltaPercent: null, activeBoardCount: 1, newMembersLast24Hours: 0, commentsToday: 1 },
        }),
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

describe('useHomeLanding', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(postApi.getHomeLanding).mockResolvedValue({
            data: { data: null },
        } as never)
    })

    it('maps the aggregated home landing contract into feed-ready sections', () => {
        const landing = useHomeLanding()

        expect(landing.isError.value).toBe(false)
        expect(landing.featured.value?.postId).toBe(1)
        expect(landing.editorPicks.value).toHaveLength(1)
        expect(landing.liveActivity.value[0]?.postId).toBe(3)
        expect(landing.spotlightBoards.value[0]?.boardUrl).toBe('free')
        expect(landing.stats.value.boardCount).toBe(1)
        expect(landing.selectedPeriod.value).toBe('24h')
        expect(vi.mocked(useQuery).mock.calls[0]?.[0]).toEqual(expect.objectContaining({
            placeholderData: expect.any(Function),
        }))
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
