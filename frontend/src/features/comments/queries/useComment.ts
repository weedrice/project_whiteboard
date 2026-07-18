import { useInfiniteQuery, useMutation, useQueryClient, type QueryKey } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import { commentApi, type CommentParams, type CommentPayload } from '@/api/comment'
import { unwrapAxiosApiPageData } from '@/api/response'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import { postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'
import type { Comment, CommentListResponse } from '@/types'
import { useAuthStore } from '@/stores/auth'
import {
    AUTH_SCOPED_QUERY_META,
    currentSessionQueryKey,
    isSessionGenerationCurrent,
} from '@/queryAuthScope'

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

type CommentLikeVariables = {
    commentId: string | number
    postId: string | number
    liked: boolean
}

type CommentCacheSnapshot = [QueryKey, unknown]

export function updateCommentLikeCache(
    value: unknown,
    commentId: string | number,
    liked: boolean,
): unknown {
    if (Array.isArray(value)) {
        return value.map((item) => updateCommentLikeCache(item, commentId, liked))
    }

    if (!value || typeof value !== 'object') {
        return value
    }

    const record = value as Record<string, unknown>
    let nextRecord = record

    if (String(record.commentId ?? '') === String(commentId)) {
        const previousLiked = Boolean(record.liked)
        const previousCount = Number(record.likeCount ?? 0)
        nextRecord = {
            ...record,
            liked,
            likeCount: previousLiked === liked
                ? previousCount
                : Math.max(0, previousCount + (liked ? 1 : -1)),
        }
    }

    for (const key of ['content', 'children', 'pages']) {
        const nested = nextRecord[key]
        if (!Array.isArray(nested)) continue

        const updated = updateCommentLikeCache(nested, commentId, liked)
        if (updated !== nested) {
            nextRecord = nextRecord === record ? { ...record } : nextRecord
            nextRecord[key] = updated
        }
    }

    return nextRecord
}

export function useComment() {
    const queryClient = useQueryClient()
    const authStore = useAuthStore()
    const authKey = (queryKey: readonly unknown[]) => currentSessionQueryKey(authStore, queryKey)
    const captureMutationSession = () => ({ sessionGeneration: authStore.sessionGeneration })
    const isCurrentMutation = (context?: { sessionGeneration: number }) => (
        context !== undefined && isSessionGenerationCurrent(authStore, context.sessionGeneration)
    )

    const invalidatePostCommentQueries = (postId: string | number) => {
        queryClient.invalidateQueries({ queryKey: authKey(commentQueryKeys.postRoot(postId)) })
    }

    const invalidateReplyQueries = () => {
        queryClient.invalidateQueries({ queryKey: authKey(commentQueryKeys.repliesRoot) })
    }

    const invalidatePostDetailQueries = (postId: string | number) => {
        queryClient.invalidateQueries({ queryKey: authKey(postQueryKeys.detailPrefix(postId)) })
    }

    const invalidateCommentMutationTargets = (postId?: string | number, includePostDetail = false) => {
        if (postId !== undefined) {
            invalidatePostCommentQueries(postId)
            if (includePostDetail) {
                invalidatePostDetailQueries(postId)
            }
            return
        }

        queryClient.invalidateQueries({ queryKey: authKey(commentQueryKeys.all) })
        if (includePostDetail) {
            queryClient.invalidateQueries({ queryKey: authKey(postQueryKeys.detailsRoot) })
        }
    }

    const invalidateCommentCountConsumers = () => {
        queryClient.invalidateQueries({ queryKey: authKey(postQueryKeys.lists) })
        queryClient.invalidateQueries({ queryKey: authKey(postQueryKeys.boardPostsRoot) })
        queryClient.invalidateQueries({ queryKey: authKey(homeQueryKeys.all) })
        queryClient.invalidateQueries({ queryKey: authKey(['search']) })
        queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.myCommentsRoot) })
        const currentUserId = authStore.user?.userId
        if (currentUserId != null) {
            queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.publicCommentsRoot(currentUserId)) })
            queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.profile(currentUserId)) })
        }
    }

    const useComments = (postId: Ref<string | number>, params: Ref<CommentParams>) => {
        return useApiPageQuery<Comment>({
            queryKey: computed(() => commentQueryKeys.list(postId.value, params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => commentApi.getComments(postId.value, params.value),
                (config) => commentApi.getComments(postId.value, params.value, config),
            ),
            enabled: computed(() => !!postId.value),
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useInfiniteComments = (postId: Ref<string | number>, params: Ref<CommentParams>) => {
        return useInfiniteQuery({
            queryKey: computed(() => authKey([
                ...commentQueryKeys.postRoot(postId.value),
                'infinite',
                { size: params.value.size, sort: params.value.sort },
            ] as const)),
            initialPageParam: 0,
            queryFn: async ({ pageParam, signal }) => unwrapAxiosApiPageData(await commentApi.getComments(
                postId.value,
                { ...params.value, page: Number(pageParam) },
                { signal },
            )),
            getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.number + 1,
            enabled: computed(() => !!postId.value),
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useBestComments = (postId: Ref<string | number>) => {
        return useApiQuery<Comment[]>({
            queryKey: computed(() => [...commentQueryKeys.postRoot(postId.value), 'best'] as const),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => commentApi.getBestComments(postId.value),
                (config) => commentApi.getBestComments(postId.value, config),
            ),
            enabled: computed(() => !!postId.value),
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useReplies = (
        parentId: Ref<string | number>,
        params: Ref<CommentParams>,
        enabled?: Ref<boolean>,
    ) => {
        return useApiQuery<CommentListResponse>({
            queryKey: computed(() => commentQueryKeys.replies(parentId.value, params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => commentApi.getReplies(parentId.value, params.value),
                (config) => commentApi.getReplies(parentId.value, params.value, config),
            ),
            enabled: computed(() => Boolean(parentId.value) && (enabled ? enabled.value : true)),
            keepPreviousData: true,
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useCreateComment = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ postId, data }: { postId: string | number, data: CommentPayload }) => {
                return await commentApi.createComment(postId, data)
            },
            onSuccess: (_result, variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateCommentMutationTargets(variables.postId, true)
                invalidateReplyQueries()
                invalidateCommentCountConsumers()
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.pointsRoot) })
            },
        })
    }

    const useUpdateComment = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ commentId, data }: UpdateCommentVariables) => {
                return await commentApi.updateComment(commentId, data)
            },
            onSuccess: (_result, variables, context) => {
                if (!isCurrentMutation(context)) return
                invalidateCommentMutationTargets(variables.postId)
                invalidateReplyQueries()
            },
        })
    }

    const useDeleteComment = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (variables: DeleteCommentVariables) => {
                const commentId = typeof variables === 'object' ? variables.commentId : variables
                return await commentApi.deleteComment(commentId)
            },
            onSuccess: (_result, variables, context) => {
                if (!isCurrentMutation(context)) return
                const postId = typeof variables === 'object' ? variables.postId : undefined
                invalidateCommentMutationTargets(postId, true)
                invalidateReplyQueries()
                invalidateCommentCountConsumers()
            },
        })
    }

    const useToggleCommentLike = () => {
        return useMutation({
            mutationFn: async ({ commentId, liked }: CommentLikeVariables) => {
                return liked
                    ? await commentApi.likeComment(commentId)
                    : await commentApi.unlikeComment(commentId)
            },
            onMutate: async (variables): Promise<{
                snapshots: CommentCacheSnapshot[]
                sessionGeneration: number
            }> => {
                const sessionGeneration = authStore.sessionGeneration
                const commentsKey = authKey(commentQueryKeys.all)
                await queryClient.cancelQueries({ queryKey: commentsKey })
                const snapshots = queryClient.getQueriesData({ queryKey: commentsKey }) as CommentCacheSnapshot[]

                queryClient.setQueriesData(
                    { queryKey: commentsKey },
                    (current) => updateCommentLikeCache(current, variables.commentId, variables.liked),
                )

                return { snapshots, sessionGeneration }
            },
            onError: (_error, _variables, context) => {
                if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
                context?.snapshots.forEach(([queryKey, value]) => {
                    queryClient.setQueryData(queryKey, value)
                })
            },
            onSettled: (_data, _error, variables, context) => {
                if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
                invalidatePostCommentQueries(variables.postId)
                queryClient.invalidateQueries({ queryKey: authKey(commentQueryKeys.all) })
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
        useToggleCommentLike,
    }
}
