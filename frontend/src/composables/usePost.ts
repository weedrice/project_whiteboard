import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import type { Post } from '@/types'
import { postApi, type PostCreateData, type PostDraftData, type PostUpdateData, type ReportData } from '@/api/post'
import { unwrapAxiosApiData } from '@/api/response'
import {
    invalidatePostCaches,
    restorePostCacheSnapshots,
    savePostCacheSnapshots,
    updatePostInAllCaches,
} from '@/composables/postCacheUpdates'
import { homeQueryKeys } from '@/composables/homeQueryKeys'
import { postDetailQueryKey, postQueryKeys } from '@/composables/postQueryKeys'
import { normalizePostReactionFlags, type PostReactionAlias } from '@/utils/postViewModel'

export { postDetailQueryKey, postQueryKeys } from '@/composables/postQueryKeys'

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
            queryFn: async () => {
                const post = unwrapAxiosApiData(await postApi.getPost(postId.value, {
                    ...requestConfig,
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

    const useCreatePost = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: PostCreateData }) => {
                return await postApi.createPost(boardUrl, data)
            },
            onSuccess: (_, { boardUrl }) => {
                queryClient.invalidateQueries({ queryKey: postQueryKeys.boardPosts(boardUrl) })
                queryClient.invalidateQueries({ queryKey: homeQueryKeys.landingRoot })
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
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: postQueryKeys.boardPostsRoot })
                queryClient.invalidateQueries({ queryKey: homeQueryKeys.landingRoot })
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

    return {
        usePostDetail,
        useCreatePost,
        useUpdatePost,
        useDeletePost,
        useLikePost,
        useUnlikePost,
        useScrapPost,
        useUnscrapPost,
        useReportPost,
        useSaveDraft,
        useDeleteDraft,
    }
}
