import { useQueryClient } from '@tanstack/vue-query'
import { computed, ref } from 'vue'
import { userApi, type UserAgent } from '@/api/user'
import { useErrorHandler } from '@/composables/useErrorHandler'
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
  const error = ref<string | null>(null)
  const loadFailedMessage = '데이터를 불러오는데 실패했습니다.'

  function markLoadFailed() {
    error.value = loadFailedMessage
  }

  async function fetchMyProfile() {
    try {
      const data = await queryClient.fetchQuery({
        queryKey: ['user', 'me'],
        queryFn: async () => {
          const { data } = await userApi.getMyProfile()
          return data.success ? data.data : null
        },
        staleTime: QUERY_STALE_TIME.MEDIUM
      })
      if (data) {
        profile.value = data
      } else {
        markLoadFailed()
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my profile')
      markLoadFailed()
    }
  }

  async function fetchMyAgents() {
    try {
      const data = await queryClient.fetchQuery({
        queryKey: ['user', 'agents'],
        queryFn: async () => {
          const { data } = await userApi.getMyAgents()
          return data.data
        },
        staleTime: QUERY_STALE_TIME.MEDIUM
      })
      if (data?.agents) {
        myAgents.value = data.agents
      } else {
        markLoadFailed()
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my agents')
      markLoadFailed()
    }
  }

  async function fetchMyPosts() {
    try {
      const params = {
        page: myPostsCurrentPage.value,
        size: myPostsSize.value,
        sort: myPostsSort.value
      }
      const data = await queryClient.fetchQuery({
        queryKey: ['user', 'me', 'posts', params],
        queryFn: async () => {
          const { data } = await userApi.getMyPosts(params)
          return data.success ? data.data : null
        },
        staleTime: QUERY_STALE_TIME.SHORT
      })
      if (data) {
        myPosts.value = data.content
        myPostsTotalCount.value = data.totalElements
      } else {
        markLoadFailed()
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my posts')
      markLoadFailed()
    }
  }

  async function fetchMyComments() {
    try {
      const params = {
        page: myCommentsCurrentPage.value,
        size: myCommentsSize.value
      }
      const data = await queryClient.fetchQuery({
        queryKey: ['user', 'me', 'comments', params],
        queryFn: async () => {
          const { data } = await userApi.getMyComments(params)
          return data.success ? data.data : null
        },
        staleTime: QUERY_STALE_TIME.SHORT
      })
      if (data) {
        myComments.value = data.content
        myCommentsTotalCount.value = data.totalElements
      } else {
        markLoadFailed()
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my comments')
      markLoadFailed()
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
    error.value = null
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
    error,
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
