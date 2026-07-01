import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { commentApi, type CommentParams, type CommentPayload } from '@/api/comment'
import { commentQueryKeys } from '@/composables/commentQueryKeys'
import { postQueryKeys } from '@/composables/postQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'
import type { Comment, CommentListResponse } from '@/types'

export function useComment() {
    const queryClient = useQueryClient()

    const useComments = (postId: Ref<string | number>, params: Ref<CommentParams>) => {
        return useApiPageQuery<Comment>({
            queryKey: commentQueryKeys.list(postId, params),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => commentApi.getComments(postId.value, params.value),
                (config) => commentApi.getComments(postId.value, params.value, config),
            ),
            enabled: computed(() => !!postId.value),
        })
    }

    const useReplies = (
        parentId: Ref<string | number>,
        params: Ref<CommentParams>,
        enabled?: Ref<boolean>,
    ) => {
        return useApiQuery<CommentListResponse>({
            queryKey: commentQueryKeys.replies(parentId, params),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => commentApi.getReplies(parentId.value, params.value),
                (config) => commentApi.getReplies(parentId.value, params.value, config),
            ),
            enabled: computed(() => Boolean(parentId.value) && (enabled ? enabled.value : true)),
            keepPreviousData: true,
        })
    }

    const useCreateComment = () => {
        return useMutation({
            mutationFn: async ({ postId, data }: { postId: string | number, data: CommentPayload }) => {
                return await commentApi.createComment(postId, data)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: commentQueryKeys.all })
                queryClient.invalidateQueries({ queryKey: postQueryKeys.detailsRoot })
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
                queryClient.invalidateQueries({ queryKey: postQueryKeys.detailsRoot })
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
