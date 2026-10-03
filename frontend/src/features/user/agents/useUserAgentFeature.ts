import { computed } from 'vue'
import { useQuery, type QueryFunctionContext } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import type { AxiosRequestConfig } from 'axios'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import { withQuerySignal } from '@/utils/querySignal'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import {
  resolveResponseData,
  type UserFeatureContext,
} from '@/features/user/userFeatureContext'

export const createMyAgentsQueryOptions = (generation: number, config?: AxiosRequestConfig) => ({
  queryKey: ['session', generation, ...userQueryKeys.agents] as const,
  queryFn: async (context: QueryFunctionContext) => {
    return unwrapAxiosApiData(await userApi.getMyAgents(withQuerySignal(config, context)))
  },
  staleTime: QUERY_STALE_TIME.MEDIUM,
  meta: AUTH_SCOPED_QUERY_META,
})

export function useUserAgentFeature(context: UserFeatureContext) {
  const {
    queryClient,
    authStore,
    authKey,
    captureMutationSession,
    isCurrentMutation,
  } = context

  const useMyAgents = () => useQuery({
    ...createMyAgentsQueryOptions(authStore.sessionGeneration),
    queryKey: computed(() => authKey(userQueryKeys.agents)),
  })

  const createAgentMutation = (
    request: (value: string | number, config: AxiosRequestConfig) => Promise<{ data: unknown }>,
  ) => useAuthScopedMutation({
    onMutate: captureMutationSession,
    mutationFn: async (value: string | number, mutationContext) => resolveResponseData(request(value, mutationContext.requestConfig)),
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.agents) })
    },
  })

  const useClaimAgent = () => createAgentMutation((agentToken, config) => userApi.claimAgent(String(agentToken), config))
  const useSuspendMyAgent = () => createAgentMutation(userApi.suspendMyAgent)
  const useActivateMyAgent = () => createAgentMutation(userApi.activateMyAgent)
  const useDeleteMyAgent = () => createAgentMutation(userApi.deleteMyAgent)

  return {
    useMyAgents,
    useClaimAgent,
    useSuspendMyAgent,
    useActivateMyAgent,
    useDeleteMyAgent,
  }
}
