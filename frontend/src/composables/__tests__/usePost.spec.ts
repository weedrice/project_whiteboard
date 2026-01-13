import { describe, it, expect, vi, beforeEach } from 'vitest'
import { usePost } from '../usePost'
import { ref } from 'vue'
import { postApi } from '@/api/post'

// Mock vue-query
const mockInvalidateQueries = vi.fn()
const mockCancelQueries = vi.fn()
const mockGetQueryData = vi.fn()
const mockSetQueryData = vi.fn()
vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries,
        cancelQueries: mockCancelQueries,
        getQueryData: mockGetQueryData,
        setQueryData: mockSetQueryData
    })),
    useQuery: vi.fn(({ queryFn }) => {
        // Execute queryFn immediately for testing (handle async)
        const dataRef = ref(null)
        Promise.resolve(queryFn()).then(value => {
            dataRef.value = value
        })
        return { data: dataRef, isLoading: ref(false), error: ref(null) }
    }),
    useMutation: vi.fn(({ mutationFn, onMutate, onSuccess, onError, onSettled }) => {
        return {
            mutate: async (variables: any) => {
                let context
                let error: any
                try {
                    if (onMutate) {
                        context = await onMutate(variables)
                    }
                    const result = await mutationFn(variables)
                    if (onSuccess) onSuccess(result, variables, context)
                    return result
                } catch (err) {
                    error = err
                    if (onError) onError(error, variables, context)
                    throw error
                } finally {
                    if (onSettled) onSettled(undefined, error, variables, context)
                }
            }
        }
    })
}))

// Mock postApi
vi.mock('@/api/post', () => ({
    postApi: {
        getPost: vi.fn().mockResolvedValue({ data: { data: { id: 1, title: 'Test Post' } } }),
        createPost: vi.fn().mockResolvedValue({ data: { id: 2, title: 'New Post' } }),
        updatePost: vi.fn().mockResolvedValue({ data: { id: 1, title: 'Updated Post' } }),
        deletePost: vi.fn().mockResolvedValue({ success: true }),
        likePost: vi.fn().mockResolvedValue({ success: true }),
        unlikePost: vi.fn().mockResolvedValue({ success: true }),
        scrapPost: vi.fn().mockResolvedValue({ success: true }),
        unscrapPost: vi.fn().mockResolvedValue({ success: true }),
        reportPost: vi.fn().mockResolvedValue({ data: { success: true } })
    }
}))

describe('usePost', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('fetches post detail', async () => {
        const { usePostDetail } = usePost()
        const postId = ref(1)
        const { data } = usePostDetail(postId)

        // Wait for the async queryFn to resolve
        await new Promise(resolve => setTimeout(resolve, 10))
        expect(data.value).toEqual({ id: 1, title: 'Test Post' })
        expect(postApi.getPost).toHaveBeenCalledWith(1, { params: { incrementView: false } })
    })

    it('creates a post', async () => {
        const { useCreatePost } = usePost()
        const mutation = useCreatePost()

        const result = await mutation.mutate({ boardUrl: 'free', data: { title: 'New Post' } })
        expect(result).toEqual({ data: { id: 2, title: 'New Post' } })
        expect(postApi.createPost).toHaveBeenCalledWith('free', { title: 'New Post' })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['board', 'free', 'posts'] })
    })

    it('updates a post', async () => {
        const { useUpdatePost } = usePost()
        const mutation = useUpdatePost()

        const result = await mutation.mutate({ postId: 1, data: { title: 'Updated Post' } })
        expect(result).toEqual({ data: { id: 1, title: 'Updated Post' } })
        expect(postApi.updatePost).toHaveBeenCalledWith(1, { title: 'Updated Post' })
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
    })

    it('deletes a post', async () => {
        const { useDeletePost } = usePost()
        const mutation = useDeletePost()

        await mutation.mutate(1)
        expect(postApi.deletePost).toHaveBeenCalledWith(1)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['board'] })
    })

    it('likes a post', async () => {
        const { useLikePost } = usePost()
        const mutation = useLikePost()

        await mutation.mutate(1)
        expect(postApi.likePost).toHaveBeenCalledWith(1)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
    })

    it('unlikes a post', async () => {
        const { useUnlikePost } = usePost()
        const mutation = useUnlikePost()

        await mutation.mutate(1)
        expect(postApi.unlikePost).toHaveBeenCalledWith(1)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
    })

    it('scraps a post', async () => {
        const { useScrapPost } = usePost()
        const mutation = useScrapPost()

        await mutation.mutate(1)
        expect(postApi.scrapPost).toHaveBeenCalledWith(1)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
    })

    it('unscraps a post', async () => {
        const { useUnscrapPost } = usePost()
        const mutation = useUnscrapPost()

        await mutation.mutate(1)
        expect(postApi.unscrapPost).toHaveBeenCalledWith(1)
        expect(mockInvalidateQueries).toHaveBeenCalledWith({ queryKey: ['post', 1] })
    })

    it('reports a post', async () => {
        const reportData = { targetType: 'POST', targetId: 1, reason: 'Spam' }
        vi.mocked(postApi.reportPost).mockResolvedValue({ data: { success: true } } as any)

        const { useReportPost } = usePost()
        const mutation = useReportPost()

        await mutation.mutate(reportData)
        expect(postApi.reportPost).toHaveBeenCalledWith(reportData)
    })
})
