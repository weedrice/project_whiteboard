import { useQuery, useMutation, useQueryClient, type QueryFunctionContext } from '@tanstack/vue-query'
import {
    userApi,
    type KeywordSubscriptionPayload,
    type KeywordSubscriptionResponse,
    type UserUpdatePayload,
    type NotificationSettingsBulkPayload,
    type PointHistoryParams,
} from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { computed, type Ref } from 'vue'
import type { DraftPostListResponse, DraftPostSummary, LoginHistory, PageResponse, UserPoint, UserSettingsUpdatePayload } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import type { AxiosRequestConfig } from 'axios'
import { callWithOptionalQuerySignal, withQuerySignal } from '@/utils/querySignal'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { userQueryKeys, type UserQueryPaginationParams } from '@/features/user/userQueryKeys'
import { postApi, type ScheduledPost } from '@/api/post'
import { badgeApi } from '@/api/badge'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import {
    currentSessionQueryKey,
    isSessionGenerationCurrent,
} from '@/queryAuthScope'
import { useAuthStore } from '@/stores/auth'
import { invalidateBlockVisibilityCaches } from '@/features/user/blockVisibilityCache'
import { invalidateProfileAuthorCaches } from '@/features/user/profile/profileCacheInvalidation'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'

interface PasswordUpdateData {
    currentPassword: string
    newPassword: string
}

type PaginationParams = UserQueryPaginationParams

export { userQueryKeys } from '@/features/user/userQueryKeys'

export const userSettingsQueryKey = userQueryKeys.settings
export const userSettingsSessionQueryKey = (generation: number) =>
    ['session', generation, ...userQueryKeys.settings] as const

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

export const createMyProfileQueryOptions = (generation: number, config?: AxiosRequestConfig) => ({
    queryKey: ['session', generation, ...userQueryKeys.me] as const,
    queryFn: async (context: QueryFunctionContext) => {
        return unwrapAxiosApiData(await userApi.getMyProfile(withQuerySignal(config, context)))
    },
    staleTime: QUERY_STALE_TIME.MEDIUM,
    meta: AUTH_SCOPED_QUERY_META,
})

export const createMyAgentsQueryOptions = (generation: number, config?: AxiosRequestConfig) => ({
    queryKey: ['session', generation, ...userQueryKeys.agents] as const,
    queryFn: async (context: QueryFunctionContext) => {
        return unwrapAxiosApiData(await userApi.getMyAgents(withQuerySignal(config, context)))
    },
    staleTime: QUERY_STALE_TIME.MEDIUM,
    meta: AUTH_SCOPED_QUERY_META,
})

