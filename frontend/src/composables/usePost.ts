import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { postApi } from '@/api/post'
import { computed, type Ref } from 'vue'

export function usePost() {
    const queryClient = useQueryClient()

    // Fetch single post details
    const usePostDetail = (postId: Ref<string | number>, options = {}) => {
        return useQuery({
            queryKey: ['post', postId],
            queryFn: async () => {
                const { data } = await postApi.getPost(postId.value)
                return data.data
            },
            enabled: computed(() => !!postId.value),
            ...options
        })
    }

    // Create a new post
    const useCreatePost = () => {
        return useMutation({
            mutationFn: async ({ boardUrl, data }: { boardUrl: string, data: any }) => {
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
            mutationFn: async ({ postId, data }: { postId: string | number, data: any }) => {
                return await postApi.updatePost(postId, data)
            },
            onSuccess: (_, { postId }) => {
                queryClient.invalidateQueries({ queryKey: ['post', postId] })
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
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries({ queryKey: ['post', postId] })
            }
        })
    }

    // Unlike a post
    const useUnlikePost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.unlikePost(postId)
            },
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries({ queryKey: ['post', postId] })
            }
        })
    }

    // Scrap a post
    const useScrapPost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.scrapPost(postId)
            },
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries({ queryKey: ['post', postId] })
            }
        })
    }

    // Unscrap a post
    const useUnscrapPost = () => {
        return useMutation({
            mutationFn: async (postId: string | number) => {
                return await postApi.unscrapPost(postId)
            },
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries({ queryKey: ['post', postId] })
            }
        })
    }

    // Report a post
    const useReportPost = () => {
        return useMutation({
            mutationFn: async (data: any) => {
                return await postApi.reportPost(data)
            }
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
        useReportPost
    }
}
