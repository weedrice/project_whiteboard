import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { userApi, type UserAgent } from '@/api/user'
import { useDashboardPagination } from '@/composables/useDashboardPagination'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import { createMyAgentsQueryOptions, createMyProfileQueryOptions } from '@/composables/useUser'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { useAuthStore } from '@/stores/auth'
import type { MyComment, PostSummary, User } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { getListLoadErrorMessage } from '@/utils/listLoadError'
import { encodePathSegment } from '@/utils/urlPath'

type Translate = (key: string) => string

export interface MyCommentListItem {
  commentId: number
  content: string | null
  createdAt: string
  postLink: string | null
  postTitle: string
  boardLabel: string
}

export function useMyPageDashboardResource(t: Translate) {
  const { handleSilentError } = useErrorHandler()
  const queryClient = useQueryClient()
  const authStore = useAuthStore()

  const profile = ref<User | null>(null)
  const myAgents = ref<UserAgent[]>([])

  const myPostsPagination = useDashboardPagination<PostSummary>(
    (params, { signal }) => queryClient.fetchQuery({
      queryKey: userQueryKeys.myPosts(params),
      queryFn: async () => {
        const { data } = await userApi.getMyPosts(params, { signal })
        return data
      },
      staleTime: QUERY_STALE_TIME.SHORT
    }),
    { page: 0, size: 10, sort: 'createdAt,desc' },
    t,
  )
  const myCommentsPagination = useDashboardPagination<MyComment>(
    (params, { signal }) => queryClient.fetchQuery({
      queryKey: userQueryKeys.myComments(params),
      queryFn: async () => {
        const { data } = await userApi.getMyComments(params, { signal })
        return data
      },
      staleTime: QUERY_STALE_TIME.SHORT
    }),
    { page: 0, size: 10 },
    t,
  )

  const myPosts = myPostsPagination.items
  const myPostsTotalCount = myPostsPagination.totalCount
  const myPostsTotalPages = myPostsPagination.totalPages
  const myPostsCurrentPage = myPostsPagination.page
  const myPostsSize = myPostsPagination.size
  const myPostsSort = myPostsPagination.sort

  const myComments = myCommentsPagination.items
  const myCommentsTotalCount = myCommentsPagination.totalCount
  const myCommentsTotalPages = myCommentsPagination.totalPages
  const myCommentsCurrentPage = myCommentsPagination.page
  const myCommentsSize = myCommentsPagination.size
  const myCommentItems = computed<MyCommentListItem[]>(() => myComments.value.map((comment) => ({
    commentId: comment.commentId,
    content: comment.content,
    createdAt: comment.createdAt,
    postLink: comment.post
      ? `/board/${encodePathSegment(comment.post.boardUrl)}/post/${encodePathSegment(comment.post.postId)}`
      : null,
    postTitle: comment.post?.title ?? '',
    boardLabel: comment.post?.boardName ?? ''
  })))

  const isLoading = ref(true)
  const loadFailedMessage = getListLoadErrorMessage(t)
  const profileTask = useLatestAsyncTask<string>({
    getErrorValue: () => loadFailedMessage,
    onError: (err) => handleSilentError(err, 'Failed to load my profile')
  })
  const agentsTask = useLatestAsyncTask<string>({
    getErrorValue: () => loadFailedMessage,
    onError: (err) => handleSilentError(err, 'Failed to load my agents')
  })
  const isProfileLoading = profileTask.loading
  const isAgentsLoading = agentsTask.loading
  const isMyPostsLoading = myPostsPagination.loading
  const isMyCommentsLoading = myCommentsPagination.loading
  const profileError = profileTask.error
  const agentsError = agentsTask.error
  const myPostsError = myPostsPagination.error
  const myCommentsError = myCommentsPagination.error

  const error = computed(() => {
    const hasAnyError = !!(profileError.value || agentsError.value || myPostsError.value || myCommentsError.value)
    const hasAnyContent = !!profile.value || myAgents.value.length > 0 || myPosts.value.length > 0 || myComments.value.length > 0
    return hasAnyError && !hasAnyContent ? loadFailedMessage : null
  })

  function markLoadFailed(target: { value: string | null }) {
    target.value = loadFailedMessage
  }

  async function fetchMyProfile() {
    if (authStore.user) {
      const cachedProfile = queryClient.getQueryData<User>(userQueryKeys.me)
      const profileSnapshot = cachedProfile?.userId === authStore.user.userId
        ? {
            ...cachedProfile,
            isEmailVerified: authStore.user.isEmailVerified ?? cachedProfile.isEmailVerified,
          }
        : authStore.user

      profile.value = profileSnapshot
      queryClient.setQueryData(userQueryKeys.me, profileSnapshot)
      return
    }

    const data = await profileTask.run(({ signal }) => queryClient.fetchQuery(createMyProfileQueryOptions({ signal })))

    if (data === undefined) return
    if (data) {
      profile.value = data
    } else {
      markLoadFailed(profileError)
    }
  }

  async function fetchMyAgents() {
    const data = await agentsTask.run(({ signal }) => queryClient.fetchQuery(createMyAgentsQueryOptions({ signal })))

    if (data === undefined) return
    if (data?.agents) {
      myAgents.value = data.agents
    } else {
      markLoadFailed(agentsError)
    }
  }

  const fetchMyPosts = () => myPostsPagination.fetch()

  const fetchMyComments = () => myCommentsPagination.fetch()

  function handleMyPostsPageChange(page: number) {
    return myPostsPagination.handlePageChange(page)
  }

  function handleMyPostsSortChange(newSort: string) {
    myPostsSort.value = newSort
    return fetchMyPosts()
  }

  function handleMyCommentsPageChange(page: number) {
    return myCommentsPagination.handlePageChange(page)
  }

  function getAgentStatusLabel(status: UserAgent['status']) {
    if (status === 'ACTIVE') return t('user.dashboard.agentStatus.active')
    if (status === 'SUSPENDED') return t('user.dashboard.agentStatus.unregistered')
    return t('user.dashboard.agentStatus.pending')
  }

  async function loadDashboard() {
    isLoading.value = true
    profileError.value = null
    agentsError.value = null
    myPostsError.value = null
    myCommentsError.value = null
    try {
      await Promise.all([
        fetchMyProfile(),
        fetchMyAgents(),
        fetchMyPosts(),
        fetchMyComments()
      ])
    } finally {
      isLoading.value = false
    }
  }

  return {
    profile,
    myAgents,
    myPosts,
    myPostsTotalCount,
    myPostsTotalPages,
    myPostsCurrentPage,
    myPostsSize,
    myPostsSort,
    myComments,
    myCommentItems,
    myCommentsTotalCount,
    myCommentsTotalPages,
    myCommentsCurrentPage,
    myCommentsSize,
    isLoading,
    isProfileLoading,
    isAgentsLoading,
    isMyPostsLoading,
    isMyCommentsLoading,
    error,
    profileError,
    agentsError,
    myPostsError,
    myCommentsError,
    fetchMyProfile,
    fetchMyAgents,
    fetchMyPosts,
    fetchMyComments,
    handleMyPostsPageChange,
    handleMyPostsSortChange,
    handleMyCommentsPageChange,
    getAgentStatusLabel,
    loadDashboard
  }
}