export function useUser() {
    const queryClient = useQueryClient()
    const authStore = useAuthStore()
    const authKey = (queryKey: readonly unknown[]) => currentSessionQueryKey(authStore, queryKey)
    const captureMutationSession = () => ({ sessionGeneration: authStore.sessionGeneration })
    const isCurrentMutation = (context?: { sessionGeneration: number }) => (
        context !== undefined && isSessionGenerationCurrent(authStore, context.sessionGeneration)
    )

    // --- Queries ---

    const useMyProfile = () => {
        return useQuery({
            ...createMyProfileQueryOptions(authStore.sessionGeneration),
            queryKey: computed(() => authKey(userQueryKeys.me)),
        })
    }

    const useUserProfile = (userId: Ref<string | number>) => {
        return useApiQuery({
            queryKey: computed(() => userQueryKeys.profile(userId.value)),
            meta: AUTH_SCOPED_QUERY_META,
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
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useBlockList = (params?: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: computed(() => userQueryKeys.blocks(params?.value)),
            meta: AUTH_SCOPED_QUERY_META,
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
            meta: AUTH_SCOPED_QUERY_META,
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
            meta: AUTH_SCOPED_QUERY_META,
            request: (context) => callWithOptionalQuerySignal(
                context,
                userApi.getKeywordSubscriptions,
                userApi.getKeywordSubscriptions,
            ),
        })
    }

    const useMyAgents = () => {
        return useQuery({
            ...createMyAgentsQueryOptions(authStore.sessionGeneration),
            queryKey: computed(() => authKey(userQueryKeys.agents)),
        })
    }

    const useMySessions = () => {
        return useApiQuery({
            queryKey: userQueryKeys.sessions,
            request: (context) => callWithOptionalQuerySignal(
                context,
                userApi.getMySessions,
                userApi.getMySessions,
            ),
            staleTime: QUERY_STALE_TIME.SHORT,
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useMyLoginHistory = (params?: Ref<PaginationParams>) => {
        return useApiPageQuery<LoginHistory>({
            queryKey: computed(() => userQueryKeys.loginHistory(params?.value)),
            request: (context) => userApi.getMyLoginHistory(params?.value ?? {}, withQuerySignal(undefined, context)),
            staleTime: QUERY_STALE_TIME.SHORT,
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useMyPoint = (enabled?: Ref<boolean>, userIdentity?: Ref<string | number | null | undefined>) => {
        return useApiQuery<UserPoint>({
            queryKey: computed(() => userQueryKeys.myPoints(userIdentity?.value)),
            request: (context) => userApi.getMyPoint(withQuerySignal(undefined, context)),
            enabled: computed(() => enabled?.value ?? true),
            staleTime: QUERY_STALE_TIME.SHORT,
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useMyScraps = (params?: Ref<PaginationParams>) => {
        return useApiPageQuery({
            queryKey: computed(() => userQueryKeys.scraps(params?.value)),
            meta: AUTH_SCOPED_QUERY_META,
            request: (context) => userApi.getMyScraps(params?.value ?? {}, withQuerySignal(undefined, context)),
        })
    }

    const useMyDrafts = (params?: Ref<PaginationParams>) => {
        return useApiQuery<DraftPostListResponse, PageResponse<DraftPostSummary>>({
            queryKey: computed(() => userQueryKeys.drafts(params?.value)),
            request: (context) => userApi.getMyDrafts(params?.value ?? {}, withQuerySignal(undefined, context)),
            selectData: toDraftPageResponse,
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useMyScheduledPosts = (params?: Ref<PaginationParams>) => {
        return useApiQuery<PageResponseRaw<ScheduledPost>, PageResponse<ScheduledPost>>({
            queryKey: computed(() => userQueryKeys.scheduledPosts(params?.value)),
            request: (context) => postApi.getMyScheduledPosts(params?.value ?? {}, withQuerySignal(undefined, context)),
            selectData: (response) => normalizePageResponse(response),
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const useUserBadges = (userId: Ref<string | number>) => {
        return useApiQuery({
            queryKey: computed(() => userQueryKeys.badges(userId.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => badgeApi.getUserBadges(userId.value),
                (config) => apiGetUserBadgesWithConfig(userId.value, config),
            ),
            enabled: computed(() => !!userId.value),
        })
    }

    const useMyPointHistories = (params?: Ref<PointHistoryParams>) => {
        return useApiQuery({
            queryKey: computed(() => userQueryKeys.pointHistories(params?.value)),
            meta: AUTH_SCOPED_QUERY_META,
            request: (context) => userApi.getMyPointHistories(params?.value ?? {}, withQuerySignal(undefined, context)),
        })
    }

    // --- Mutations ---

    const useUpdateMyProfile = () => {
        return useMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (data: UserUpdatePayload) => {
                return resolveResponseData(userApi.updateMyProfile(data, { skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                const userId = authStore.user?.userId
                if (userId == null) {
                    queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.me) })
                    return
                }
                void invalidateProfileAuthorCaches(queryClient, context.sessionGeneration, userId)
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

    const useRevokeMySession = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (sessionId: string | number) => {
                return resolveResponseData(userApi.revokeMySession(sessionId, { skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.sessions) })
            }
        })
    }

    const useRevokeOtherSessions = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async () => {
                return resolveResponseData(userApi.revokeOtherSessions({ skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.sessions) })
            }
        })
    }

    const useDeleteAccount = () => {
        return useMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (password: string) => {
                return resolveResponseData(userApi.deleteAccount(password, { skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                // Handle logout or redirect in component
                queryClient.clear()
            }
        })
    }

    const useUpdateUserSettings = () => {
        return useMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (data: UserSettingsUpdatePayload) => {
                return resolveResponseData(userApi.updateUserSettings(data, { skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userSettingsQueryKey) })
            }
        })
    }

    const useUpdateNotificationSettings = () => {
        return useMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (data: NotificationSettingsBulkPayload) => {
                return resolveResponseData(userApi.updateNotificationSettingsBulk(data, { skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.notificationSettings) })
            }
        })
    }

    const useCompleteOnboarding = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (signal?: AbortSignal) => {
                return resolveResponseData(userApi.completeOnboarding(signal ? {
                    signal,
                    skipGlobalErrorHandler: true,
                } : undefined))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userSettingsQueryKey) })
            }
        })
    }

    const useCreateKeywordSubscription = () => {
        return useMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (data: KeywordSubscriptionPayload) => {
                return resolveResponseData(userApi.createKeywordSubscription(data, { skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.keywordSubscriptions) })
            }
        })
    }

    const useDeleteKeywordSubscription = () => {
        return useMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (data: KeywordSubscriptionPayload) => {
                return resolveResponseData(userApi.deleteKeywordSubscription(data, { skipGlobalErrorHandler: true }))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.keywordSubscriptions) })
            }
        })
    }

    const useClaimAgent = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (agentToken: string) => {
                return resolveResponseData(userApi.claimAgent(agentToken))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.agents) })
            }
        })
    }

    const useSuspendMyAgent = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (agentId: string | number) => {
                return resolveResponseData(userApi.suspendMyAgent(agentId))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.agents) })
            }
        })
    }

    const useActivateMyAgent = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (agentId: string | number) => {
                return resolveResponseData(userApi.activateMyAgent(agentId))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.agents) })
            }
        })
    }

    const useDeleteMyAgent = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (agentId: string | number) => {
                return resolveResponseData(userApi.deleteMyAgent(agentId))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.agents) })
            }
        })
    }

    const useBlockUser = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (userId: string | number) => {
                return resolveResponseData(userApi.blockUser(userId))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                void invalidateBlockVisibilityCaches(queryClient, context.sessionGeneration, _variables)
            }
        })
    }

    const useUnblockUser = () => {
        return useMutation({
            onMutate: captureMutationSession,
            mutationFn: async (userId: string | number) => {
                return resolveResponseData(userApi.unblockUser(userId))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                void invalidateBlockVisibilityCaches(queryClient, context.sessionGeneration, _variables)
            }
        })
    }

    const useUpdateRepresentativeBadge = () => {
        return useMutation({
            meta: LOCAL_MUTATION_ERROR_META,
            onMutate: captureMutationSession,
            mutationFn: async (badgeCode: string | null) => {
                return unwrapAxiosApiData(await badgeApi.updateRepresentativeBadge(badgeCode))
            },
            onSuccess: (_data, _variables, context) => {
                if (!isCurrentMutation(context)) return
                queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.me) })
                queryClient.invalidateQueries({ queryKey: userQueryKeys.badgesRoot })
            }
        })
    }

    const useRecentlyViewedPosts = (params?: Ref<PaginationParams>) => {
        return useApiQuery({
            queryKey: computed(() => userQueryKeys.recentlyViewedPosts(params?.value)),
            meta: AUTH_SCOPED_QUERY_META,
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getRecentlyViewedPosts(params?.value || {}),
                (config) => userApi.getRecentlyViewedPosts(params?.value || {}, config),
            ),
        })
    }

    const usePublicProfilePosts = (userId: Ref<string | number>, params: Ref<PaginationParams>) => {
        return useApiPageQuery({
            queryKey: computed(() => userQueryKeys.publicPosts(userId.value, params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getPublicUserPosts(userId.value, params.value),
                (config) => userApi.getPublicUserPosts(userId.value, params.value, config),
            ),
            enabled: computed(() => !!userId.value),
            meta: AUTH_SCOPED_QUERY_META,
        })
    }

    const usePublicProfileComments = (userId: Ref<string | number>, params: Ref<PaginationParams>) => {
        return useApiPageQuery({
            queryKey: computed(() => userQueryKeys.publicComments(userId.value, params.value)),
            request: (context) => callWithOptionalQuerySignal(
                context,
                () => userApi.getPublicUserComments(userId.value, params.value),
                (config) => userApi.getPublicUserComments(userId.value, params.value, config),
            ),
            enabled: computed(() => !!userId.value),
            meta: AUTH_SCOPED_QUERY_META,
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
        useMySessions,
        useMyLoginHistory,
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
        useRevokeMySession,
        useRevokeOtherSessions,
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
