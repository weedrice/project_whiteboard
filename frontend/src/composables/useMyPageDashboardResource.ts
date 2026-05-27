import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { userApi, type UserAgent } from '@/api/user'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useLatestAsyncTask } from '@/composables/useLatestAsyncTask'
import { createMyAgentsQueryOptions, createMyProfileQueryOptions } from '@/composables/useUser'
import { useAuthStore } from '@/stores/auth'
import type { MyComment, PostSummary, User } from '@/types'
import { QUERY_STALE_TIME } from '@/utils/constants'

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

  const myPosts = ref<PostSummary[]>([])
  const myPostsTotalCount = ref(0)
  const myPostsCurrentPage = ref(0)
  const myPostsSize = ref(10)
  const myPostsSort = ref('createdAt,desc')

  const myComments = ref<MyComment[]>([])
  const myCommentsTotalCount = ref(0)
  const myCommentsCurrentPage = ref(0)
  const myCommentsSize = ref(10)
  const myCommentItems = computed<MyCommentListItem[]>(() => myComments.value.map((comment) => ({
    commentId: comment.commentId,
    content: comment.content,
    createdAt: comment.createdAt,
    postLink: comment.post ? `/board/${comment.post.boardUrl}/post/${comment.post.postId}` : null,
    postTitle: comment.post?.title ?? '',
    boardLabel: comment.post?.boardName ?? ''
  })))

  const isLoading = ref(true)
  const isProfileLoading = ref(false)
  const isAgentsLoading = ref(false)
  const profileError = ref<string | null>(null)
  const agentsError = ref<string | null>(null)
  const loadFailedMessage = '데이터를 불러오는데 실패했습니다.'
  const myPostsTask = useLatestAsyncTask<string>({
    getErrorValue: () => loadFailedMessage,
    onError: (err) => handleSilentError(err, 'Failed to load my posts')
  })
  const myCommentsTask = useLatestAsyncTask<string>({
    getErrorValue: () => loadFailedMessage,
    onError: (err) => handleSilentError(err, 'Failed to load my comments')
  })
  const isMyPostsLoading = myPostsTask.loading
  const isMyCommentsLoading = myCommentsTask.loading
  const myPostsError = myPostsTask.error
  const myCommentsError = myCommentsTask.error

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
      const cachedProfile = queryClient.getQueryData<User>(['user', 'me'])
      if (cachedProfile?.userId === authStore.user.userId) {
        profile.value = cachedProfile
      } else {
        profile.value = authStore.user
        queryClient.setQueryData(['user', 'me'], authStore.user)
      }
      return
    }

    isProfileLoading.value = true
    profileError.value = null
    try {
      const data = await queryClient.fetchQuery(createMyProfileQueryOptions())
      if (data) {
        profile.value = data
      } else {
        markLoadFailed(profileError)
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my profile')
      markLoadFailed(profileError)
    } finally {
      isProfileLoading.value = false
    }
  }

  async function fetchMyAgents() {
    isAgentsLoading.value = true
    agentsError.value = null
    try {
      const data = await queryClient.fetchQuery(createMyAgentsQueryOptions())
      if (data?.agents) {
        myAgents.value = data.agents
      } else {
        markLoadFailed(agentsError)
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my agents')
      markLoadFailed(agentsError)
    } finally {
      isAgentsLoading.value = false
    }
  }

  async function fetchMyPosts() {
    const data = await myPostsTask.run(async () => {
      const params = {
        page: myPostsCurrentPage.value,
        size: myPostsSize.value,
        sort: myPostsSort.value
      }
      return queryClient.fetchQuery({
        queryKey: ['user', 'me', 'posts', params],
        queryFn: async () => {
          const { data } = await userApi.getMyPosts(params)
          return data.success ? data.data : null
        },
        staleTime: QUERY_STALE_TIME.SHORT
      })
    })

    if (data === undefined) return
    if (data) {
      myPosts.value = data.content
      myPostsTotalCount.value = data.totalElements
    } else {
      markLoadFailed(myPostsError)
    }
  }

  async function fetchMyComments() {
    const data = await myCommentsTask.run(async () => {
      const params = {
        page: myCommentsCurrentPage.value,
        size: myCommentsSize.value
      }
      return queryClient.fetchQuery({
        queryKey: ['user', 'me', 'comments', params],
        queryFn: async () => {
          const { data } = await userApi.getMyComments(params)
          return data.success ? data.data : null
        },
        staleTime: QUERY_STALE_TIME.SHORT
      })
    })

    if (data === undefined) return
    if (data) {
      myComments.value = data.content
      myCommentsTotalCount.value = data.totalElements
    } else {
      markLoadFailed(myCommentsError)
    }
  }

  function handleMyPostsPageChange(page: number) {
    myPostsCurrentPage.value = page
    return fetchMyPosts()
  }

  function handleMyPostsSortChange(newSort: string) {
    myPostsSort.value = newSort
    return fetchMyPosts()
  }

  function handleMyCommentsPageChange(page: number) {
    myCommentsCurrentPage.value = page
    return fetchMyComments()
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
