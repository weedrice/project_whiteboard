import { describe, it, expect, vi, beforeEach } from 'vitest'
import { usePost } from '../usePost'
import { ref } from 'vue'
import { postApi } from '@/api/post'

// Mock vue-query
const mockInvalidateQueries = vi.fn()
vi.mock('@tanstack/vue-query', () => ({
    useQueryClient: vi.fn(() => ({
        invalidateQueries: mockInvalidateQueries
    })),
    useQuery: vi.fn(({ queryFn }) => {
        // Execute queryFn immediately for testing
        const data = queryFn()
        return { data: ref(data) }
    }),
    useMutation: vi.fn(({ mutationFn, onSuccess }) => {
        return {
            mutate: async (variables: any) => {
                const result = await mutationFn(variables)
                if (onSuccess) onSuccess(result, variables, undefined)
                return result
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
        unscrapPost: vi.fn().mockResolvedValue({ success: true })
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

        // Wait for the async queryFn to resolve in the mock
        const resolvedData = await data.value
        expect(resolvedData).toEqual({ id: 1, title: 'Test Post' })
        expect(postApi.getPost).toHaveBeenCalledWith(1)
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
})
