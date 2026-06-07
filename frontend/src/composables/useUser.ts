import { useQuery, useMutation, useQueryClient, type QueryFunctionContext } from '@tanstack/vue-query'
import { userApi, type UserUpdatePayload, type NotificationSettingsBulkPayload } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { computed, type Ref } from 'vue'
import type { UserSettings } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'
import { withQuerySignal } from '@/utils/querySignal'
import { userQueryKeys, type UserQueryPaginationParams } from '@/composables/userQueryKeys'

interface PasswordUpdateData {
    currentPassword: string
    newPassword: string
}

type PaginationParams = UserQueryPaginationParams

export { userQueryKeys } from '@/composables/userQueryKeys'

export const userSettingsQueryKey = userQueryKeys.settings

export const createMyProfileQueryOptions = (config?: AxiosRequestConfig) => ({
    queryKey: userQueryKeys.me,
    queryFn: async (context: QueryFunctionContext) => {
        return unwrapAxiosApiData(await userApi.getMyProfile(withQuerySignal(config, context)))
    },
    staleTime: QUERY_STALE_TIME.MEDIUM,
})

export const createMyAgentsQueryOptions = (config?: AxiosRequestConfig) => ({
    queryKey: userQueryKeys.agents,
    queryFn: async (context: QueryFunctionContext) => {
        return unwrapAxiosApiData(await userApi.getMyAgents(withQuerySignal(config, context)))
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
                return unwrapAxiosApiData(await userApi.getUserProfile(userId.value))
            },
            enabled: computed(() => !!userId.value),
        })
    }

    const useUserSettings = () => {
        return useQuery({
            queryKey: userSettingsQueryKey,
            queryFn: async () => {
                return unwrapAxiosApiData(await userApi.getUserSettings())
            },
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    const useBlockList = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: userQueryKeys.blocks(params),
            queryFn: async () => {
                return unwrapAxiosApiData(await userApi.getBlockList(params?.value))
            },
        })
    }

    const useNotificationSettings = () => {
        return useQuery({
            queryKey: userQueryKeys.notificationSettings,
            queryFn: async () => {
                return unwrapAxiosApiData(await userApi.getNotificationSettings())
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
                return unwrapAxiosApiData(await userApi.getMyPoint())
            },
            enabled: computed(() => enabled?.value ?? true),
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    const useMyScraps = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: userQueryKeys.scraps(params),
            queryFn: async (context: QueryFunctionContext) => {
                return unwrapAxiosApiData(await userApi.getMyScraps(params?.value ?? {}, withQuerySignal(undefined, context)))
            },
        })
    }

    const useMyPointHistories = (params?: Ref<PaginationParams>) => {
        return useQuery({
            queryKey: userQueryKeys.pointHistories(params),
            queryFn: async (context: QueryFunctionContext) => {
                return unwrapAxiosApiData(await userApi.getMyPointHistories(params?.value ?? {}, withQuerySignal(undefined, context)))
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
                return unwrapAxiosApiData(await userApi.getRecentlyViewedPosts(params?.value || {}))
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
