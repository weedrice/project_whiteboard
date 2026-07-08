import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { commentApi, type CommentParams, type CommentPayload } from '@/api/comment'
import { unwrapAxiosApiData } from '@/api/response'
import { commentQueryKeys } from '@/composables/commentQueryKeys'
import { postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { userQueryKeys } from '@/composables/userQueryKeys'
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

    const useInfiniteComments = (postId: Ref<string | number>, params: Ref<CommentParams>) => {
        return useInfiniteQuery({
            queryKey: computed(() => [
                ...commentQueryKeys.postRoot(postId),
                'infinite',
                { size: params.value.size, sort: params.value.sort },
            ] as const),
            initialPageParam: 0,
            queryFn: async ({ pageParam, signal }) => unwrapAxiosApiData(await commentApi.getComments(
                postId.value,
                { ...params.value, page: Number(pageParam) },
                { signal },
            )),
            getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
            enabled: computed(() => !!postId.value),
        })
    }

    const useBestComments = (postId: Ref<string | number>) => {
        return useApiQuery<Comment[]>({
            queryKey: computed(() => [...commentQueryKeys.postRoot(postId), 'best'] as const),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => commentApi.getBestComments(postId.value),
                (config) => commentApi.getBestComments(postId.value, config),
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.pointsRoot })
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
        useInfiniteComments,
        useBestComments,
        useReplies,
        useCreateComment,
        useUpdateComment,
        useDeleteComment,
    }
}
