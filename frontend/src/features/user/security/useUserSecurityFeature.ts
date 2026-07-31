import { computed, type Ref } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { userApi } from '@/api/user'
import type { LoginHistory } from '@/types'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'
import { withQuerySignal } from '@/utils/querySignal'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { userQueryKeys, type UserQueryPaginationParams } from '@/features/user/userQueryKeys'
import {
  resolveResponseData,
  type UserFeatureContext,
} from '@/features/user/userFeatureContext'

interface PasswordUpdateData {
  currentPassword: string
  newPassword: string
}

export function useUserSecurityFeature(context: UserFeatureContext) {
  const {
    queryClient,
    authKey,
    captureMutationSession,
    isCurrentMutation,
  } = context

  const useMySessions = () => useApiQuery({
    queryKey: userQueryKeys.sessions,
    request: (queryContext) => userApi.getMySessions(withQuerySignal(undefined, queryContext)),
    staleTime: QUERY_STALE_TIME.SHORT,
    meta: AUTH_SCOPED_QUERY_META,
  })

  const useMyLoginHistory = (params?: Ref<UserQueryPaginationParams>) => useApiPageQuery<LoginHistory>({
    queryKey: computed(() => userQueryKeys.loginHistory(params?.value)),
    request: (queryContext) => userApi.getMyLoginHistory(
      params?.value ?? {},
      withQuerySignal(undefined, queryContext),
    ),
    staleTime: QUERY_STALE_TIME.SHORT,
    meta: AUTH_SCOPED_QUERY_META,
  })

  const useUpdatePassword = () => useMutation({
    mutationFn: async ({ currentPassword, newPassword }: PasswordUpdateData) => {
      return resolveResponseData(userApi.updatePassword(currentPassword, newPassword))
    },
  })

  const useRevokeMySession = () => useMutation({
    onMutate: captureMutationSession,
    mutationFn: async (sessionId: string | number) => resolveResponseData(userApi.revokeMySession(
      sessionId,
      { skipGlobalErrorHandler: true },
    )),
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.sessions) })
    },
  })

  const useRevokeOtherSessions = () => useMutation({
    onMutate: captureMutationSession,
    mutationFn: async () => resolveResponseData(userApi.revokeOtherSessions({
      skipGlobalErrorHandler: true,
    })),
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.sessions) })
    },
  })

  const useDeleteAccount = () => useMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (password: string) => resolveResponseData(userApi.deleteAccount(
      password,
      { skipGlobalErrorHandler: true },
    )),
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.clear()
    },
  })

  return {
    useMySessions,
    useMyLoginHistory,
    useUpdatePassword,
    useRevokeMySession,
    useRevokeOtherSessions,
    useDeleteAccount,
  }
}
