import { computed, type Ref } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { badgeApi } from '@/api/badge'
import { postApi, type ScheduledPost } from '@/api/post'
import { userApi, type PointHistoryParams } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import { useApiPageQuery, useApiQuery } from '@/composables/useApiQuery'
import type {
  Badge,
  DraftPostListResponse,
  DraftPostPageResponse,
  PageResponse,
  UserPoint,
} from '@/types'
import { AUTH_SCOPED_QUERY_META } from '@/queryAuthScope'
import { LOCAL_MUTATION_ERROR_META } from '@/mutationErrorOwnership'
import { callWithOptionalQuerySignal, withQuerySignal } from '@/utils/querySignal'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { normalizePageResponse, type PageResponseRaw } from '@/utils/pageResponse'
import { invalidateProfileAuthorCaches } from '@/features/user/profile/profileCacheInvalidation'
import { userQueryKeys, type UserQueryPaginationParams } from '@/features/user/userQueryKeys'
import type { UserFeatureContext } from '@/features/user/userFeatureContext'

function toDraftPageResponse(data: DraftPostListResponse): DraftPostPageResponse {
  return {
    content: data.content,
    totalElements: data.totalElements,
    totalPages: data.totalPages,
    size: data.size,
    number: data.page,
    first: !data.hasPrevious,
    last: !data.hasNext,
    empty: data.content.length === 0,
    retentionDays: data.retentionDays,
    maxDraftsPerUser: data.maxDraftsPerUser,
  }
}

export function useUserActivityFeature(context: UserFeatureContext) {
  const {
    queryClient,
    authStore,
    authKey,
    captureMutationSession,
    isCurrentMutation,
  } = context

  const useMyPoint = (
    enabled?: Ref<boolean>,
    userIdentity?: Ref<string | number | null | undefined>,
  ) => useApiQuery<UserPoint>({
    queryKey: computed(() => userQueryKeys.myPoints(userIdentity?.value)),
    request: (queryContext) => userApi.getMyPoint(withQuerySignal(undefined, queryContext)),
    enabled: computed(() => enabled?.value ?? true),
    staleTime: QUERY_STALE_TIME.SHORT,
    meta: AUTH_SCOPED_QUERY_META,
  })

  const useMyScraps = (params?: Ref<UserQueryPaginationParams>) => useApiPageQuery({
    queryKey: computed(() => userQueryKeys.scraps(params?.value)),
    meta: AUTH_SCOPED_QUERY_META,
    request: (queryContext) => userApi.getMyScraps(
      params?.value ?? {},
      withQuerySignal(undefined, queryContext),
    ),
  })

  const useMyDrafts = (params?: Ref<UserQueryPaginationParams>) => useApiQuery<
    DraftPostListResponse,
    DraftPostPageResponse
  >({
    queryKey: computed(() => userQueryKeys.drafts(params?.value)),
    request: (queryContext) => userApi.getMyDrafts(
      params?.value ?? {},
      withQuerySignal(undefined, queryContext),
    ),
    selectData: toDraftPageResponse,
    meta: AUTH_SCOPED_QUERY_META,
  })

  const useMyScheduledPosts = (params?: Ref<UserQueryPaginationParams>) => useApiQuery<
    PageResponseRaw<ScheduledPost>,
    PageResponse<ScheduledPost>
  >({
    queryKey: computed(() => userQueryKeys.scheduledPosts(params?.value)),
    request: (queryContext) => postApi.getMyScheduledPosts(
      params?.value ?? {},
      withQuerySignal(undefined, queryContext),
    ),
    selectData: normalizePageResponse,
    meta: AUTH_SCOPED_QUERY_META,
  })

  const useUserBadges = (userId: Ref<string | number>) => useApiQuery({
    queryKey: computed(() => userQueryKeys.badges(userId.value)),
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => badgeApi.getUserBadges(userId.value),
      (config) => badgeApi.getUserBadges(userId.value, config),
    ),
    enabled: computed(() => !!userId.value),
  })

  const useMyBadges = () => useApiQuery<Badge[]>({
    queryKey: userQueryKeys.myBadges,
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => badgeApi.getMyBadges(),
      (config) => badgeApi.getMyBadges(config),
    ),
    meta: AUTH_SCOPED_QUERY_META,
  })

  const useMyPointHistories = (params?: Ref<PointHistoryParams>) => useApiQuery({
    queryKey: computed(() => userQueryKeys.pointHistories(params?.value)),
    meta: AUTH_SCOPED_QUERY_META,
    request: (queryContext) => userApi.getMyPointHistories(
      params?.value ?? {},
      withQuerySignal(undefined, queryContext),
    ),
  })

  const useRecentlyViewedPosts = (params?: Ref<UserQueryPaginationParams>) => useApiQuery({
    queryKey: computed(() => userQueryKeys.recentlyViewedPosts(params?.value)),
    meta: AUTH_SCOPED_QUERY_META,
    request: (queryContext) => callWithOptionalQuerySignal(
      queryContext,
      () => userApi.getRecentlyViewedPosts(params?.value || {}),
      (config) => userApi.getRecentlyViewedPosts(params?.value || {}, config),
    ),
  })

  const useUpdateRepresentativeBadge = () => useMutation({
    meta: LOCAL_MUTATION_ERROR_META,
    onMutate: captureMutationSession,
    mutationFn: async (badgeCode: string | null) => {
      return unwrapAxiosApiData(await badgeApi.updateRepresentativeBadge(badgeCode))
    },
    onSuccess: (_data, _variables, mutationContext) => {
      if (!isCurrentMutation(mutationContext)) return
      void invalidateProfileAuthorCaches(
        queryClient,
        mutationContext.sessionGeneration,
        authStore.user?.userId,
      )
      queryClient.invalidateQueries({ queryKey: authKey(userQueryKeys.myBadges) })
      queryClient.invalidateQueries({ queryKey: userQueryKeys.badgesRoot })
    },
  })

  return {
    useMyPoint,
    useMyScraps,
    useMyDrafts,
    useMyScheduledPosts,
    useUserBadges,
    useMyBadges,
    useMyPointHistories,
    useRecentlyViewedPosts,
    useUpdateRepresentativeBadge,
  }
}
