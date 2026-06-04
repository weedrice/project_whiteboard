import { useQuery, useMutation, useQueryClient, type QueryFunctionContext } from '@tanstack/vue-query'
import { userApi, type UserUpdatePayload, type NotificationSettingsBulkPayload } from '@/api/user'
import { computed, type Ref } from 'vue'
import type { UserSettings } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'
import { withQuerySignal } from '@/utils/querySignal'

interface PasswordUpdateData {
    currentPassword: string
    newPassword: string
}

interface PaginationParams {
    page?: number
    size?: number
}

const userQueryKeys = {
    me: ['user', 'me'] as const,
    profile: (userId: Ref<string | number>) => ['user', userId] as const,
    settings: ['user', 'settings'] as const,
    blocksRoot: ['user', 'blocks'] as const,
    blocks: (params?: Ref<PaginationParams>) => computed(() => ['user', 'blocks', params?.value ?? {}] as const),
    notificationSettings: ['user', 'notification-settings'] as const,
    agents: ['user', 'agents'] as const,
    myPoints: (userIdentity?: Ref<string | number | null | undefined>) =>
        computed(() => ['user', 'points', 'me', userIdentity?.value ?? 'anonymous'] as const),
    scraps: (params?: Ref<PaginationParams>) => computed(() => ['user', 'scraps', params?.value ?? {}] as const),
    pointHistories: (params?: Ref<PaginationParams>) =>
        computed(() => ['user', 'points', 'history', params?.value ?? {}] as const),
    recentlyViewedPosts: (params?: Ref<PaginationParams>) => ['user', 'history', 'views', params] as const,
}

export const userSettingsQueryKey = userQueryKeys.settings

export const createMyProfileQueryOptions = (config?: AxiosRequestConfig) => ({
    queryKey: userQueryKeys.me,
    queryFn: async (context: QueryFunctionContext) => {
        const { data } = await userApi.getMyProfile(withQuerySignal(config, context))
        return data.data
    },
    staleTime: QUERY_STALE_TIME.MEDIUM,
})

export const createMyAgentsQueryOptions = (config?: AxiosRequestConfig) => ({
    queryKey: userQueryKeys.agents,
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
            queryKey: userQueryKeys.profile(userId),
            queryFn: async () => {
                const { data } = await userApi.getUserProfile(userId.value)
                return data.data
            },
            enabled: computed(() => !!userId.value),
        })
    }

    const useUserSettings = () => {
        return useQuery({
            queryKey: userSettingsQueryKey,
            queryFn: async () => {
                const { data } = await userApi.getUserSettings()
                return data.data
            },
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    const useBlockList = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: userQueryKeys.blocks(params),
            queryFn: async () => {
                const { data } = await userApi.getBlockList(params?.value)
                return data.data
            },
        })
    }

    const useNotificationSettings = () => {
        return useQuery({
            queryKey: userQueryKeys.notificationSettings,
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
            queryKey: userQueryKeys.myPoints(userIdentity),
            queryFn: async () => {
                const { data } = await userApi.getMyPoint()
                return data.data
            },
            enabled: computed(() => enabled?.value ?? true),
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    const useMyScraps = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: userQueryKeys.scraps(params),
            queryFn: async (context: QueryFunctionContext) => {
                const { data } = await userApi.getMyScraps(params?.value ?? {}, withQuerySignal(undefined, context))
                return data.data
            },
        })
    }

    const useMyPointHistories = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: userQueryKeys.pointHistories(params),
            queryFn: async (context: QueryFunctionContext) => {
                const { data } = await userApi.getMyPointHistories(params?.value ?? {}, withQuerySignal(undefined, context))
                return data.data
            },
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.me })
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
                queryClient.invalidateQueries({ queryKey: userSettingsQueryKey })
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.notificationSettings })
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.blocksRoot })
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
                queryClient.invalidateQueries({ queryKey: userQueryKeys.blocksRoot })
            }
        })
    }

    const useRecentlyViewedPosts = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: userQueryKeys.recentlyViewedPosts(params),
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
        useMyScraps,
        useMyPointHistories,
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
