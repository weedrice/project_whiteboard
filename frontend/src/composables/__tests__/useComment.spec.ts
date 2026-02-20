import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useComment } from '../useComment'
import { commentApi } from '@/api/comment'

// Mock dependencies
vi.mock('@/api/comment', () => ({
    commentApi: {
        getComments: vi.fn(),
        createComment: vi.fn(),
        updateComment: vi.fn(),
        deleteComment: vi.fn()
    }
}))

// Mock vue-query
const mockInvalidateQueries = vi.fn()
const mockQueryOptions: Array<Record<string, unknown>> = []
vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options) => {
        mockQueryOptions.push(options)
        return {
            data: ref(null),
            isLoading: ref(false),
            error: ref(null),
            refetch: vi.fn()
        }
    }),
    useMutation: vi.fn((options) => {
        return {
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
            error: ref(null)
        }
    }),
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries
    }))
}))

describe('useComment', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockQueryOptions.length = 0
    })

    describe('useComments', () => {
        it('fetches comments with enabled/placeholder query options', async () => {
            vi.mocked(commentApi.getComments).mockResolvedValueOnce({
                data: {
                    data: {
                        content: [{ commentId: 1, content: 'hello' }],
                    },
                },
            } as never)

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
            expect(result).toEqual({ content: [{ commentId: 1, content: 'hello' }] })
        })

        it('disables comments query when postId is falsy', () => {
            const { useComments } = useComment()
            const postId = ref(0)
            const params = ref({ page: 0, size: 10 })

            useComments(postId, params)
            const options = mockQueryOptions[0]

            expect((options.enabled as { value: boolean }).value).toBe(false)
        })
    })

    describe('useCreateComment', () => {
        it('calls commentApi.createComment', async () => {
            const { useCreateComment } = useComment()
            const mutation = useCreateComment()

            const mockResponse = { data: { success: true, data: { commentId: 1 } } }
            vi.mocked(commentApi.createComment).mockResolvedValue(mockResponse as any)

            await mutation.mutateAsync({
                postId: 1,
                data: { content: 'Test comment' }
            })

            expect(commentApi.createComment).toHaveBeenCalledWith(1, { content: 'Test comment' })
        })

        it('invalidates queries on success', async () => {
            const { useCreateComment } = useComment()
            const mutation = useCreateComment()

            vi.mocked(commentApi.createComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({
                postId: 123,
                data: { content: 'New comment' }
            })

            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post'] })
        })
    })

    describe('useUpdateComment', () => {
        it('calls commentApi.updateComment', async () => {
            const { useUpdateComment } = useComment()
            const mutation = useUpdateComment()

            vi.mocked(commentApi.updateComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({
                commentId: 5,
                data: { content: 'Updated content' }
            })

            expect(commentApi.updateComment).toHaveBeenCalledWith(5, { content: 'Updated content' })
        })

        it('invalidates comments queries on success', async () => {
            const { useUpdateComment } = useComment()
            const mutation = useUpdateComment()

            vi.mocked(commentApi.updateComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({
                commentId: 5,
                data: { content: 'Updated' }
            })

            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments'] })
        })
    })

    describe('useDeleteComment', () => {
        it('calls commentApi.deleteComment', async () => {
            const { useDeleteComment } = useComment()
            const mutation = useDeleteComment()

            vi.mocked(commentApi.deleteComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync(10)

            expect(commentApi.deleteComment).toHaveBeenCalledWith(10)
        })

        it('invalidates queries on success', async () => {
            const { useDeleteComment } = useComment()
            const mutation = useDeleteComment()

            vi.mocked(commentApi.deleteComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync(10)

            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments'] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post'] })
        })
    })
})
