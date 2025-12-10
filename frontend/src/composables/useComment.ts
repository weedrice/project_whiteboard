import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { commentApi, type CommentParams, type CommentPayload } from '@/api/comment'
import { computed, type Ref } from 'vue'

export function useComment() {
    const queryClient = useQueryClient()

    // Fetch comments for a post
    const useComments = (postId: Ref<string | number>, params: Ref<CommentParams>) => {
        return useQuery({
            queryKey: ['comments', postId, params],
            queryFn: async () => {
                const { data } = await commentApi.getComments(postId.value, params.value)
                return data.data
            },
            enabled: computed(() => !!postId.value),
            placeholderData: (previousData: any) => previousData
        })
    }

    // Create a new comment
    const useCreateComment = () => {
        return useMutation({
            mutationFn: async ({ postId, data }: { postId: string | number, data: CommentPayload }) => {
                return await commentApi.createComment(postId, data)
            },
            onSuccess: (_, { postId }) => {
                queryClient.invalidateQueries({ queryKey: ['comments', postId] })
                queryClient.invalidateQueries({ queryKey: ['post', postId] }) // Update comment count in post detail
            }
        })
    }

    // Update a comment
    const useUpdateComment = () => {
        return useMutation({
            mutationFn: async ({ commentId, data }: { commentId: string | number, data: CommentPayload }) => {
                return await commentApi.updateComment(commentId, data)
            },
            onSuccess: (_, { commentId }) => {
                // We might need postId to invalidate the list, but for now we can invalidate all comments
                // Ideally, we should pass postId to onSuccess or return it from backend
                queryClient.invalidateQueries({ queryKey: ['comments'] })
            }
        })
    }

    // Delete a comment
    const useDeleteComment = () => {
        return useMutation({
            mutationFn: async (commentId: string | number) => {
                return await commentApi.deleteComment(commentId)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['comments'] })
                // Also need to invalidate post to update comment count, but we need postId
                queryClient.invalidateQueries({ queryKey: ['post'] })
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
