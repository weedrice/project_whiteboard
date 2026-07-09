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
import { userQueryKeys } from '@/composables/userQueryKeys'
import { postDetailQueryKey, postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'
import { normalizePostReactionFlags, type PostReactionAlias } from '@/utils/postViewModel'
import { withQuerySignal } from '@/utils/querySignal'

export { postDetailQueryKey, postQueryKeys } from '@/features/board/posts/queries/postQueryKeys'

export function usePost() {
    const queryClient = useQueryClient()

    const usePostDetail = (
        postId: Ref<string | number>,
        options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
    ) => {
        const { requestConfig, ...queryOptions } = options
        const incrementView = requestConfig?.params?.incrementView !== false

        return useQuery({
            queryKey: postDetailQueryKey(postId, incrementView),
            queryFn: async (context?: { signal?: AbortSignal }) => {
                const post = unwrapAxiosApiData(await postApi.getPost(postId.value, {
                    ...withQuerySignal(requestConfig, context),
                    params: {
                        incrementView: true,
                        ...(requestConfig?.params || {}),
                    },
                }))
                return normalizePostReactionFlags(post as PostReactionAlias)
            },
            enabled: computed(() => !!postId.value),
            ...queryOptions,
        })
    }

    const useRelatedPosts = (
        postId: Ref<string | number>,
        options: { enabled?: Ref<boolean>, size?: number, requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}
    ) => {
        const { requestConfig, size = 5, enabled, ...queryOptions } = options
        return useQuery({
            queryKey: computed(() => postQueryKeys.related(postId.value, size)),
            queryFn: async (context?: { signal?: AbortSignal }) => unwrapAxiosApiData(
                await postApi.getRelatedPosts(postId.value, size, withQuerySignal(requestConfig, context))
            ),
            enabled: computed(() => !!postId.value && (enabled?.value ?? true)),
            ...queryOptions,
        })
    }

    const useCreatePost = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: PostCreateData }) => {
                return await postApi.createPost(boardUrl, data)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: postQueryKeys.boardPostsRoot })
                queryClient.invalidateQueries({ queryKey: homeQueryKeys.landingRoot })
                queryClient.invalidateQueries({ queryKey: userQueryKeys.pointsRoot })
            },
        })
    }

    const useCreateScheduledPost = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: ScheduledPostData }) => {
                return await postApi.createScheduledPost(boardUrl, data)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.scheduledPostsRoot })
            },
        })
    }

    const useUpdatePost = () => {
        return useMutation({
            mutationFn: async ({ postId, data }: { postId: string | number, data: PostUpdateData }) => {
                return await postApi.updatePost(postId, data)
            },
            onSuccess: (_, { postId }) => {
                invalidatePostCaches(queryClient, postId)
            },
        })
    }

    const useDeletePost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.deletePost(postId)
            },
            onSuccess: (_, postId) => {
                invalidatePostCaches(queryClient, postId)
            },
        })
    }

    function createOptimisticPostMutation(
        mutationFn: (postId: string | number) => Promise<unknown>,
        updater: (post: Partial<Post>) => Partial<Post>
    ) {
        return useMutation({
            mutationFn,
            onMutate: async (postId) => {
                await queryClient.cancelQueries({ queryKey: postQueryKeys.detailPrefix(postId) })
                await queryClient.cancelQueries({ queryKey: postQueryKeys.lists })
                const snapshots = savePostCacheSnapshots(queryClient, postId)

                updatePostInAllCaches(queryClient, postId, updater)

                return { snapshots }
            },
            onError: (_err, _postId, context) => {
                if (context?.snapshots) {
                    restorePostCacheSnapshots(queryClient, context.snapshots)
                }
            },
            onSettled: (_, __, postId) => {
                invalidatePostCaches(queryClient, postId)
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
            })
        )
    }

    const useUnscrapPost = () => {
        return createOptimisticPostMutation(
            (postId) => postApi.unscrapPost(postId),
            (old) => ({
                ...old,
                scrapped: false,
            })
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
        mutationFn,
        onSuccess: (_, postId) => {
            invalidatePostCaches(queryClient, postId)
        },
    })

    const usePinPostByManager = () => createManagerPostMutation((postId) => postApi.pinPostByManager(postId))
    const useUnpinPostByManager = () => createManagerPostMutation((postId) => postApi.unpinPostByManager(postId))
    const useBlindPostByManager = () => useMutation({
        mutationFn: async ({ postId, reason }: { postId: string | number, reason?: string }) => {
            return await postApi.blindPostByManager(postId, reason)
        },
        onSuccess: (_, { postId }) => {
            invalidatePostCaches(queryClient, postId)
        },
    })
    const useUnblindPostByManager = () => createManagerPostMutation((postId) => postApi.unblindPostByManager(postId))

    const updatePollInDetailCache = (postId: string | number, poll: Post['poll']) => {
        queryClient.setQueriesData<Post>({ queryKey: postQueryKeys.detailPrefix(postId) }, (old) => (
            old ? { ...old, poll } : old
        ))
    }

    const useVotePoll = () => {
        return useMutation({
            mutationFn: async ({ postId, data }: { postId: string | number, data: PollVotePayload }) => {
                return unwrapAxiosApiData(await postApi.votePoll(postId, data))
            },
            onSuccess: (poll, { postId }) => {
                updatePollInDetailCache(postId, poll)
            },
        })
    }

    const useDeletePollVote = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return unwrapAxiosApiData(await postApi.deletePollVote(postId))
            },
            onSuccess: (poll, postId) => {
                updatePollInDetailCache(postId, poll)
            },
        })
    }

    const useSaveDraft = () => {
        return useMutation({
            mutationFn: async (data: PostDraftData) => {
                return await postApi.saveDraft(data)
            },
        })
    }

    const useDeleteDraft = () => {
        return useMutation({
            mutationFn: async (draftId: string | number) => {
                return await postApi.deleteDraft(draftId)
            },
        })
    }

    const useCancelScheduledPost = () => {
        return useMutation({
            mutationFn: async (scheduledPostId: string | number) => {
                return await postApi.cancelScheduledPost(scheduledPostId)
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.scheduledPostsRoot })
            },
        })
    }

    return {
        usePostDetail,
        useRelatedPosts,
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
