import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { userApi, type UserAgent } from '@/api/user'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import { usePagination } from '@/composables/usePagination'
import { createMyAgentsQueryOptions, createMyProfileQueryOptions } from '@/composables/useUser'
import { userQueryKeys } from '@/composables/userQueryKeys'
import { useAuthStore } from '@/stores/auth'
import type { MyComment, PostSummary, User } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'
import { getListLoadErrorMessage } from '@/utils/listLoadError'

export interface MyCommentListItem {
  commentId: number
  content: string | null
  createdAt: string
  postLink: string | null
  postTitle: string
  boardLabel: string
}

export function useMyPageDashboardResource() {
  const { handleSilentError } = useErrorHandler()
  const queryClient = useQueryClient()
  const authStore = useAuthStore()

  const profile = ref<User | null>(null)
  const myAgents = ref<UserAgent[]>([])

  const myPostsPagination = usePagination<PostSummary>(
    (params, { signal }) => queryClient.fetchQuery({
      queryKey: userQueryKeys.myPosts(params),
      queryFn: async () => {
        const { data } = await userApi.getMyPosts(params, { signal })
        return data
      },
      staleTime: QUERY_STALE_TIME.SHORT
    }),
    { page: 0, size: 10, sort: 'createdAt,desc' }
  )
  const myCommentsPagination = usePagination<MyComment>(
    (params, { signal }) => queryClient.fetchQuery({
      queryKey: userQueryKeys.myComments(params),
      queryFn: async () => {
        const { data } = await userApi.getMyComments(params, { signal })
        return data
      },
      staleTime: QUERY_STALE_TIME.SHORT
    }),
    { page: 0, size: 10 }
  )

  const myPosts = myPostsPagination.items
  const myPostsTotalCount = myPostsPagination.totalCount
  const myPostsCurrentPage = myPostsPagination.page
  const myPostsSize = myPostsPagination.size
  const myPostsSort = myPostsPagination.sort

  const myComments = myCommentsPagination.items
  const myCommentsTotalCount = myCommentsPagination.totalCount
  const myCommentsCurrentPage = myCommentsPagination.page
  const myCommentsSize = myCommentsPagination.size
  const myCommentItems = computed<MyCommentListItem[]>(() => myComments.value.map((comment) => ({
    commentId: comment.commentId,
    content: comment.content,
    createdAt: comment.createdAt,
    postLink: comment.post ? `/board/${comment.post.boardUrl}/post/${comment.post.postId}` : null,
    postTitle: comment.post?.title ?? '',
    boardLabel: comment.post?.boardName ?? ''
  })))

  const isLoading = ref(true)
  const loadFailedMessage = getListLoadErrorMessage()
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
      if (cachedProfile?.userId === authStore.user.userId) {
        profile.value = cachedProfile
      } else {
        profile.value = authStore.user
        queryClient.setQueryData(userQueryKeys.me, authStore.user)
      }
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
    if (status === 'ACTIVE') return '활성'
    if (status === 'SUSPENDED') return '미등록'
    return '대기'
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
    myPostsCurrentPage,
    myPostsSize,
    myPostsSort,
    myComments,
    myCommentItems,
    myCommentsTotalCount,
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
