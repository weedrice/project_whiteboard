import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import { computed } from 'vue'

export function useUser() {
    const queryClient = useQueryClient()

    // --- Queries ---

    const useMyProfile = () => {
        return useQuery({
            queryKey: ['user', 'me'],
            queryFn: async () => {
                const { data } = await userApi.getMyProfile()
                return data.data
            },
            staleTime: 1000 * 60 * 5, // 5 minutes
        })
    }

    const useUserProfile = (userId) => {
        return useQuery({
            queryKey: ['user', userId],
            queryFn: async () => {
                const { data } = await userApi.getUserProfile(userId.value)
                return data.data
            },
            enabled: computed(() => !!userId.value),
        })
    }

    const useUserSettings = () => {
        return useQuery({
            queryKey: ['user', 'settings'],
            queryFn: async () => {
                const { data } = await userApi.getUserSettings()
                return data.data
            },
        })
    }

    const useBlockList = () => {
        return useQuery({
            queryKey: ['user', 'blocks'],
            queryFn: async () => {
                const { data } = await userApi.getBlockList()
                return data.data
            },
        })
    }

    const useNotificationSettings = () => {
        return useQuery({
            queryKey: ['user', 'notification-settings'],
            queryFn: async () => {
                const { data } = await userApi.getNotificationSettings()
                return data.data
            },
        })
    }

    // --- Mutations ---

    const useUpdateMyProfile = () => {
        return useMutation({
            mutationFn: async (data) => {
                const { data: res } = await userApi.updateMyProfile(data)
                return res
            },
            onSuccess: () => {
                queryClient.invalidateQueries(['user', 'me'])
            }
        })
    }

    const useUpdatePassword = () => {
        return useMutation({
            mutationFn: async ({ currentPassword, newPassword }) => {
                const { data } = await userApi.updatePassword(currentPassword, newPassword)
                return data
            }
        })
    }

    const useDeleteAccount = () => {
        return useMutation({
            mutationFn: async (password) => {
                const { data } = await userApi.deleteAccount(password)
                return data
            },
            onSuccess: () => {
                // Handle logout or redirect in component
                queryClient.clear()
            }
        })
    }

    const useUpdateUserSettings = () => {
        return useMutation({
            mutationFn: async (data) => {
                const { data: res } = await userApi.updateUserSettings(data)
                return res
            },
            onSuccess: () => {
                queryClient.invalidateQueries(['user', 'settings'])
            }
        })
    }

    const useUpdateNotificationSettings = () => {
        return useMutation({
            mutationFn: async (data) => {
                const { data: res } = await userApi.updateNotificationSettings(data)
                return res
            },
            onSuccess: () => {
                queryClient.invalidateQueries(['user', 'notification-settings'])
            }
        })
    }

    const useBlockUser = () => {
        return useMutation({
            mutationFn: async (userId) => {
                const { data } = await userApi.blockUser(userId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries(['user', 'blocks'])
            }
        })
    }

    const useUnblockUser = () => {
        return useMutation({
            mutationFn: async (userId) => {
                const { data } = await userApi.unblockUser(userId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries(['user', 'blocks'])
            }
        })
    }

    const useRecentlyViewedPosts = (params) => {
        return useQuery({
            queryKey: ['user', 'history', 'views', params],
            queryFn: async () => {
                const { data } = await userApi.getRecentlyViewedPosts(params?.value)
                return data.data
            },
        })
    }

    return {
        useMyProfile,
        useUserProfile,
        useUserSettings,
        useNotificationSettings,
        useBlockList,
        useRecentlyViewedPosts,
        useUpdateMyProfile,
        useUpdatePassword,
        useDeleteAccount,
        useUpdateUserSettings,
        useUpdateNotificationSettings,
        useBlockUser,
        useUnblockUser
    }
}
