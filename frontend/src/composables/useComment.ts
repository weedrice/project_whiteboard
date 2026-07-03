import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { commentApi, type CommentParams, type CommentPayload } from '@/api/comment'
import { commentQueryKeys } from '@/composables/commentQueryKeys'
import { postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'
import type { Comment, CommentListResponse } from '@/types'

type CommentMutationWithPostId<TVariables extends object = object> = TVariables & {
    postId: string | number
}

type UpdateCommentVariables = CommentMutationWithPostId<{
    commentId: string | number
    data: CommentPayload
}>

type DeleteCommentVariables = string | number | {
    commentId: string | number
    postId?: string | number
}

export function useComment() {
    const queryClient = useQueryClient()

    const invalidatePostCommentQueries = (postId: string | number) => {
        queryClient.invalidateQueries({ queryKey: commentQueryKeys.postRoot(postId) })
    }

    const invalidatePostDetailQueries = (postId: string | number) => {
        queryClient.invalidateQueries({ queryKey: postQueryKeys.detailPrefix(postId) })
    }

    const invalidateCommentMutationTargets = (postId?: string | number, includePostDetail = false) => {
        if (postId !== undefined) {
            invalidatePostCommentQueries(postId)
            if (includePostDetail) {
                invalidatePostDetailQueries(postId)
            }
            return
        }

        queryClient.invalidateQueries({ queryKey: commentQueryKeys.all })
        if (includePostDetail) {
            queryClient.invalidateQueries({ queryKey: postQueryKeys.detailsRoot })
        }
    }

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
            onSuccess: (_result, variables) => {
                invalidateCommentMutationTargets(variables.postId, true)
            },
        })
    }

    const useUpdateComment = () => {
        return useMutation({
            mutationFn: async ({ commentId, data }: UpdateCommentVariables) => {
                return await commentApi.updateComment(commentId, data)
            },
            onSuccess: (_result, variables) => {
                invalidateCommentMutationTargets(variables.postId)
            },
        })
    }

    const useDeleteComment = () => {
        return useMutation({
            mutationFn: async (variables: DeleteCommentVariables) => {
                const commentId = typeof variables === 'object' ? variables.commentId : variables
                return await commentApi.deleteComment(commentId)
            },
            onSuccess: (_result, variables) => {
                const postId = typeof variables === 'object' ? variables.postId : undefined
                invalidateCommentMutationTargets(postId, true)
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
