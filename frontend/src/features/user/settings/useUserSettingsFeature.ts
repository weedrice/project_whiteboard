import { useMutation } from '@tanstack/vue-query'
import {
  userApi,
  type KeywordSubscriptionPayload,
  type KeywordSubscriptionResponse,
  type NotificationSettingsBulkPayload,
} from '@/api/user'
import type { UserSettingsUpdatePayload } from '@/types'
import { useApiQuery } from '@/composables/useApiQuery'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'
import { callWithOptionalQuerySignal } from '@/utils/querySignal'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import {
  resolveResponseData,
  type UserFeatureContext,
} from '@/features/user/userFeatureContext'

export const userSettingsQueryKey = userQueryKeys.settings
export const userSettingsSessionQueryKey = (generation: number) =>
  ['session', generation, ...userQueryKeys.settings] as const

export function useUserSettingsFeature(context: UserFeatureContext) {
  const {
    queryClient,
    authKey,
    captureMutationSession,
    isCurrentMutation,
  } = context

  const useUserSettings = () => useApiQuery({
    queryKey: userSettingsQueryKey,
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      userApi.getUserSettings,
      userApi.getUserSettings,
    ),
    staleTime: QUERY_STALE_TIME.MEDIUM,
    meta: AUTH_SCOPED_QUERY_META,
  })

  const useNotificationSettings = () => useApiQuery({
    queryKey: userQueryKeys.notificationSettings,
    meta: AUTH_SCOPED_QUERY_META,
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      userApi.getNotificationSettings,
      userApi.getNotificationSettings,
    ),
  })

  const useKeywordSubscriptions = () => useApiQuery<KeywordSubscriptionResponse[]>({
    queryKey: userQueryKeys.keywordSubscriptions,
    meta: AUTH_SCOPED_QUERY_META,
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      userApi.getKeywordSubscriptions,
      userApi.getKeywordSubscriptions,
    ),
  })

  const useUpdateUserSettings = () => useMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (data: UserSettingsUpdatePayload) => {
      return resolveResponseData(userApi.updateUserSettings(data, { skipGlobalErrorHandler: true }))
    },
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userSettingsQueryKey) })
    },
  })

  const useUpdateNotificationSettings = () => useMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (data: NotificationSettingsBulkPayload) => {
      return resolveResponseData(userApi.updateNotificationSettingsBulk(
        data,
        { skipGlobalErrorHandler: true },
      ))
    },
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.notificationSettings) })
    },
  })

  const useCompleteOnboarding = () => useMutation({
    onMutate: captureMutationSession,
    mutationFn: async (signal?: AbortSignal) => {
      return resolveResponseData(userApi.completeOnboarding(signal ? {
        signal,
        skipGlobalErrorHandler: true,
      } : undefined))
    },
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userSettingsQueryKey) })
    },
  })

  const useCreateKeywordSubscription = () => useMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (data: KeywordSubscriptionPayload) => {
      return resolveResponseData(userApi.createKeywordSubscription(
        data,
        { skipGlobalErrorHandler: true },
      ))
    },
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.keywordSubscriptions) })
    },
  })

  const useDeleteKeywordSubscription = () => useMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (data: KeywordSubscriptionPayload) => {
      return resolveResponseData(userApi.deleteKeywordSubscription(
        data,
        { skipGlobalErrorHandler: true },
      ))
    },
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.keywordSubscriptions) })
    },
  })

  return {
    useUserSettings,
    useNotificationSettings,
    useKeywordSubscriptions,
    useUpdateUserSettings,
    useUpdateNotificationSettings,
    useCompleteOnboarding,
    useCreateKeywordSubscription,
    useDeleteKeywordSubscription,
  }
}
