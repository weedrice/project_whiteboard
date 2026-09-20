import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { VueQueryPlugin, type QueryClient } from '@tanstack/vue-query'
import { commentApi } from '@/api/comment'
import { apiSuccessResponse } from '@/test/apiResponseFixtures'
import { expectQueriesRefetchedOnce } from '@/test/queryInvalidation'
import { sessionQueryKey } from '@/queryAuthScope'
import { useComment } from '../useComment'

const authStore = vi.hoisted(() => ({ sessionGeneration: 3 }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('@/api/comment', () => ({ commentApi: { likeComment: vi.fn(), unlikeComment: vi.fn() } }))

const commentKeys = [
  ['comments', 'post', 123, { page: 0 }],
  ['comments', 'post', 123, 'infinite', { size: 20 }],
  ['comments', 'post', 123, 'best'],
  ['comments', 'post', 456, { page: 0 }],
  ['comments', 'replies', 7, { page: 0 }],
]

async function toggleLike(queryClient: QueryClient, liked: boolean, fails = false) {
  let mutation!: ReturnType<ReturnType<typeof useComment>['useToggleCommentLike']>
  const wrapper = mount(defineComponent({
    setup() {
      mutation = useComment().useToggleCommentLike()
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
  try {
    const request = mutation.mutateAsync({ commentId: 7, postId: 123, liked })
    if (fails) await expect(request).rejects.toThrow('like failed')
    else await request
  } finally {
    wrapper.unmount()
  }
}

describe('comment like cache invalidation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authStore.sessionGeneration = 3
    vi.mocked(commentApi.likeComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.likeComment>())
    vi.mocked(commentApi.unlikeComment).mockResolvedValue(apiSuccessResponse<typeof commentApi.unlikeComment>())
  })

  it.each([
    { liked: true, fails: false },
    { liked: false, fails: false },
    { liked: true, fails: true },
  ])('refreshes comments once without cancelling requests after $liked / failure $fails', async ({ liked, fails }) => {
    if (fails) vi.mocked(commentApi.likeComment).mockRejectedValueOnce(new Error('like failed'))
    const affected = commentKeys.map((key) => sessionQueryKey(3, key))
    const refreshed = await expectQueriesRefetchedOnce(
      (client) => toggleLike(client, liked, fails),
      affected,
      [
        ...commentKeys.map((key) => sessionQueryKey(4, key)),
        sessionQueryKey(3, ['post', 123]),
      ],
    )
    expect(refreshed).toEqual(affected)
  })

  it('does not refresh either session when a like completes after a session change', async () => {
    vi.mocked(commentApi.likeComment).mockImplementationOnce(async () => {
      authStore.sessionGeneration = 4
      return apiSuccessResponse<typeof commentApi.likeComment>()
    })
    const refreshed = await expectQueriesRefetchedOnce(
      (client) => toggleLike(client, true),
      [],
      [
        ...commentKeys.map((key) => sessionQueryKey(3, key)),
        ...commentKeys.map((key) => sessionQueryKey(4, key)),
      ],
    )
    expect(refreshed).toEqual([])
  })
})
