import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useMyPageDashboardResource } from '../useMyPageDashboardResource'
import { userApi } from '@/api/user'

vi.mock('@/api/user', () => ({
  userApi: {
    getMyProfile: vi.fn(),
    getMyAgents: vi.fn(),
    getMyPosts: vi.fn(),
    getMyComments: vi.fn()
  }
}))

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({
    handleSilentError: vi.fn()
  })
}))

describe('useMyPageDashboardResource', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(userApi.getMyProfile).mockResolvedValue({
      data: { success: true, data: { userId: 1, email: 'me@example.com' } }
    } as never)
    vi.mocked(userApi.getMyAgents).mockResolvedValue({
      data: { success: true, data: { agents: [{ agentId: 1, name: 'Agent', status: 'ACTIVE' }] } }
    } as never)
    vi.mocked(userApi.getMyPosts).mockResolvedValue({
      data: { success: true, data: { content: [{ postId: 7, title: 'Post' }], totalElements: 1 } }
    } as never)
    vi.mocked(userApi.getMyComments).mockResolvedValue({
      data: { success: true, data: { content: [{ commentId: 3, content: 'Comment' }], totalElements: 1 } }
    } as never)
  })

  it('loads dashboard resources with the existing pagination defaults', async () => {
    const resource = useMyPageDashboardResource()

    await resource.loadDashboard()

    expect(userApi.getMyPosts).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: 'createdAt,desc'
    })
    expect(userApi.getMyComments).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(resource.profile.value?.email).toBe('me@example.com')
    expect(resource.myAgents.value).toHaveLength(1)
    expect(resource.myPostsTotalCount.value).toBe(1)
    expect(resource.myCommentsTotalCount.value).toBe(1)
    expect(resource.isLoading.value).toBe(false)
  })

  it('updates post sort before refetching my posts', async () => {
    const resource = useMyPageDashboardResource()

    await resource.handleMyPostsSortChange('likeCount,desc')

    expect(userApi.getMyPosts).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: 'likeCount,desc'
    })
  })
})
