import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import { commentApi } from '@/api/comment'
import { apiDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { updateCommentLikeCache, useComment } from '../useComment'

vi.mock('@/stores/auth', () => ({
    useAuthStore: () => ({ sessionGeneration: 0 }),
}))

vi.mock('@/api/comment', () => ({
    commentApi: {
        getComments: vi.fn(),
        getReplies: vi.fn(),
        createComment: vi.fn(),
        updateComment: vi.fn(),
        deleteComment: vi.fn(),
        likeComment: vi.fn(),
        unlikeComment: vi.fn(),
    },
}))

const mockInvalidateQueries = vi.fn()
const mockCancelQueries = vi.fn()
const mockGetQueriesData = vi.fn((): Array<[readonly unknown[], unknown]> => [])
const mockSetQueriesData = vi.fn()
const mockSetQueryData = vi.fn()
const mockQueryOptions: Array<Record<string, unknown>> = []

vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options) => {
        mockQueryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn(),
        }
    }),
    useInfiniteQuery: vi.fn((options) => {
        mockQueryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            fetchNextPage: vi.fn(),
        }
    }),
    useMutation: vi.fn((options) => {
        const execute = async (variables: unknown) => {
            const context = await options.onMutate?.(variables)
            try {
                const result = await options.mutationFn(variables)
                options.onSuccess?.(result, variables, context)
                options.onSettled?.(result, null, variables, context)
                return result
            } catch (error) {
                options.onError?.(error, variables, context)
                options.onSettled?.(undefined, error, variables, context)
                throw error
            }
        }

        return {
        mutate: execute,
        mutateAsync: execute,
        isLoading: ref(false),
        isPending: ref(false),
        error: ref(null),
    }}),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries,
        cancelQueries: mockCancelQueries,
        getQueriesData: mockGetQueriesData,
        setQueriesData: mockSetQueriesData,
        setQueryData: mockSetQueryData,
    })),
}))

