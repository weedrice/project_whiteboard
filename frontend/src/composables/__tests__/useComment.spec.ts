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
vi.mock('@tanstack/vue-query', () => ({
    useQuery: vi.fn((options) => {
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
    })

    describe('useComments', () => {
        it('returns query hooks', () => {
            const { useComments } = useComment()
            const postId = ref(1)
            const params = ref({ page: 0, size: 10 })

            const result = useComments(postId, params)

            expect(result).toHaveProperty('data')
            expect(result).toHaveProperty('isLoading')
            expect(result).toHaveProperty('error')
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
                data: { contents: 'Test comment' }
            })

            expect(commentApi.createComment).toHaveBeenCalledWith(1, { contents: 'Test comment' })
        })

        it('invalidates queries on success', async () => {
            const { useCreateComment } = useComment()
            const mutation = useCreateComment()

            vi.mocked(commentApi.createComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({
                postId: 123,
                data: { contents: 'New comment' }
            })

            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['comments', 123] })
            expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 123] })
        })
    })

    describe('useUpdateComment', () => {
        it('calls commentApi.updateComment', async () => {
            const { useUpdateComment } = useComment()
            const mutation = useUpdateComment()

            vi.mocked(commentApi.updateComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({
                commentId: 5,
                data: { contents: 'Updated content' }
            })

            expect(commentApi.updateComment).toHaveBeenCalledWith(5, { contents: 'Updated content' })
        })

        it('invalidates comments queries on success', async () => {
            const { useUpdateComment } = useComment()
            const mutation = useUpdateComment()

            vi.mocked(commentApi.updateComment).mockResolvedValue({ data: { success: true } } as any)

            await mutation.mutateAsync({
                commentId: 5,
                data: { contents: 'Updated' }
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
