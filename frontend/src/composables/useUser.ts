import { useQuery, useMutation, useQueryClient, type QueryFunctionContext } from '@tanstack/vue-query'
import { userApi, type UserUpdatePayload, type NotificationSettingsBulkPayload } from '@/api/user'
import { computed, type Ref } from 'vue'
import type { UserSettings } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'

interface PasswordUpdateData {
    currentPassword: string
    newPassword: string
}

interface PaginationParams {
    page?: number
    size?: number
}

function withQuerySignal(config: AxiosRequestConfig | undefined, queryContext?: QueryFunctionContext): AxiosRequestConfig {
    return {
        ...config,
        signal: config?.signal ?? queryContext?.signal,
    }
}

export const createMyProfileQueryOptions = (config?: AxiosRequestConfig) => ({
    queryKey: ['user', 'me'] as const,
    queryFn: async (context: QueryFunctionContext) => {
        const { data } = await userApi.getMyProfile(withQuerySignal(config, context))
        return data.data
    },
    staleTime: QUERY_STALE_TIME.MEDIUM,
})

export const createMyAgentsQueryOptions = (config?: AxiosRequestConfig) => ({
    queryKey: ['user', 'agents'] as const,
    queryFn: async (context: QueryFunctionContext) => {
        const { data } = await userApi.getMyAgents(withQuerySignal(config, context))
        return data.data
    },
    staleTime: QUERY_STALE_TIME.MEDIUM,
})

export function useUser() {
    const queryClient = useQueryClient()

    // --- Queries ---

    const useMyProfile = () => {
        return useQuery(createMyProfileQueryOptions())
    }

    const useUserProfile = (userId: Ref<string | number>) => {
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
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    const useBlockList = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: computed(() => ['user', 'blocks', params?.value ?? {}]),
            queryFn: async () => {
                const { data } = await userApi.getBlockList(params?.value)
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

    const useMyAgents = () => {
        return useQuery(createMyAgentsQueryOptions())
    }

    const useMyPoint = (enabled?: Ref<boolean>, userIdentity?: Ref<string | number | null | undefined>) => {
        return useQuery({
            queryKey: computed(() => ['user', 'points', 'me', userIdentity?.value ?? 'anonymous']),
            queryFn: async () => {
                const { data } = await userApi.getMyPoint()
                return data.data
            },
            enabled: computed(() => enabled?.value ?? true),
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    // --- Mutations ---

    const useUpdateMyProfile = () => {
        return useMutation({
            mutationFn: async (data: UserUpdatePayload) => {
                const { data: res } = await userApi.updateMyProfile(data)
                return res
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'me'] })
            }
        })
    }

    const useUpdatePassword = () => {
        return useMutation({
            mutationFn: async ({ currentPassword, newPassword }: PasswordUpdateData) => {
                const { data } = await userApi.updatePassword(currentPassword, newPassword)
                return data
            }
        })
    }

    const useDeleteAccount = () => {
        return useMutation({
            mutationFn: async (password: string) => {
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
            mutationFn: async (data: Partial<UserSettings>) => {
                const { data: res } = await userApi.updateUserSettings(data)
                return res
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'settings'] })
            }
        })
    }

    const useUpdateNotificationSettings = () => {
        return useMutation({
            mutationFn: async (data: NotificationSettingsBulkPayload) => {
                const { data: res } = await userApi.updateNotificationSettingsBulk(data)
                return res
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'notification-settings'] })
            }
        })
    }

    const useClaimAgent = () => {
        return useMutation({
            mutationFn: async (agentToken: string) => {
                const { data } = await userApi.claimAgent(agentToken)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'agents'] })
            }
        })
    }

    const useSuspendMyAgent = () => {
        return useMutation({
            mutationFn: async (agentId: string | number) => {
                const { data } = await userApi.suspendMyAgent(agentId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'agents'] })
            }
        })
    }

    const useActivateMyAgent = () => {
        return useMutation({
            mutationFn: async (agentId: string | number) => {
                const { data } = await userApi.activateMyAgent(agentId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'agents'] })
            }
        })
    }

    const useDeleteMyAgent = () => {
        return useMutation({
            mutationFn: async (agentId: string | number) => {
                const { data } = await userApi.deleteMyAgent(agentId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'agents'] })
            }
        })
    }

    const useBlockUser = () => {
        return useMutation({
            mutationFn: async (userId: string | number) => {
                const { data } = await userApi.blockUser(userId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'blocks'] })
            }
        })
    }

    const useUnblockUser = () => {
        return useMutation({
            mutationFn: async (userId: string | number) => {
                const { data } = await userApi.unblockUser(userId)
                return data
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: ['user', 'blocks'] })
            }
        })
    }

    const useRecentlyViewedPosts = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: ['user', 'history', 'views', params],
            queryFn: async () => {
                const { data } = await userApi.getRecentlyViewedPosts(params?.value || {})
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
        useMyAgents,
        useMyPoint,
        useRecentlyViewedPosts,
        useUpdateMyProfile,
        useUpdatePassword,
        useDeleteAccount,
        useUpdateUserSettings,
        useUpdateNotificationSettings,
        useClaimAgent,
        useSuspendMyAgent,
        useActivateMyAgent,
        useDeleteMyAgent,
        useBlockUser,
        useUnblockUser
    }
}