describe('useComment', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockQueryOptions.length = 0
    })

    it('fetches comments with enabled/placeholder query options', async () => {
        vi.mocked(commentApi.getComments).mockResolvedValueOnce(
            apiDataResponse<typeof commentApi.getComments>({
                content: [{ commentId: 1, content: 'hello' }],
            })
        )

        const { useComments } = useComment()
        const postId = ref(1)
        const params = ref({ page: 0, size: 10 })

        useComments(postId, params)
        const options = mockQueryOptions[0]
        const result = await (options.queryFn as () => Promise<unknown>)()

        const queryKey = options.queryKey as ReturnType<typeof computed>
        expect(queryKey.value).toEqual(['session', 0, 'comments', 'post', 1, { page: 0, size: 10 }])
        expect((options.enabled as { value: boolean }).value).toBe(true)
        expect(options.placeholderData).toBeUndefined()
        expect(commentApi.getComments).toHaveBeenCalledWith(1, { page: 0, size: 10 })
        expect(result).toEqual({
            content: [{ commentId: 1, content: 'hello' }],
            empty: false,
            first: true,
            last: true,
            number: 0,
            size: 1,
            totalElements: 1,
            totalPages: 1,
        })

        postId.value = 2
        params.value = { page: 1, size: 20 }
        expect(queryKey.value).toEqual(['session', 0, 'comments', 'post', 2, { page: 1, size: 20 }])
    })

    it('normalizes backend pages before calculating the next infinite comment page', async () => {
        vi.mocked(commentApi.getComments).mockResolvedValueOnce(
            apiDataResponse<typeof commentApi.getComments>({
                content: [{ commentId: 1, content: 'hello' }],
                page: 1,
                size: 10,
                totalElements: 21,
                totalPages: 3,
                hasNext: true,
                hasPrevious: true,
            })
        )

        const { useInfiniteComments } = useComment()
        useInfiniteComments(ref(1), ref({ page: 0, size: 10 }))
        const options = mockQueryOptions[0]
        const page = await (options.queryFn as (context: { pageParam: number, signal?: AbortSignal }) => Promise<{
            number: number
            last: boolean
        }>)({ pageParam: 1 })
        const getNextPageParam = options.getNextPageParam as (page: { number: number, last: boolean }) => number | undefined

        expect(page).toMatchObject({ number: 1, last: false })
        expect(getNextPageParam(page)).toBe(2)
        expect(getNextPageParam({ number: 2, last: true })).toBeUndefined()
    })

    it('fetches replies with a dedicated query key', async () => {
        vi.mocked(commentApi.getReplies).mockResolvedValueOnce(
            apiDataResponse<typeof commentApi.getReplies>({
                content: [{ commentId: 2, content: 'reply' }],
                totalElements: 1,
            })
        )

        const { useReplies } = useComment()
        const parentId = ref(10)
        const params = ref({ page: 0, size: 10 })
        const enabled = ref(true)

        useReplies(parentId, params, enabled)
        const options = mockQueryOptions[0]
        const result = await (options.queryFn as () => Promise<unknown>)()

        expect((options.queryKey as ReturnType<typeof computed>).value).toEqual([
            'session',
            0,
            'comments',
            'replies',
            10,
            { page: 0, size: 10 },
        ])
        expect((options.enabled as { value: boolean }).value).toBe(true)
        expect(commentApi.getReplies).toHaveBeenCalledWith(10, { page: 0, size: 10 })
        expect(result).toEqual({ content: [{ commentId: 2, content: 'reply' }], totalElements: 1 })
    })

    it('disables replies query when the toggle is off', () => {
        const { useReplies } = useComment()
        const parentId = ref(10)
        const params = ref({ page: 0, size: 10 })
        const enabled = ref(false)

        useReplies(parentId, params, enabled)
        const options = mockQueryOptions[0]

        expect((options.enabled as { value: boolean }).value).toBe(false)
    })

    it('calls commentApi.createComment and invalidates related queries', async () => {
        const { useCreateComment } = useComment()
        const mutation = useCreateComment()

        vi.mocked(commentApi.createComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.createComment>())

        await mutation.mutateAsync({
            postId: 123,
            data: { content: 'New comment' },
        })

        expect(commentApi.createComment).toHaveBeenCalledWith(123, { content: 'New comment' })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments', 'post', 123] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments', 'replies'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'post', 123] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'posts'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'board', 'posts'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'home'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'search'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', 'me', 'comments'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'user', 'points'] })
        expect(mockInvalidateQueries).not.toHaveBeenCalledWith({ queryKey: ['comments'] })
        expect(mockInvalidateQueries).not.toHaveBeenCalledWith({ queryKey: ['post'] })
    })

    it('calls commentApi.updateComment and invalidates comments queries', async () => {
        const { useUpdateComment } = useComment()
        const mutation = useUpdateComment()

        vi.mocked(commentApi.updateComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.updateComment>())

        await mutation.mutateAsync({
            commentId: 5,
            postId: 123,
            data: { content: 'Updated' },
        })

        expect(commentApi.updateComment).toHaveBeenCalledWith(5, { content: 'Updated' })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments', 'post', 123] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments', 'replies'] })
        expect(mockInvalidateQueries).not.toHaveBeenCalledWith({ queryKey: ['comments'] })
    })

    it('calls commentApi.deleteComment and invalidates related queries', async () => {
        const { useDeleteComment } = useComment()
        const mutation = useDeleteComment()

        vi.mocked(commentApi.deleteComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.deleteComment>())

        await mutation.mutateAsync({ commentId: 10, postId: 123 })

        expect(commentApi.deleteComment).toHaveBeenCalledWith(10)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments', 'post', 123] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments', 'replies'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'post', 123] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'posts'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'home'] })
        expect(mockInvalidateQueries).not.toHaveBeenCalledWith({ queryKey: ['comments'] })
        expect(mockInvalidateQueries).not.toHaveBeenCalledWith({ queryKey: ['post'] })
    })

    it('updates matching comments across infinite pages, replies, and children', () => {
        const cache = {
            pages: [
                {
                    content: [
                        {
                            commentId: 1,
                            likeCount: 2,
                            liked: false,
                            children: [{ commentId: 2, likeCount: 0, liked: false }],
                        },
                    ],
                },
            ],
        }

        const liked = updateCommentLikeCache(cache, 1, true) as typeof cache
        const childLiked = updateCommentLikeCache(liked, 2, true) as typeof cache

        expect(liked.pages[0].content[0]).toMatchObject({ liked: true, likeCount: 3 })
        expect(childLiked.pages[0].content[0].children[0]).toMatchObject({ liked: true, likeCount: 1 })
        expect(updateCommentLikeCache(childLiked, 2, false)).toMatchObject({
            pages: [{ content: [{ children: [{ liked: false, likeCount: 0 }] }] }],
        })
    })

    it('likes and unlikes comments, then refreshes all related comment caches', async () => {
        vi.mocked(commentApi.likeComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.likeComment>())
        vi.mocked(commentApi.unlikeComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.unlikeComment>())
        const { useToggleCommentLike } = useComment()
        const mutation = useToggleCommentLike()

        await mutation.mutateAsync({ commentId: 7, postId: 123, liked: true })
        await mutation.mutateAsync({ commentId: 7, postId: 123, liked: false })

        expect(commentApi.likeComment).toHaveBeenCalledWith(7)
        expect(commentApi.unlikeComment).toHaveBeenCalledWith(7)
        expect(mockCancelQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments'] })
        expect(mockSetQueriesData).toHaveBeenCalled()
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments', 'post', 123] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments'] })
    })

    it('restores comment query snapshots when a like request fails', async () => {
        const failure = new Error('like failed')
        const snapshot = { content: [{ commentId: 7, liked: false, likeCount: 2 }] }
        mockGetQueriesData.mockReturnValueOnce([[['comments', 'post', 123], snapshot]])
        vi.mocked(commentApi.likeComment).mockRejectedValueOnce(failure)
        const { useToggleCommentLike } = useComment()
        const mutation = useToggleCommentLike()

        await expect(mutation.mutateAsync({ commentId: 7, postId: 123, liked: true })).rejects.toThrow('like failed')

        expect(mockSetQueryData).toHaveBeenCalledWith(['comments', 'post', 123], snapshot)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['session', 0, 'comments'] })
    })
})
