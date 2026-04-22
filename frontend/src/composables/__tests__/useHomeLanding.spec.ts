import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useQuery } from '@tanstack/vue-query'
import { useHomeLanding } from '../useHomeLanding'
import { postApi } from '@/api/post'

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn(() => ({
        data: ref({
            featuredPost: { postId: 1, boardUrl: 'free', boardName: 'Free', authorName: 'A', title: 'Featured', viewCount: 1, likeCount: 0, commentCount: 0, isNotice: false, isNsfw: false, isSpoiler: false, createdAt: '2025-01-01' },
            editorPicks: [
                { postId: 2, boardUrl: 'free', boardName: 'Free', authorName: 'B', title: 'Pick', viewCount: 2, likeCount: 0, commentCount: 0, isNotice: false, isNsfw: false, isSpoiler: false, createdAt: '2025-01-01' },
            ],
            trendingPosts: [],
            liveActivity: [
                { postId: 3, boardUrl: 'free', boardName: 'Free', authorName: 'C', title: 'Live', viewCount: 3, likeCount: 1, commentCount: 1, isNotice: false, isNsfw: false, isSpoiler: false, createdAt: '2025-01-01' },
            ],
            boards: [{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10 }],
            stats: { boardCount: 1, postCount: 2, liveCount: 1 },
        }),
        isLoading: ref(false),
        isError: ref(false),
        error: ref(null),
        refetch: vi.fn(),
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
        expect(vi.mocked(useQuery).mock.calls[0]?.[0]).toEqual(expect.objectContaining({
            placeholderData: undefined,
        }))
    })
})
