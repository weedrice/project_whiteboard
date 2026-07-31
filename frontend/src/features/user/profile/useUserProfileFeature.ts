import { computed, type Ref } from 'vue'
import { useMutation, useQuery, type QueryFunctionContext } from '@tanstack/vue-query'
import type { AxiosRequestConfig } from 'axios'
import { userApi, type UserUpdatePayload } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'
import { callWithOptionalQuerySignal, withQuerySignal } from '@/utils/querySignal'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { invalidateBlockVisibilityCaches } from '@/features/user/blockVisibilityCache'
import { invalidateProfileAuthorCaches } from '@/features/user/profile/profileCacheInvalidation'
import { userQueryKeys, type UserQueryPaginationParams } from '@/features/user/userQueryKeys'
import {
  resolveResponseData,
  type UserFeatureContext,
} from '@/features/user/userFeatureContext'

export const createMyProfileQueryOptions = (generation: number, config?: AxiosRequestConfig) => ({
  queryKey: ['session', generation, ...userQueryKeys.me] as const,
  queryFn: async (context: QueryFunctionContext) => {
    return unwrapAxiosApiData(await userApi.getMyProfile(withQuerySignal(config, context)))
  },
  staleTime: QUERY_STALE_TIME.MEDIUM,
  meta: AUTH_SCOPED_QUERY_META,
})

export function useUserProfileFeature(context: UserFeatureContext) {
  const {
    queryClient,
    authStore,
    authKey,
    captureMutationSession,
    isCurrentMutation,
  } = context

  const useMyProfile = () => useQuery({
    ...createMyProfileQueryOptions(authStore.sessionGeneration),
    queryKey: computed(() => authKey(userQueryKeys.me)),
  })

  const useUserProfile = (userId: Ref<string | number>) => useApiQuery({
    queryKey: computed(() => userQueryKeys.profile(userId.value)),
    meta: AUTH_SCOPED_QUERY_META,
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => userApi.getUserProfile(userId.value),
      (config) => userApi.getUserProfile(userId.value, config),
    ),
    enabled: computed(() => !!userId.value),
  })

  const useUpdateMyProfile = () => useMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (data: UserUpdatePayload) => {
      return resolveResponseData(userApi.updateMyProfile(data, { skipGlobalErrorHandler: true }))
    },
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      const userId = authStore.user?.userId
      if (userId == null) {
        queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.me) })
        return
      }
      void invalidateProfileAuthorCaches(
        queryClient,
        mutationContext.sessionGeneration,
        userId,
      )
    },
  })

  const useBlockList = (params?: Ref<UserQueryPaginationParams>) => useApiQuery({
    queryKey: computed(() => userQueryKeys.blocks(params?.value)),
    meta: AUTH_SCOPED_QUERY_META,
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => userApi.getBlockList(params?.value),
      (config) => userApi.getBlockList(params?.value, config),
    ),
  })

  const useBlockUser = () => useMutation({
    onMutate: captureMutationSession,
    mutationFn: async (userId: string | number) => resolveResponseData(userApi.blockUser(userId)),
    onSuccess: (_data, userId, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      void invalidateBlockVisibilityCaches(
        queryClient,
        mutationContext.sessionGeneration,
        userId,
      )
    },
  })

  const useUnblockUser = () => useMutation({
    onMutate: captureMutationSession,
    mutationFn: async (userId: string | number) => resolveResponseData(userApi.unblockUser(userId)),
    onSuccess: (_data, userId, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      void invalidateBlockVisibilityCaches(
        queryClient,
        mutationContext.sessionGeneration,
        userId,
      )
    },
  })

  const usePublicProfilePosts = (
    userId: Ref<string | number>,
    params: Ref<UserQueryPaginationParams>,
  ) => useApiPageQuery({
    queryKey: computed(() => userQueryKeys.publicPosts(userId.value, params.value)),
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => userApi.getPublicUserPosts(userId.value, params.value),
      (config) => userApi.getPublicUserPosts(userId.value, params.value, config),
    ),
    enabled: computed(() => !!userId.value),
    meta: AUTH_SCOPED_QUERY_META,
  })

  const usePublicProfileComments = (
    userId: Ref<string | number>,
    params: Ref<UserQueryPaginationParams>,
  ) => useApiPageQuery({
    queryKey: computed(() => userQueryKeys.publicComments(userId.value, params.value)),
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => userApi.getPublicUserComments(userId.value, params.value),
      (config) => userApi.getPublicUserComments(userId.value, params.value, config),
    ),
    enabled: computed(() => !!userId.value),
    meta: AUTH_SCOPED_QUERY_META,
  })

  return {
    useMyProfile,
    useUserProfile,
    useUpdateMyProfile,
    useBlockList,
    useBlockUser,
    useUnblockUser,
    usePublicProfilePosts,
    usePublicProfileComments,
  }
}
