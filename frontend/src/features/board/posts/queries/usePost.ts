import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import { computed, type Ref } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import type { Post } from '@/types'
import { postApi, type PollVotePayload, type PostCreateData, type PostDraftData, type PostUpdateData, type ReportData, type ScheduledPostData } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import {
    invalidatePostCaches,
    invalidatePostCollectionCaches,
    restorePostCacheSnapshots,
    savePostCacheSnapshots,
    updatePostInAllCaches,
} from '@/features/board/posts/queries/postCacheUpdates'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { invalidateMyReportCaches } from '@/features/user/reports/reportCacheInvalidation'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'
import { postDetailQueryKey, postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'
import { useAuthStore } from '@/stores/auth'
import {
    AUTH_SCOPED_QUERY_META,
    currentSessionQueryKey,
    isSessionGenerationCurrent,
    sessionQueryKey,
} from '@/queryAuthScope'

export { postDetailQueryKey, postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'

export function usePost() {
    const queryClient = useQueryClient()
    const authStore = useAuthStore()
    const authKey = (queryKey: readonly unknown[]) => currentSessionQueryKey(authStore, queryKey)
    const captureMutationSession = () => ({ sessionGeneration: authStore.sessionGeneration })
    const isCurrentMutation = (context?: { sessionGeneration: number }) => (
        context !== undefined && isSessionGenerationCurrent(authStore, context.sessionGeneration)
    )

    const usePostDetail = (
        postId: Ref<string | number>,
        options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
    ) => {
        const { requestConfig, ...queryOptions } = options
        const incrementView = requestConfig?.params?.incrementView !== false

        return useQuery({
            queryKey: computed(() => authKey(postDetailQueryKey(postId.value, incrementView))),
            queryFn: async (context?: { signal?: AbortSignal }) => {
                const post = unwrapAxiosApiData(await postApi.getPost(postId.value, {
                    ...withQuerySignal(requestConfig, context),
                    params: {
                        incrementView: true,
                        ...(requestConfig?.params || {}),
                    },
                }))
                return post
            },
            enabled: computed(() => !!postId.value),
            meta: AUTH_SCOPED_QUERY_META,
            ...queryOptions,
        })
    }

    const useRelatedPosts = (
        postId: Ref<string | number>,
        options: { enabled?: Ref<boolean>, size?: number, requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
    ) => {
        const { requestConfig, size = 5, enabled, ...queryOptions } = options
        return useQuery({
            queryKey: computed(() => authKey(postQueryKeys.related(postId.value, size))),
            queryFn: async (context?: { signal?: AbortSignal }) => unwrapAxiosApiData(
                await postApi.getRelatedPosts(postId.value, size, withQuerySignal(requestConfig, context))
            ),
            enabled: computed(() => !!postId.value && (enabled?.value ?? true)),
            meta: AUTH_SCOPED_QUERY_META,
            ...queryOptions,
        })
    }

    const useScheduledPostDetail = (scheduledPostId: Ref<string | number>, enabled: Ref<boolean>) => {
        return useQuery({
            queryKey: computed(() => authKey(userQueryKeys.scheduledPost(scheduledPostId.value))),
            queryFn: async () => unwrapAxiosApiData(await postApi.getScheduledPost(scheduledPostId.value)),
            enabled: computed(() => Boolean(scheduledPostId.value) && enabled.value),
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const usePostVersions = (postId: Ref<string | number>, enabled: Ref<boolean>) => {
        return useQuery({
            queryKey: computed(() => authKey(postQueryKeys.versions(postId.value))),
            queryFn: async (context?: { signal?: AbortSignal }) => unwrapAxiosApiData(
                await postApi.getPostVersions(postId.value, withQuerySignal(
                    { skipGlobalErrorHandler: true },
                    context,
                )),
            ),
            enabled: computed(() => Boolean(postId.value) && enabled.value),
            retry: false,
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useCreatePost = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: PostCreateData }, mutationContext) => {
                return await postApi.createPost(boardUrl, data, mutationContext.requestConfig)
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                const sessionKey = (queryKey: readonly unknown[]) => (
                    sessionQueryKey(context.sessionGeneration, queryKey)
                )
                invalidatePostCollectionCaches(queryClient, context.sessionGeneration)
                queryClient.invalidateQueries({ queryKey: sessionKey(userQueryKeys.pointsRoot) })
            },
        })
    }

    const useCreateScheduledPost = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: ScheduledPostData }, mutationContext) => {
                return await postApi.createScheduledPost(boardUrl, data, mutationContext.requestConfig)
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.scheduledPostsRoot) })
            },
        })
    }

    const useUpdateScheduledPost = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ scheduledPostId, data }: { scheduledPostId: string | number, data: ScheduledPostData }, mutationContext) => {
                return await postApi.updateScheduledPost(scheduledPostId, data, mutationContext.requestConfig)
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.scheduledPostsRoot) })
            },
        })
    }

    const useUpdatePost = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ postId, data }: { postId: string | number, data: PostUpdateData }, mutationContext) => {
                return await postApi.updatePost(postId, data, mutationContext.requestConfig)
            },
            onSuccess: (_, { postId }, context) => {
                if (!isCurrentMutation(context)) return
                invalidatePostCaches(queryClient, context.sessionGeneration, postId)
            },
        })
    }

    const useDeletePost = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async (postId: string | number, mutationContext) => {
                return await postApi.deletePost(postId, mutationContext.requestConfig)
            },
            onSuccess: (_, postId, context) => {
                if (!isCurrentMutation(context)) return
                invalidatePostCaches(queryClient, context.sessionGeneration, postId)
            },
        })
    }

    function createOptimisticPostMutation(
        mutationFn: (postId: string | number, config: AxiosRequestConfig) => Promise<unknown>,
        updater: (post: Partial<Post>) => Partial<Post>,
        invalidateOnSettled?: (sessionGeneration: number) => void,
    ) {
        return useAuthScopedMutation({
            mutationFn: (postId: string | number, context) => mutationFn(postId, context.requestConfig),
            onMutate: async (postId, context) => {
                const sessionGeneration = authStore.sessionGeneration
                await queryClient.cancelQueries({ queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.detailPrefix(postId)) })
                context.assertCurrent()
                await queryClient.cancelQueries({ queryKey: sessionQueryKey(sessionGeneration, postQueryKeys.lists) })
                context.assertCurrent()
                const snapshots = savePostCacheSnapshots(queryClient, sessionGeneration, postId)

                updatePostInAllCaches(queryClient, sessionGeneration, postId, updater)

                return { snapshots, sessionGeneration }
            },
            onError: (_err, _postId, context) => {
                if (context?.snapshots && isSessionGenerationCurrent(authStore, context.sessionGeneration)) {
                    restorePostCacheSnapshots(queryClient, context.snapshots)
                }
            },
            onSettled: (_, __, postId, context) => {
                if (!context || !isSessionGenerationCurrent(authStore, context.sessionGeneration)) return
                invalidatePostCaches(queryClient, context.sessionGeneration, postId)
                invalidateOnSettled?.(context.sessionGeneration)
            },
        })
    }

    const useLikePost = () => {
        return createOptimisticPostMutation(
            (postId, config) => postApi.likePost(postId, config),
            (old) => ({
                ...old,
                liked: true,
                likeCount: (old.likeCount || 0) + 1,
            })
        )
    }

    const useUnlikePost = () => {
        return createOptimisticPostMutation(
            (postId, config) => postApi.unlikePost(postId, config),
            (old) => ({
                ...old,
                liked: false,
                likeCount: Math.max((old.likeCount || 0) - 1, 0),
            })
        )
    }

    const useScrapPost = () => {
        return createOptimisticPostMutation(
            (postId, config) => postApi.scrapPost(postId, config),
            (old) => ({
                ...old,
                scrapped: true,
            }),
            (sessionGeneration) => queryClient.invalidateQueries({
                queryKey: currentSessionQueryKey({ sessionGeneration }, userQueryKeys.scrapsRoot),
            }),
        )
    }

    const useUnscrapPost = () => {
        return createOptimisticPostMutation(
            (postId, config) => postApi.unscrapPost(postId, config),
            (old) => ({
                ...old,
                scrapped: false,
            }),
            (sessionGeneration) => queryClient.invalidateQueries({
                queryKey: currentSessionQueryKey({ sessionGeneration }, userQueryKeys.scrapsRoot),
            }),
        )
    }

    const useReportPost = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async (data: ReportData, mutationContext) => {
                return await postApi.reportPost(data, mutationContext.requestConfig)
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                void invalidateMyReportCaches(queryClient, context.sessionGeneration)
            },
        })
    }

    const createManagerPostMutation = (
        mutationFn: (postId: string | number, config: AxiosRequestConfig) => Promise<unknown>
    ) => useAuthScopedMutation({
        onMutate: captureMutationSession,
        mutationFn: (postId: string | number, context) => mutationFn(postId, context.requestConfig),
        onSuccess: (_, postId, context) => {
            if (!isCurrentMutation(context)) return
            invalidatePostCaches(queryClient, authStore.sessionGeneration, postId)
        },
    })

    const useBlindPostByManager = () => useAuthScopedMutation({
        onMutate: captureMutationSession,
        mutationFn: async ({ postId, reason }: { postId: string | number, reason?: string }, mutationContext) => {
            return await postApi.blindPostByManager(postId, reason, mutationContext.requestConfig)
        },
        onSuccess: (_, { postId }, context) => {
            if (!isCurrentMutation(context)) return
            invalidatePostCaches(queryClient, authStore.sessionGeneration, postId)
        },
    })
    const useUnblindPostByManager = () => createManagerPostMutation((postId, config) => postApi.unblindPostByManager(postId, config))

    const updatePollInDetailCache = (postId: string | number, poll: Post['poll']) => {
        queryClient.setQueriesData<Post>({ queryKey: authKey(postQueryKeys.detailPrefix(postId)) }, (old) => (
            old ? { ...old, poll } : old
        ))
    }

    const useVotePoll = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ postId, data }: { postId: string | number, data: PollVotePayload }, mutationContext) => {
                return unwrapAxiosApiData(await postApi.votePoll(postId, data, mutationContext.requestConfig))
            },
            onSuccess: (poll, { postId }, context) => {
                if (!isCurrentMutation(context)) return
                updatePollInDetailCache(postId, poll)
            },
        })
    }

    const useDeletePollVote = () => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async (postId: string | number, mutationContext) => {
                return unwrapAxiosApiData(await postApi.deletePollVote(postId, mutationContext.requestConfig))
            },
            onSuccess: (poll, postId, context) => {
                if (!isCurrentMutation(context)) return
                updatePollInDetailCache(postId, poll)
            },
        })
    }

    const useSaveDraft = (resolveRequestConfig?: () => AxiosRequestConfig | undefined) => {
        return useAuthScopedMutation({
            onMutate: captureMutationSession,
            mutationFn: async (data: PostDraftData, mutationContext) => {
                return await postApi.saveDraft(data, mutationContext.withRequestConfig(resolveRequestConfig?.()))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.draftsRoot) })
            },
        })
    }

    const useDeleteDraft = (resolveRequestConfig?: () => AxiosRequestConfig | undefined) => {
        return useAuthScopedMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async ({ draftId, version }: { draftId: string | number, version?: number }, mutationContext) => {
                const requestConfig = resolveRequestConfig?.()
                return await postApi.deleteDraft(draftId, mutationContext.withRequestConfig({
                    ...requestConfig,
                    params: {
                        ...requestConfig?.params,
                        ...(version !== undefined ? { version } : {}),
                    },
                    skipGlobalErrorHandler: true,
                }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.draftsRoot) })
            },
        })
    }

    const useCancelScheduledPost = () => {
        return useAuthScopedMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (scheduledPostId: string | number, mutationContext) => {
                return await postApi.cancelScheduledPost(scheduledPostId, mutationContext.withRequestConfig({
                    skipGlobalErrorHandler: true,
                }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.scheduledPostsRoot) })
            },
        })
    }

    return {
        usePostDetail,
        useScheduledPostDetail,
        useRelatedPosts,
        usePostVersions,
        useCreatePost,
        useCreateScheduledPost,
        useUpdateScheduledPost,
        useUpdatePost,
        useDeletePost,
        useLikePost,
        useUnlikePost,
        useScrapPost,
        useUnscrapPost,
        useReportPost,
        useBlindPostByManager,
        useUnblindPostByManager,
        useVotePoll,
        useDeletePollVote,
        useSaveDraft,
        useDeleteDraft,
        useCancelScheduledPost,
    }
}
