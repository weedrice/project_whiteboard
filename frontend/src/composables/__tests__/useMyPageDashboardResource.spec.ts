import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useMyPageDashboardResource } from '../useMyPageDashboardResource'
import { userApi } from '@/api/user'
import { QUERY_STALE_TIME } from '@/utils/constants'

const mocks = vi.hoisted(() => ({
  fetchQuery: vi.fn(),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({
    fetchQuery: mocks.fetchQuery,
  }),
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getMyProfile: vi.fn(),
    getMyAgents: vi.fn(),
    getMyPosts: vi.fn(),
    getMyComments: vi.fn(),
  },
}))

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({
    handleSilentError: vi.fn(),
  }),
}))

const loadFailedMessage = '데이터를 불러오는데 실패했습니다.'

const createDeferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve
  })

  return { promise, resolve }
}

describe('useMyPageDashboardResource', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.fetchQuery.mockImplementation(async (options: { queryFn: () => Promise<unknown> }) => options.queryFn())
    vi.mocked(userApi.getMyProfile).mockResolvedValue({
      data: { success: true, data: { userId: 1, email: 'me@example.com' } },
    } as never)
    vi.mocked(userApi.getMyAgents).mockResolvedValue({
      data: { success: true, data: { agents: [{ agentId: 1, name: 'Agent', status: 'ACTIVE' }] } },
    } as never)
    vi.mocked(userApi.getMyPosts).mockResolvedValue({
      data: { success: true, data: { content: [{ postId: 7, title: 'Post' }], totalElements: 1 } },
    } as never)
    vi.mocked(userApi.getMyComments).mockResolvedValue({
      data: {
        success: true,
        data: {
          content: [{
            commentId: 3,
            content: 'Comment',
            createdAt: '2026-05-20T10:00:00',
            post: {
              postId: 9,
              boardUrl: 'notice',
              boardName: 'Notice',
              title: 'Post title',
            },
          }],
          totalElements: 1,
        },
      },
    } as never)
  })

  it('loads dashboard resources with the existing pagination defaults', async () => {
    const resource = useMyPageDashboardResource()

    await resource.loadDashboard()

    expect(userApi.getMyPosts).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: 'createdAt,desc',
    })
    expect(userApi.getMyComments).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(mocks.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
      queryKey: ['user', 'me'],
      staleTime: QUERY_STALE_TIME.MEDIUM,
    }))
    expect(mocks.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
      queryKey: ['user', 'agents'],
      staleTime: QUERY_STALE_TIME.MEDIUM,
    }))
    expect(mocks.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
      queryKey: ['user', 'me', 'posts', {
        page: 0,
        size: 10,
        sort: 'createdAt,desc',
      }],
      staleTime: QUERY_STALE_TIME.SHORT,
    }))
    expect(mocks.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
      queryKey: ['user', 'me', 'comments', {
        page: 0,
        size: 10,
      }],
      staleTime: QUERY_STALE_TIME.SHORT,
    }))
    expect(resource.profile.value?.email).toBe('me@example.com')
    expect(resource.myAgents.value).toHaveLength(1)
    expect(resource.myPostsTotalCount.value).toBe(1)
    expect(resource.myCommentsTotalCount.value).toBe(1)
    expect(resource.myCommentItems.value).toEqual([{
      commentId: 3,
      content: 'Comment',
      createdAt: '2026-05-20T10:00:00',
      postLink: '/board/notice/post/9',
      postTitle: 'Post title',
      boardLabel: 'Notice',
    }])
    expect(resource.isLoading.value).toBe(false)
  })

  it('updates post sort before refetching my posts', async () => {
    const resource = useMyPageDashboardResource()

    await resource.handleMyPostsSortChange('likeCount,desc')

    expect(userApi.getMyPosts).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: 'likeCount,desc',
    })
    expect(mocks.fetchQuery).toHaveBeenCalledWith(expect.objectContaining({
      queryKey: ['user', 'me', 'posts', {
        page: 0,
        size: 10,
        sort: 'likeCount,desc',
      }],
    }))
  })

  it('keeps successful dashboard sections visible when a resource request fails', async () => {
    mocks.fetchQuery.mockImplementation(async (options: { queryKey: unknown[]; queryFn: () => Promise<unknown> }) => {
      if (options.queryKey[0] === 'user' && options.queryKey[2] === 'posts') {
        throw new Error('network')
      }
      return options.queryFn()
    })
    const resource = useMyPageDashboardResource()

    await resource.loadDashboard()

    expect(resource.error.value).toBeNull()
    expect(resource.myPostsError.value).toBe(loadFailedMessage)
    expect(resource.profile.value?.email).toBe('me@example.com')
    expect(resource.myCommentsTotalCount.value).toBe(1)
    expect(resource.isLoading.value).toBe(false)
  })

  it('sets a section error when a dashboard resource returns an unsuccessful envelope', async () => {
    vi.mocked(userApi.getMyComments).mockResolvedValueOnce({
      data: { success: false },
    } as never)
    const resource = useMyPageDashboardResource()

    await resource.loadDashboard()

    expect(resource.error.value).toBeNull()
    expect(resource.myCommentsError.value).toBe(loadFailedMessage)
    expect(resource.isLoading.value).toBe(false)
  })

  it('ignores stale my posts responses from earlier requests', async () => {
    const firstRequest = createDeferred<{ content: Array<{ postId: number; title: string }>; totalElements: number }>()
    const secondRequest = createDeferred<{ content: Array<{ postId: number; title: string }>; totalElements: number }>()
    mocks.fetchQuery
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)
    const resource = useMyPageDashboardResource()

    const firstFetch = resource.handleMyPostsPageChange(1)
    const secondFetch = resource.handleMyPostsPageChange(2)

    secondRequest.resolve({ content: [{ postId: 2, title: 'Second' }], totalElements: 1 })
    await secondFetch

    firstRequest.resolve({ content: [{ postId: 1, title: 'First' }], totalElements: 1 })
    await firstFetch

    expect(resource.myPosts.value).toEqual([{ postId: 2, title: 'Second' }])
    expect(resource.isMyPostsLoading.value).toBe(false)
  })

  it('ignores stale my comments responses from earlier requests', async () => {
    const firstRequest = createDeferred<{ content: Array<{ commentId: number; content: string; createdAt: string }>; totalElements: number }>()
    const secondRequest = createDeferred<{ content: Array<{ commentId: number; content: string; createdAt: string }>; totalElements: number }>()
    mocks.fetchQuery
      .mockReturnValueOnce(firstRequest.promise)
      .mockReturnValueOnce(secondRequest.promise)
    const resource = useMyPageDashboardResource()

    const firstFetch = resource.handleMyCommentsPageChange(1)
    const secondFetch = resource.handleMyCommentsPageChange(2)

    secondRequest.resolve({ content: [{ commentId: 2, content: 'Second', createdAt: '2026-05-20T10:00:00' }], totalElements: 1 })
    await secondFetch

    firstRequest.resolve({ content: [{ commentId: 1, content: 'First', createdAt: '2026-05-20T09:00:00' }], totalElements: 1 })
    await firstFetch

    expect(resource.myComments.value).toEqual([{ commentId: 2, content: 'Second', createdAt: '2026-05-20T10:00:00' }])
    expect(resource.isMyCommentsLoading.value).toBe(false)
  })
})
