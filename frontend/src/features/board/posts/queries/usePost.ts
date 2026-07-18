import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import type { Post } from '@/types'
import { postApi, type PollVotePayload, type PostCreateData, type PostDraftData, type PostUpdateData, type ReportData, type ScheduledPostData } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import {
    invalidatePostCaches,
    restorePostCacheSnapshots,
    savePostCacheSnapshots,
    updatePostInAllCaches,
} from '@/features/board/posts/queries/postCacheUpdates'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { postDetailQueryKey, postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { withQuerySignal } from '@/utils/querySignal'
import { useAuthStore } from '@/stores/auth'
import {
    AUTH_SCOPED_QUERY_META,
    currentSessionQueryKey,
    isSessionGenerationCurrent,
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
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: PostCreateData }) => {
                return await postApi.createPost(boardUrl, data)
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(postQueryKeys.boardPostsRoot) })
                queryClient.invalidateQueries({ queryKey: authKey(homeQueryKeys.landingRoot) })
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.pointsRoot) })
            },
        })
    }

    const useCreateScheduledPost = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: ScheduledPostData }) => {
                return await postApi.createScheduledPost(boardUrl, data)
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.scheduledPostsRoot) })
            },
        })
    }

    const useUpdatePost = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ postId, data }: { postId: string | number, data: PostUpdateData }) => {
                return await postApi.updatePost(postId, data)
            },
            onSuccess: (_, { postId }, context) => {
                if (!isCurrentMutation(context)) return
                invalidatePostCaches(queryClient, authStore.sessionGeneration, postId)
            },
        })
    }

    const useDeletePost = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (postId: string | number) => {
                return await postApi.deletePost(postId)
            },
            onSuccess: (_, postId, context) => {
                if (!isCurrentMutation(context)) return
                invalidatePostCaches(queryClient, authStore.sessionGeneration, postId)
            },
        })
    }

    function createOptimisticPostMutation(
        mutationFn: (postId: string | number) => Promise<unknown>,
        updater: (post: Partial<Post>) => Partial<Post>,
        invalidateOnSettled?: (sessionGeneration: number) => void,
    ) {
        return useMutation({
            mutationFn,
            onMutate: async (postId) => {
                const sessionGeneration = authStore.sessionGeneration
                await queryClient.cancelQueries({ queryKey: currentSessionQueryKey(authStore, postQueryKeys.detailPrefix(postId)) })
                await queryClient.cancelQueries({ queryKey: currentSessionQueryKey(authStore, postQueryKeys.lists) })
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
            (postId) => postApi.likePost(postId),
            (old) => ({
                ...old,
                liked: true,
                likeCount: (old.likeCount || 0) + 1,
            })
        )
    }

    const useUnlikePost = () => {
        return createOptimisticPostMutation(
            (postId) => postApi.unlikePost(postId),
            (old) => ({
                ...old,
                liked: false,
                likeCount: Math.max((old.likeCount || 0) - 1, 0),
            })
        )
    }

    const useScrapPost = () => {
        return createOptimisticPostMutation(
            (postId) => postApi.scrapPost(postId),
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
            (postId) => postApi.unscrapPost(postId),
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
        return useMutation({
            mutationFn: async (data: ReportData) => {
                return await postApi.reportPost(data)
            },
        })
    }

    const createManagerPostMutation = (
        mutationFn: (postId: string | number) => Promise<unknown>
    ) => useMutation({
        onMutate: captureMutationSession,
        mutationFn,
        onSuccess: (_, postId, context) => {
            if (!isCurrentMutation(context)) return
            invalidatePostCaches(queryClient, authStore.sessionGeneration, postId)
        },
    })

    const usePinPostByManager = () => createManagerPostMutation((postId) => postApi.pinPostByManager(postId))
    const useUnpinPostByManager = () => createManagerPostMutation((postId) => postApi.unpinPostByManager(postId))
    const useBlindPostByManager = () => useMutation({
        onMutate: captureMutationSession,
        mutationFn: async ({ postId, reason }: { postId: string | number, reason?: string }) => {
            return await postApi.blindPostByManager(postId, reason)
        },
        onSuccess: (_, { postId }, context) => {
            if (!isCurrentMutation(context)) return
            invalidatePostCaches(queryClient, authStore.sessionGeneration, postId)
        },
    })
    const useUnblindPostByManager = () => createManagerPostMutation((postId) => postApi.unblindPostByManager(postId))

    const updatePollInDetailCache = (postId: string | number, poll: Post['poll']) => {
        queryClient.setQueriesData<Post>({ queryKey: authKey(postQueryKeys.detailPrefix(postId)) }, (old) => (
            old ? { ...old, poll } : old
        ))
    }

    const useVotePoll = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async ({ postId, data }: { postId: string | number, data: PollVotePayload }) => {
                return unwrapAxiosApiData(await postApi.votePoll(postId, data))
            },
            onSuccess: (poll, { postId }, context) => {
                if (!isCurrentMutation(context)) return
                updatePollInDetailCache(postId, poll)
            },
        })
    }

    const useDeletePollVote = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (postId: string | number) => {
                return unwrapAxiosApiData(await postApi.deletePollVote(postId))
            },
            onSuccess: (poll, postId, context) => {
                if (!isCurrentMutation(context)) return
                updatePollInDetailCache(postId, poll)
            },
        })
    }

    const useSaveDraft = (resolveRequestConfig?: () => AxiosRequestConfig | undefined) => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (data: PostDraftData) => {
                return await postApi.saveDraft(data, resolveRequestConfig?.())
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.draftsRoot) })
            },
        })
    }

    const useDeleteDraft = (resolveRequestConfig?: () => AxiosRequestConfig | undefined) => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (draftId: string | number) => {
                return await postApi.deleteDraft(draftId, resolveRequestConfig?.())
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.draftsRoot) })
            },
        })
    }

    const useCancelScheduledPost = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (scheduledPostId: string | number) => {
                return await postApi.cancelScheduledPost(scheduledPostId)
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.scheduledPostsRoot) })
            },
        })
    }

    return {
        usePostDetail,
        useRelatedPosts,
        usePostVersions,
        useCreatePost,
        useCreateScheduledPost,
        useUpdatePost,
        useDeletePost,
        useLikePost,
        useUnlikePost,
        useScrapPost,
        useUnscrapPost,
        useReportPost,
        usePinPostByManager,
        useUnpinPostByManager,
        useBlindPostByManager,
        useUnblindPostByManager,
        useVotePoll,
        useDeletePollVote,
        useSaveDraft,
        useDeleteDraft,
        useCancelScheduledPost,
    }
}
