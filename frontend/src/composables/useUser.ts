import { useQuery, useMutation, useQueryClient, type QueryFunctionContext } from '@tanstack/vue-query'
import { userApi, type UserUpdatePayload, type NotificationSettingsBulkPayload } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { computed, type Ref } from 'vue'
import type { UserPoint, UserSettings } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'
import { callWithOptionalQuerySignal, withQuerySignal } from '@/utils/querySignal'
import { useApiQuery } from '@/composables/useApiQuery'
import { userQueryKeys, type UserQueryPaginationParams } from '@/composables/userQueryKeys'

interface PasswordUpdateData {
    currentPassword: string
    newPassword: string
}

type PaginationParams = UserQueryPaginationParams

export { userQueryKeys } from '@/composables/userQueryKeys'

export const userSettingsQueryKey = userQueryKeys.settings

const resolveResponseData = async <T>(request: Promise<{ data: T }>): Promise<T> => {
    const { data } = await request
    return data
}

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
        return useApiQuery({
            queryKey: userQueryKeys.profile(userId),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getUserProfile(userId.value),
                (config) => userApi.getUserProfile(userId.value, config),
            ),
            enabled: computed(() => !!userId.value),
        })
    }

    const useUserSettings = () => {
        return useApiQuery({
            queryKey: userSettingsQueryKey,
            request: (context) => callWithOptionalQuerySignal(
                context,
                userApi.getUserSettings,
                userApi.getUserSettings,
            ),
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    const useBlockList = (params?: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: userQueryKeys.blocks(params),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getBlockList(params?.value),
                (config) => userApi.getBlockList(params?.value, config),
            ),
        })
    }

    const useNotificationSettings = () => {
        return useApiQuery({
            queryKey: userQueryKeys.notificationSettings,
            request: (context) => callWithOptionalQuerySignal(
                context,
                userApi.getNotificationSettings,
                userApi.getNotificationSettings,
            ),
        })
    }

    const useMyAgents = () => {
        return useQuery(createMyAgentsQueryOptions())
    }

    const useMyPoint = (enabled?: Ref<boolean>, userIdentity?: Ref<string | number | null | undefined>) => {
        return useApiQuery<UserPoint>({
            queryKey: userQueryKeys.myPoints(userIdentity),
            request: () => userApi.getMyPoint(),
            enabled: computed(() => enabled?.value ?? true),
            staleTime: QUERY_STALE_TIME.SHORT,
        })
    }

    const useMyScraps = (params?: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: userQueryKeys.scraps(params),
            request: (context) => userApi.getMyScraps(params?.value ?? {}, withQuerySignal(undefined, context)),
        })
    }

    const useMyPointHistories = (params?: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: userQueryKeys.pointHistories(params),
            request: (context) => userApi.getMyPointHistories(params?.value ?? {}, withQuerySignal(undefined, context)),
        })
    }

    // --- Mutations ---

    const useUpdateMyProfile = () => {
        return useMutation({
            mutationFn: async (data: UserUpdatePayload) => {
                return resolveResponseData(userApi.updateMyProfile(data))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.me })
            }
        })
    }

    const useUpdatePassword = () => {
        return useMutation({
            mutationFn: async ({ currentPassword, newPassword }: PasswordUpdateData) => {
                return resolveResponseData(userApi.updatePassword(currentPassword, newPassword))
            }
        })
    }

    const useDeleteAccount = () => {
        return useMutation({
            mutationFn: async (password: string) => {
                return resolveResponseData(userApi.deleteAccount(password))
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
                return resolveResponseData(userApi.updateUserSettings(data))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userSettingsQueryKey })
            }
        })
    }

    const useUpdateNotificationSettings = () => {
        return useMutation({
            mutationFn: async (data: NotificationSettingsBulkPayload) => {
                return resolveResponseData(userApi.updateNotificationSettingsBulk(data))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.notificationSettings })
            }
        })
    }

    const useClaimAgent = () => {
        return useMutation({
            mutationFn: async (agentToken: string) => {
                return resolveResponseData(userApi.claimAgent(agentToken))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
            }
        })
    }

    const useSuspendMyAgent = () => {
        return useMutation({
            mutationFn: async (agentId: string | number) => {
                return resolveResponseData(userApi.suspendMyAgent(agentId))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
            }
        })
    }

    const useActivateMyAgent = () => {
        return useMutation({
            mutationFn: async (agentId: string | number) => {
                return resolveResponseData(userApi.activateMyAgent(agentId))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
            }
        })
    }

    const useDeleteMyAgent = () => {
        return useMutation({
            mutationFn: async (agentId: string | number) => {
                return resolveResponseData(userApi.deleteMyAgent(agentId))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.agents })
            }
        })
    }

    const useBlockUser = () => {
        return useMutation({
            mutationFn: async (userId: string | number) => {
                return resolveResponseData(userApi.blockUser(userId))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.blocksRoot })
            }
        })
    }

    const useUnblockUser = () => {
        return useMutation({
            mutationFn: async (userId: string | number) => {
                return resolveResponseData(userApi.unblockUser(userId))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.blocksRoot })
            }
        })
    }

    const useRecentlyViewedPosts = (params?: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: userQueryKeys.recentlyViewedPosts(params),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getRecentlyViewedPosts(params?.value || {}),
                (config) => userApi.getRecentlyViewedPosts(params?.value || {}, config),
            ),
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
