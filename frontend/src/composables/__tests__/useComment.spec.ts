import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { commentApi } from '@/api/comment'
import { apiDataResponse, apiSuccessResponse } from '@/test/apiResponseFixtures'
import { useComment } from '../useComment'

vi.mock('@/api/comment', () => ({
    commentApi: {
        getComments: vi.fn(),
        getReplies: vi.fn(),
        createComment: vi.fn(),
        updateComment: vi.fn(),
        deleteComment: vi.fn(),
    },
}))

const mockInvalidateQueries = vi.fn()
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
    useMutation: vi.fn((options) => ({
        mutate: async (variables: unknown) => {
            const result = await options.mutationFn(variables)
            options.onSuccess?.(result, variables)
            return result
        },
        mutateAsync: async (variables: unknown) => {
            const result = await options.mutationFn(variables)
            options.onSuccess?.(result, variables)
            return result
        },
        isLoading: ref(false),
        error: ref(null),
    })),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries,
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

        expect(options.queryKey).toEqual(['comments', postId, params])
        expect((options.enabled as { value: boolean }).value).toBe(true)
        expect((options.placeholderData as (prev: unknown) => unknown)('keep')).toBe('keep')
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

        expect(options.queryKey).toEqual(['comments', 'replies', parentId, params])
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
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post'] })
    })

    it('calls commentApi.updateComment and invalidates comments queries', async () => {
        const { useUpdateComment } = useComment()
        const mutation = useUpdateComment()

        vi.mocked(commentApi.updateComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.updateComment>())

        await mutation.mutateAsync({
            commentId: 5,
            data: { content: 'Updated' },
        })

        expect(commentApi.updateComment).toHaveBeenCalledWith(5, { content: 'Updated' })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments'] })
    })

    it('calls commentApi.deleteComment and invalidates related queries', async () => {
        const { useDeleteComment } = useComment()
        const mutation = useDeleteComment()

        vi.mocked(commentApi.deleteComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.deleteComment>())

        await mutation.mutateAsync(10)

        expect(commentApi.deleteComment).toHaveBeenCalledWith(10)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments'] })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post'] })
    })
})
