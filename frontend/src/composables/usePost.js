import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { postApi } from '@/api/post'
import { computed } from 'vue'

export function usePost() {
    const queryClient = useQueryClient()

    // Fetch single post details
    const usePostDetail = (postId, options = {}) => {
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
            mutationFn: async ({ boardUrl, data }) => {
                return await postApi.createPost(boardUrl, data)
            },
            onSuccess: (_, { boardUrl }) => {
                queryClient.invalidateQueries(['board', boardUrl, 'posts'])
            }
        })
    }

    // Update a post
    const useUpdatePost = () => {
        return useMutation({
            mutationFn: async ({ postId, data }) => {
                return await postApi.updatePost(postId, data)
            },
            onSuccess: (_, { postId }) => {
                queryClient.invalidateQueries(['post', postId])
            }
        })
    }

    // Delete a post
    const useDeletePost = () => {
        return useMutation({
            mutationFn: async (postId) => {
                return await postApi.deletePost(postId)
            },
            onSuccess: () => {
                // Invalidate relevant queries (e.g., board posts)
                // Note: We might need boardUrl to be more specific, but 'posts' key usually includes boardUrl
                queryClient.invalidateQueries(['board'])
            }
        })
    }

    // Like a post
    const useLikePost = () => {
        return useMutation({
            mutationFn: async (postId) => {
                return await postApi.likePost(postId)
            },
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries(['post', postId])
            }
        })
    }

    // Unlike a post
    const useUnlikePost = () => {
        return useMutation({
            mutationFn: async (postId) => {
                return await postApi.unlikePost(postId)
            },
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries(['post', postId])
            }
        })
    }

    // Scrap a post
    const useScrapPost = () => {
        return useMutation({
            mutationFn: async (postId) => {
                return await postApi.scrapPost(postId)
            },
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries(['post', postId])
            }
        })
    }

    // Unscrap a post
    const useUnscrapPost = () => {
        return useMutation({
            mutationFn: async (postId) => {
                return await postApi.unscrapPost(postId)
            },
            onSuccess: (_, postId) => {
                queryClient.invalidateQueries(['post', postId])
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
        useUnscrapPost
    }
}
