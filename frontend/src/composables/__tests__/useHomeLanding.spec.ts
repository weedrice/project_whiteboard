import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useHomeLanding } from '../useHomeLanding'
import { postApi } from '@/api/post'

const mocks = vi.hoisted(() => {
    const queryResults: Array<Record<string, unknown>> = []
    return {
        queryResults,
    }
})

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options: Record<string, unknown>) => {
        const queryKey = options.queryKey as string[]
        if (queryKey.includes('trending')) {
            return {
                data: ref([{ postId: 1, boardUrl: 'free', boardName: 'Free', authorName: 'A', title: 'Post', viewCount: 1, likeCount: 0, commentCount: 0, isNotice: false, isNsfw: false, isSpoiler: false, createdAt: '2025-01-01' }]),
                isLoading: ref(false),
                isError: ref(false),
                error: ref(null),
                refetch: vi.fn(),
            }
        }

        return {
            data: ref([]),
            isLoading: ref(false),
            isError: ref(true),
            error: ref(new Error('boards failed')),
            refetch: vi.fn(),
        }
    }),
}))

vi.mock('@/api/post', () => ({
    postApi: {
        getTrendingPosts: vi.fn(),
    },
}))

vi.mock('@/composables/useBoard', () => ({
    useBoard: () => ({
        useBoards: () => ({
            data: ref([{ boardId: 1, boardUrl: 'free', boardName: 'Free', subscriberCount: 10 }]),
            isLoading: ref(false),
            isError: ref(true),
            error: ref(new Error('boards failed')),
            refetch: vi.fn(),
        }),
    }),
}))

describe('useHomeLanding', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        vi.mocked(postApi.getTrendingPosts).mockResolvedValue({
            data: { data: [] },
        } as never)
    })

    it('keeps landing content available when boards query fails', () => {
        const landing = useHomeLanding()

        expect(landing.isError.value).toBe(false)
        expect(landing.featured.value?.postId).toBe(1)
        expect(landing.spotlightBoards.value).toEqual([])
        expect(landing.isBoardsError.value).toBe(true)
    })
})
