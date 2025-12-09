import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { commentApi } from '@/api/comment'
import { computed } from 'vue'

export function useComment() {
    const queryClient = useQueryClient()

    // Fetch comments for a post
    const useComments = (postId, params) => {
        return useQuery({
            queryKey: ['comments', postId, params],
            queryFn: async () => {
                const { data } = await commentApi.getComments(postId.value, params.value)
                return data.data
            },
            enabled: computed(() => !!postId.value),
            keepPreviousData: true
        })
    }

    // Create a new comment
    const useCreateComment = () => {
        return useMutation({
            mutationFn: async ({ postId, data }) => {
                return await commentApi.createComment(postId, data)
            },
            onSuccess: (_, { postId }) => {
                queryClient.invalidateQueries(['comments', postId])
                queryClient.invalidateQueries(['post', postId]) // Update comment count in post detail
            }
        })
    }

    // Update a comment
    const useUpdateComment = () => {
        return useMutation({
            mutationFn: async ({ commentId, data }) => {
                return await commentApi.updateComment(commentId, data)
            },
            onSuccess: (_, { commentId }) => {
                // We might need postId to invalidate the list, but for now we can invalidate all comments
                // Ideally, we should pass postId to onSuccess or return it from backend
                queryClient.invalidateQueries(['comments'])
            }
        })
    }

    // Delete a comment
    const useDeleteComment = () => {
        return useMutation({
            mutationFn: async (commentId) => {
                return await commentApi.deleteComment(commentId)
            },
            onSuccess: () => {
                queryClient.invalidateQueries(['comments'])
                // Also need to invalidate post to update comment count, but we need postId
                queryClient.invalidateQueries(['post'])
            }
        })
    }

    return {
        useComments,
        useCreateComment,
        useUpdateComment,
        useDeleteComment
    }
}
