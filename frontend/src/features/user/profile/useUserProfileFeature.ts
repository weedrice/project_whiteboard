import { createAuthScopedQueryOptions, createReactiveAuthScopedQueryOptions, getAuthQuerySource } from '@/utils/authScopedQuery'
import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useAuthScopedMutation } from '@/composables/useAuthScopedMutation'
import type { AxiosRequestConfig } from 'axios'
import { userApi, type UserUpdatePayload } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import { AUTH_SCOPED_QUERY_META, sessionQueryKey } from '@/queryAuthScope'
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
  ...createAuthScopedQueryOptions(getAuthQuerySource(), ['session', generation, ...userQueryKeys.me],
    async (context) => unwrapAxiosApiData(await userApi.getMyProfile(withQuerySignal(config, context)))),
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
    ...createReactiveAuthScopedQueryOptions(authStore, computed(() => authKey(userQueryKeys.me)),
      async (context) => unwrapAxiosApiData(await userApi.getMyProfile(withQuerySignal(undefined, context)))),
    staleTime: QUERY_STALE_TIME.MEDIUM,
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

  const useUpdateMyProfile = () => useAuthScopedMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (data: UserUpdatePayload, mutationContext) => {
      return resolveResponseData(userApi.updateMyProfile(data, mutationContext.withRequestConfig({ skipGlobalErrorHandler: true })))
    },
    onSuccess: (response, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      const savedProfile = response.data
      const currentUser = authStore.user
      if (currentUser && savedProfile?.userId === currentUser.userId) {
        // Commit known write results before the best-effort /users/me refresh.
        // A failed refresh must not advertise another free image change or an old balance.
        const imageChanged = _variables.profileImageId != null
          && savedProfile.profileImageUrl !== currentUser.profileImageUrl
        authStore.user = {
          ...currentUser,
          displayName: savedProfile.displayName,
          profileImageUrl: savedProfile.profileImageUrl ?? undefined,
          profileImageChangeFreeAvailable: imageChanged ? false : currentUser.profileImageChangeFreeAvailable,
          points: savedProfile.remainingPoints ?? currentUser.points,
        }
      }
      if ((response.data?.spentPoints ?? 0) > 0) {
        void queryClient.invalidateQueries({
          queryKey: sessionQueryKey(mutationContext.sessionGeneration, userQueryKeys.pointsRoot),
        })
      }
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

  const useBlockUser = () => useAuthScopedMutation({
    onMutate: captureMutationSession,
    mutationFn: async (userId: string | number, mutationContext) => resolveResponseData(userApi.blockUser(userId, mutationContext.requestConfig)),
    onSuccess: (_data, userId, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      void invalidateBlockVisibilityCaches(
        queryClient,
        mutationContext.sessionGeneration,
        userId,
      )
    },
  })

  const useUnblockUser = () => useAuthScopedMutation({
    onMutate: captureMutationSession,
    mutationFn: async (userId: string | number, mutationContext) => resolveResponseData(userApi.unblockUser(userId, mutationContext.requestConfig)),
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
    enabled?: Ref<boolean>,
  ) => useApiPageQuery({
    queryKey: computed(() => userQueryKeys.publicPosts(userId.value, params.value)),
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => userApi.getPublicUserPosts(userId.value, params.value, { skipGlobalErrorHandler: true }),
      (config) => userApi.getPublicUserPosts(userId.value, params.value, { ...config, skipGlobalErrorHandler: true }),
    ),
    enabled: computed(() => !!userId.value && (enabled?.value ?? true)),
    meta: { ...AUTH_SCOPED_QUERY_META, errorMessage: false },
  })

  const usePublicProfileComments = (
    userId: Ref<string | number>,
    params: Ref<UserQueryPaginationParams>,
    enabled?: Ref<boolean>,
  ) => useApiPageQuery({
    queryKey: computed(() => userQueryKeys.publicComments(userId.value, params.value)),
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => userApi.getPublicUserComments(userId.value, params.value, { skipGlobalErrorHandler: true }),
      (config) => userApi.getPublicUserComments(userId.value, params.value, { ...config, skipGlobalErrorHandler: true }),
    ),
    enabled: computed(() => !!userId.value && (enabled?.value ?? true)),
    meta: { ...AUTH_SCOPED_QUERY_META, errorMessage: false },
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
