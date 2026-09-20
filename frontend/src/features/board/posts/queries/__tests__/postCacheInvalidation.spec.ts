import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { VueQueryPlugin, type QueryClient } from '@tanstack/vue-query'
import { postApi } from '@/api/post'
import { apiDataResponse } from '@/test/apiResponseFixtures'
import { expectQueriesRefetchedOnce } from '@/test/queryInvalidation'
import { sessionQueryKey } from '@/queryAuthScope'
import { invalidatePostCollectionCaches } from '../postCacheUpdates'
import { usePost } from '../usePost'

const authStore = vi.hoisted(() => ({ sessionGeneration: 3 }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authStore }))
vi.mock('@/api/post', () => ({ postApi: { createPost: vi.fn() } }))

const collectionKeys = [
  ['posts', { page: 0 }],
  ['tags', 'popular'],
  ['home', 'landing', '24h'],
  ['board', 'posts', 'general', { page: 0 }],
  ['board', 'posts', 'infinite', 'general'],
  ['board', 'detail', 'general'],
  ['boards', 'subscriptions', 10],
  ['user', 'me', 'posts', { page: 0 }],
  ['search', 'posts', 'query'],
  ['user', '7'],
  ['user', 8, 'posts'],
  ['user', '7', 'comments'],
]
const unrelatedKeys = [
  ['user', 'me'], ['user', 'drafts'], ['board', 'notices', 'general'], ['post', 1],
]

async function createPost(queryClient: QueryClient) {
  let mutation!: ReturnType<ReturnType<typeof usePost>['useCreatePost']>
  const wrapper = mount(defineComponent({
    setup() {
      mutation = usePost().useCreatePost()
      return () => null
    },
  }), { global: { plugins: [[VueQueryPlugin, { queryClient }]] } })
  try {
    await mutation.mutateAsync({ boardUrl: 'general', data: { title: 'Title', contents: 'Body' } })
  } finally {
    wrapper.unmount()
  }
}

describe('post cache invalidation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authStore.sessionGeneration = 3
    vi.mocked(postApi.createPost).mockResolvedValue(apiDataResponse<typeof postApi.createPost>({ postId: 1 }))
  })

  it('refreshes every collection once while retaining public profile coverage and session isolation', async () => {
    const affected = collectionKeys.map((key) => sessionQueryKey(3, key))
    const refreshed = await expectQueriesRefetchedOnce(
      (client) => invalidatePostCollectionCaches(client, 3),
      affected,
      [
        ...collectionKeys.map((key) => sessionQueryKey(4, key)),
        ...unrelatedKeys.map((key) => sessionQueryKey(3, key)),
      ],
    )
    expect(refreshed).toEqual(affected)
  })

  it('refreshes tags, board lists, and points once after creating a post', async () => {
    const keys = [...collectionKeys, ['user', 'points', 'me']]
    const affected = keys.map((key) => sessionQueryKey(3, key))
    const refreshed = await expectQueriesRefetchedOnce(createPost, affected, [
      ...keys.map((key) => sessionQueryKey(4, key)),
      ...unrelatedKeys.map((key) => sessionQueryKey(3, key)),
    ])
    expect(refreshed).toEqual(affected)
  })

  it('does not refresh either session when post creation completes after a session change', async () => {
    vi.mocked(postApi.createPost).mockImplementationOnce(async () => {
      authStore.sessionGeneration = 4
      return apiDataResponse<typeof postApi.createPost>({ postId: 1 })
    })
    const keys = [...collectionKeys, ['user', 'points', 'me']]
    const refreshed = await expectQueriesRefetchedOnce(createPost, [], [
      ...keys.map((key) => sessionQueryKey(3, key)),
      ...keys.map((key) => sessionQueryKey(4, key)),
    ])
    expect(refreshed).toEqual([])
  })
})
