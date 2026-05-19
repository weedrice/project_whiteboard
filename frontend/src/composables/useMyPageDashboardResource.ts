import { ref } from 'vue'
import { userApi, type UserAgent } from '@/api/user'
import { useErrorHandler } from '@/composables/useErrorHandler'
import type { Comment, PostSummary, User } from '@/types'

export function useMyPageDashboardResource() {
  const { handleSilentError } = useErrorHandler()

  const profile = ref<User | null>(null)
  const myAgents = ref<UserAgent[]>([])

  const myPosts = ref<PostSummary[]>([])
  const myPostsTotalCount = ref(0)
  const myPostsCurrentPage = ref(0)
  const myPostsSize = ref(10)
  const myPostsSort = ref('createdAt,desc')

  const myComments = ref<Comment[]>([])
  const myCommentsTotalCount = ref(0)
  const myCommentsCurrentPage = ref(0)
  const myCommentsSize = ref(10)

  const isLoading = ref(true)
  const error = ref<string | null>(null)

  async function fetchMyProfile() {
    try {
      const { data } = await userApi.getMyProfile()
      if (data.success) {
        profile.value = data.data
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my profile')
    }
  }

  async function fetchMyAgents() {
    try {
      const { data } = await userApi.getMyAgents()
      if (data.success) {
        myAgents.value = data.data.agents
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my agents')
    }
  }

  async function fetchMyPosts() {
    try {
      const { data } = await userApi.getMyPosts({
        page: myPostsCurrentPage.value,
        size: myPostsSize.value,
        sort: myPostsSort.value
      })
      if (data.success) {
        myPosts.value = data.data.content
        myPostsTotalCount.value = data.data.totalElements
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my posts')
    }
  }

  async function fetchMyComments() {
    try {
      const { data } = await userApi.getMyComments({
        page: myCommentsCurrentPage.value,
        size: myCommentsSize.value
      })
      if (data.success) {
        myComments.value = data.data.content
        myCommentsTotalCount.value = data.data.totalElements
      }
    } catch (err: unknown) {
      handleSilentError(err, 'Failed to load my comments')
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
    await Promise.all([
      fetchMyProfile(),
      fetchMyAgents(),
      fetchMyPosts(),
      fetchMyComments()
    ])
    isLoading.value = false
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
