import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { postApi, type PostCreateData, type PostDraftData, type PostUpdateData, type ReportData } from '@/api/post'
import { computed, type Ref } from 'vue'
import type { Post } from '@/types'
import type { AxiosRequestConfig } from 'axios'

type PostReactionAlias = Post & {
    isLiked?: boolean
    isScrapped?: boolean
}

function normalizePostReactionFlags(post: PostReactionAlias): Post {
    return {
        ...post,
        liked: post.liked ?? post.isLiked ?? false,
        scrapped: post.scrapped ?? post.isScrapped ?? false,
    }
}

export function usePost() {
    const queryClient = useQueryClient()

    // --- Optimistic Update 헬퍼 ---

    // 캐시 조작용 인터페이스
    interface InfiniteQueryData {
        pages: Array<{ content: Partial<Post>[];[key: string]: unknown }>
        pageParams: unknown[]
    }

    interface PageQueryData {
        content: Partial<Post>[]
        [key: string]: unknown
    }

    // 모든 게시글 캐시(상세, 트렌딩, 게시판 목록)에서 특정 postId의 데이터를 업데이트
    function updatePostInAllCaches(postId: string | number, updater: (post: Partial<Post>) => Partial<Post>) {
        // 1. 게시글 상세 캐시
        queryClient.setQueryData<Post>(['post', postId], (old) => {
            if (!old) return old
            return updater(old) as Post
        })

        // 2. 트렌딩 피드 등 게시글 목록 캐시 (InfiniteQuery 구조)
        queryClient.setQueriesData<InfiniteQueryData>(
            { queryKey: ['posts'] },
            (old) => {
                if (!old?.pages) return old
                return {
                    ...old,
                    pages: old.pages.map((page) => ({
                        ...page,
                        content: Array.isArray(page.content)
                            ? page.content.map((p) =>
                                String(p.postId) === String(postId) ? updater(p) : p
                            )
                            : page.content
                    }))
                }
            }
        )

        // 3. 게시판별 게시글 목록 캐시
        queryClient.getQueriesData({ queryKey: ['board'] }).forEach(([key]) => {
            const keyArr = key as unknown[]
            if (!keyArr.includes('posts')) return
            queryClient.setQueryData(key, (old: InfiniteQueryData | PageQueryData | undefined) => {
                if (!old) return old
                // InfiniteQuery 구조
                if ('pages' in old && old.pages) {
                    return {
                        ...old,
                        pages: (old as InfiniteQueryData).pages.map((page) => ({
                            ...page,
                            content: Array.isArray(page.content)
                                ? page.content.map((p) =>
                                    String(p.postId) === String(postId) ? updater(p) : p
                                )
                                : page.content
                        }))
                    }
                }
                // 일반 PageResponse 구조
                if ('content' in old && Array.isArray(old.content)) {
                    return {
                        ...old,
                        content: old.content.map((p) =>
                            String(p.postId) === String(postId) ? updater(p) : p
                        )
                    }
                }
                return old
            })
        })
    }

    // 롤백을 위한 스냅샷 저장
    function savePostCacheSnapshots(postId: string | number) {
        return {
            postDetail: queryClient.getQueryData(['post', postId]),
            postsQueries: queryClient.getQueriesData({ queryKey: ['posts'] }),
            boardQueries: queryClient.getQueriesData({ queryKey: ['board'] })
                .filter(([key]) => (key as unknown[]).includes('posts'))
        }
    }

    // 스냅샷 복원 (에러 시 롤백)
    function restorePostCacheSnapshots(
        postId: string | number,
        snapshots: ReturnType<typeof savePostCacheSnapshots>
    ) {
        if (snapshots.postDetail !== undefined) {
            queryClient.setQueryData(['post', postId], snapshots.postDetail)
        }
        snapshots.postsQueries.forEach(([key, data]) => {
            queryClient.setQueryData(key, data)
        })
        snapshots.boardQueries.forEach(([key, data]) => {
            queryClient.setQueryData(key, data)
        })
    }

    // 관련 캐시 무효화 (서버 상태와 동기화)
    function invalidatePostCaches(postId: string | number) {
        queryClient.invalidateQueries({ queryKey: ['post', postId] })
        queryClient.invalidateQueries({ queryKey: ['posts'] })
        queryClient.invalidateQueries({
            predicate: (query) => {
                const key = query.queryKey
                return Array.isArray(key) && key[0] === 'board' && key.includes('posts')
            }
        })
    }

    // Fetch single post details (incrementView: true → 조회수 증가 + 로그인 시 최근 읽은 글에 반영)
    const usePostDetail = (postId: Ref<string | number>, options: { requestConfig?: AxiosRequestConfig } & Record<string, unknown> = {}) => {
        const { requestConfig, ...queryOptions } = options
        return useQuery({
            queryKey: ['post', postId],
            queryFn: async () => {
                const { data } = await postApi.getPost(postId.value, {
                    ...requestConfig,
                    params: {
                        incrementView: true,
                        ...(requestConfig?.params || {})
                    }
                })
                return normalizePostReactionFlags(data.data as PostReactionAlias)
            },
            enabled: computed(() => !!postId.value),
            ...queryOptions
        })
    }

    // Create a new post
    const useCreatePost = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: PostCreateData }) => {
                return await postApi.createPost(boardUrl, data)
            },
            onSuccess: (_, { boardUrl }) => {
                queryClient.invalidateQueries({ queryKey: ['board', boardUrl, 'posts'] })
            }
        })
    }

    // Update a post
    const useUpdatePost = () => {
        return useMutation({
            mutationFn: async ({ postId, data }: { postId: string | number, data: PostUpdateData }) => {
                return await postApi.updatePost(postId, data)
            },
            onSuccess: (_, { postId }) => {
                invalidatePostCaches(postId)
            }
        })
    }

    // Delete a post
    const useDeletePost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.deletePost(postId)
            },
            onSuccess: () => {
                // Invalidate relevant queries (e.g., board posts)
                // Note: We might need boardUrl to be more specific, but 'posts' key usually includes boardUrl
                queryClient.invalidateQueries({ queryKey: ['board'] })
            }
        })
    }

    // Like a post
    const useLikePost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.likePost(postId)
            },
            onMutate: async (postId) => {
                await queryClient.cancelQueries({ queryKey: ['post', postId] })
                await queryClient.cancelQueries({ queryKey: ['posts'] })
                const snapshots = savePostCacheSnapshots(postId)

                updatePostInAllCaches(postId, (old) => ({
                    ...old,
                    liked: true,
                    likeCount: (old.likeCount || 0) + 1
                }))

                return { snapshots }
            },
            onError: (_err, postId, context) => {
                if (context?.snapshots) {
                    restorePostCacheSnapshots(postId, context.snapshots)
                }
            },
            onSettled: (_, __, postId) => {
                invalidatePostCaches(postId)
            }
        })
    }

    // Unlike a post
    const useUnlikePost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.unlikePost(postId)
            },
            onMutate: async (postId) => {
                await queryClient.cancelQueries({ queryKey: ['post', postId] })
                await queryClient.cancelQueries({ queryKey: ['posts'] })
                const snapshots = savePostCacheSnapshots(postId)

                updatePostInAllCaches(postId, (old) => ({
                    ...old,
                    liked: false,
                    likeCount: Math.max((old.likeCount || 0) - 1, 0)
                }))

                return { snapshots }
            },
            onError: (_err, postId, context) => {
                if (context?.snapshots) {
                    restorePostCacheSnapshots(postId, context.snapshots)
                }
            },
            onSettled: (_, __, postId) => {
                invalidatePostCaches(postId)
            }
        })
    }

    // Scrap a post
    const useScrapPost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.scrapPost(postId)
            },
            onMutate: async (postId) => {
                await queryClient.cancelQueries({ queryKey: ['post', postId] })
                await queryClient.cancelQueries({ queryKey: ['posts'] })
                const snapshots = savePostCacheSnapshots(postId)

                updatePostInAllCaches(postId, (old) => ({
                    ...old,
                    scrapped: true
                }))

                return { snapshots }
            },
            onError: (_err, postId, context) => {
                if (context?.snapshots) {
                    restorePostCacheSnapshots(postId, context.snapshots)
                }
            },
            onSettled: (_, __, postId) => {
                invalidatePostCaches(postId)
            }
        })
    }

    // Unscrap a post
    const useUnscrapPost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.unscrapPost(postId)
            },
            onMutate: async (postId) => {
                await queryClient.cancelQueries({ queryKey: ['post', postId] })
                await queryClient.cancelQueries({ queryKey: ['posts'] })
                const snapshots = savePostCacheSnapshots(postId)

                updatePostInAllCaches(postId, (old) => ({
                    ...old,
                    scrapped: false
                }))

                return { snapshots }
            },
            onError: (_err, postId, context) => {
                if (context?.snapshots) {
                    restorePostCacheSnapshots(postId, context.snapshots)
                }
            },
            onSettled: (_, __, postId) => {
                invalidatePostCaches(postId)
            }
        })
    }

    // Report a post
    const useReportPost = () => {
        return useMutation({
            mutationFn: async (data: ReportData) => {
                return await postApi.reportPost(data)
            }
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
