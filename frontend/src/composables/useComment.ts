import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { commentApi, type CommentParams, type CommentPayload } from '@/api/comment'

const commentQueryKeys = {
    all: ['comments'] as const,
    list: (postId: Ref<string | number>, params: Ref<CommentParams>) => ['comments', postId, params] as const,
    replies: (parentId: Ref<string | number>, params: Ref<CommentParams>) =>
        ['comments', 'replies', parentId, params] as const,
}

const postQueryKeys = {
    all: ['post'] as const,
}

export function useComment() {
    const queryClient = useQueryClient()

    const useComments = (postId: Ref<string | number>, params: Ref<CommentParams>) => {
        return useQuery({
            queryKey: commentQueryKeys.list(postId, params),
            queryFn: async () => {
                const { data } = await commentApi.getComments(postId.value, params.value)
                return data.data
            },
            enabled: computed(() => !!postId.value),
            placeholderData: (previousData) => previousData,
        })
    }

    const useReplies = (
        parentId: Ref<string | number>,
        params: Ref<CommentParams>,
        enabled?: Ref<boolean>,
    ) => {
        return useQuery({
            queryKey: commentQueryKeys.replies(parentId, params),
            queryFn: async () => {
                const { data } = await commentApi.getReplies(parentId.value, params.value)
                return data.data
            },
            enabled: computed(() => Boolean(parentId.value) && (enabled ? enabled.value : true)),
            placeholderData: (previousData) => previousData,
        })
    }

    const useCreateComment = () => {
        return useMutation({
            mutationFn: async ({ postId, data }: { postId: string | number, data: CommentPayload }) => {
                return await commentApi.createComment(postId, data)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: commentQueryKeys.all })
                queryClient.invalidateQueries({ queryKey: postQueryKeys.all })
            },
        })
    }

    const useUpdateComment = () => {
        return useMutation({
            mutationFn: async ({ commentId, data }: { commentId: string | number, data: CommentPayload }) => {
                return await commentApi.updateComment(commentId, data)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: commentQueryKeys.all })
            },
        })
    }

    const useDeleteComment = () => {
        return useMutation({
            mutationFn: async (commentId: string | number) => {
                return await commentApi.deleteComment(commentId)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: commentQueryKeys.all })
                queryClient.invalidateQueries({ queryKey: postQueryKeys.all })
            },
        })
    }

    return {
        useComments,
        useReplies,
        useCreateComment,
        useUpdateComment,
        useDeleteComment,
    }
}
