import { useQuery, useMutation, useQueryClient, type QueryFunctionContext } from '@tanstack/vue-query'
import {
    userApi,
    type KeywordSubscriptionPayload,
    type KeywordSubscriptionResponse,
    type UserUpdatePayload,
    type NotificationSettingsBulkPayload
} from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { computed, type Ref } from 'vue'
import type { DraftPostListResponse, DraftPostSummary, PageResponse, UserPoint, UserSettings } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'
import { callWithOptionalQuerySignal, withQuerySignal } from '@/utils/querySignal'
import { useApiQuery } from '@/composables/useApiQuery'
import { userQueryKeys, type UserQueryPaginationParams } from '@/composables/userQueryKeys'
import { postApi, type ScheduledPost } from '@/api/post'
import { badgeApi } from '@/api/badge'

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

const toDraftPageResponse = (data: DraftPostListResponse): PageResponse<DraftPostSummary> => ({
    content: data.content,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    size: data.size,
    number: data.page,
    first: !data.hasPrevious,
    last: !data.hasNext,
    empty: data.content.length === 0,
})

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
            staleTime: QUERY_STALE_TIME.MEDIUM,
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

    const useKeywordSubscriptions = () => {
        return useApiQuery<KeywordSubscriptionResponse[]>({
            queryKey: userQueryKeys.keywordSubscriptions,
            request: (context) => callWithOptionalQuerySignal(
                context,
                userApi.getKeywordSubscriptions,
                userApi.getKeywordSubscriptions,
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

    const useMyDrafts = (params?: Ref<PaginationParams>) => {
        return useApiQuery<DraftPostListResponse, PageResponse<DraftPostSummary>>({
            queryKey: userQueryKeys.drafts(params),
            request: (context) => userApi.getMyDrafts(params?.value ?? {}, withQuerySignal(undefined, context)),
            selectData: toDraftPageResponse,
        })
    }

    const useMyScheduledPosts = (params?: Ref<PaginationParams>) => {
        return useApiQuery<PageResponse<ScheduledPost>>({
            queryKey: userQueryKeys.scheduledPosts(params),
            request: (context) => postApi.getMyScheduledPosts(params?.value ?? {}, withQuerySignal(undefined, context)),
        })
    }

    const useUserBadges = (userId: Ref<string | number>) => {
        return useApiQuery({
            queryKey: userQueryKeys.badges(userId),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => badgeApi.getUserBadges(userId.value),
                (config) => apiGetUserBadgesWithConfig(userId.value, config),
            ),
            enabled: computed(() => !!userId.value),
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

    const useCompleteOnboarding = () => {
        return useMutation({
            mutationFn: async () => {
                return resolveResponseData(userApi.completeOnboarding())
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userSettingsQueryKey })
            }
        })
    }

    const useCreateKeywordSubscription = () => {
        return useMutation({
            mutationFn: async (data: KeywordSubscriptionPayload) => {
                return resolveResponseData(userApi.createKeywordSubscription(data))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.keywordSubscriptions })
            }
        })
    }

    const useDeleteKeywordSubscription = () => {
        return useMutation({
            mutationFn: async (data: KeywordSubscriptionPayload) => {
                return resolveResponseData(userApi.deleteKeywordSubscription(data))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.keywordSubscriptions })
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

    const useUpdateRepresentativeBadge = () => {
        return useMutation({
            mutationFn: async (badgeCode: string | null) => {
                return unwrapAxiosApiData(await badgeApi.updateRepresentativeBadge(badgeCode))
            },
            onSuccess: () => {
                queryClient.invalidateQueries({ queryKey: userQueryKeys.me })
                queryClient.invalidateQueries({ queryKey: userQueryKeys.badgesRoot })
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

    const usePublicProfilePosts = (userId: Ref<string | number>, params: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: userQueryKeys.publicPosts(userId, params),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getPublicUserPosts(userId.value, params.value),
                (config) => userApi.getPublicUserPosts(userId.value, params.value, config),
            ),
            enabled: computed(() => !!userId.value),
        })
    }

    const usePublicProfileComments = (userId: Ref<string | number>, params: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: userQueryKeys.publicComments(userId, params),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getPublicUserComments(userId.value, params.value),
                (config) => userApi.getPublicUserComments(userId.value, params.value, config),
            ),
            enabled: computed(() => !!userId.value),
        })
    }

    return {
        useMyProfile,
        useUserProfile,
        useUserSettings,
        useNotificationSettings,
        useKeywordSubscriptions,
        useBlockList,
        useMyAgents,
        useMyPoint,
        useMyScraps,
        useMyDrafts,
        useMyScheduledPosts,
        useUserBadges,
        useMyPointHistories,
        useRecentlyViewedPosts,
        usePublicProfilePosts,
        usePublicProfileComments,
        useUpdateMyProfile,
        useUpdatePassword,
        useDeleteAccount,
        useUpdateUserSettings,
        useCompleteOnboarding,
        useUpdateNotificationSettings,
        useCreateKeywordSubscription,
        useDeleteKeywordSubscription,
        useClaimAgent,
        useSuspendMyAgent,
        useActivateMyAgent,
        useDeleteMyAgent,
        useBlockUser,
        useUnblockUser,
        useUpdateRepresentativeBadge,
    }
}

function apiGetUserBadgesWithConfig(userId: string | number, config?: AxiosRequestConfig) {
    return badgeApi.getUserBadges(userId, config)
}
