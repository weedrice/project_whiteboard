import { reactive } from 'vue'
import { flushPromises, mount, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CommentList from '../CommentList.vue'
import CommentItem from '../CommentItem.vue'
import { commentApi, type Comment } from '@/api/comment'
import { commentQueryKeys } from '@/features/comments/queries/commentQueryKeys'
import { sessionQueryKey } from '@/queryAuthScope'
import { apiDataResponse, pageResponseFixture } from '@/test/apiResponseFixtures'

const authState = reactive({ isAuthenticated: true, sessionGeneration: 0, user: { userId: 99 } })
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: vi.fn() }) }))
vi.mock('@/api/comment', () => ({
  commentApi: { getComments: vi.fn(), getBestComments: vi.fn(), getReplies: vi.fn() },
}))

let queryClient: QueryClient
let wrapper: VueWrapper | undefined
let currentComment: Comment

async function setup() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div />' } }] })
  wrapper = mount(CommentList, {
    props: { postId: 1, boardUrl: 'general' },
    global: {
      plugins: [router, [VueQueryPlugin, { queryClient }]],
      stubs: {
        RouterLink: RouterLinkStub,
        CommentForm: { template: '<textarea data-testid="comment-draft" />' },
        ReportModal: true,
        UserMenu: {
          props: ['displayName'],
          template: '<span data-testid="comment-author">{{ displayName }}</span>',
        },
      },
    },
  })
  await vi.waitFor(() => expect(wrapper!.findComponent(CommentItem).exists()).toBe(true))
  return wrapper
}

async function refreshComments() {
  await queryClient.invalidateQueries({ queryKey: sessionQueryKey(0, commentQueryKeys.postRoot(1)) })
  await flushPromises()
  expect(commentApi.getComments).toHaveBeenCalledTimes(2)
}

describe('CommentList refreshed row data', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: Infinity } } })
    currentComment = {
      commentId: 1,
      content: 'Hello @Mentioned',
      author: { userId: 7, displayName: 'Before', profileImageUrl: '/before.png', representativeBadge: { badgeCode: 'OLD_BADGE' } },
      mentions: [{ userId: 8, displayName: 'Mentioned' }],
      likeCount: 0,
      isDeleted: false,
      createdAt: '2026-01-01T00:00:00',
      children: [],
      replyCount: 0,
      hasReplies: false,
    }
    vi.mocked(commentApi.getComments).mockImplementation(async () => apiDataResponse<typeof commentApi.getComments>(
      pageResponseFixture([currentComment], { number: 0, size: 50, totalElements: 1, totalPages: 1, last: true }),
    ))
    vi.mocked(commentApi.getBestComments).mockResolvedValue(apiDataResponse<typeof commentApi.getBestComments>([]))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    queryClient.clear()
  })

  it('refreshes author name, photo and badge without remounting an open reply draft', async () => {
    const view = await setup()
    const row = view.getComponent(CommentItem)
    expect(row.get('[data-testid="comment-author"]').text()).toBe('Before')
    expect(row.get('img').attributes('src')).toBe('/before.png')
    expect(row.text()).toContain('OLD_BADGE')
    await row.findAll('button').find(button => button.text() === '답글')!.trigger('click')
    await row.get('[data-testid="comment-draft"]').setValue('Keep my unfinished reply')

    currentComment = {
      ...currentComment,
      author: { userId: 7, displayName: 'After', profileImageUrl: '/after.png', representativeBadge: { badgeCode: 'NEW_BADGE' } },
    }
    await refreshComments()

    expect(row.get('[data-testid="comment-author"]').text()).toBe('After')
    expect(row.get('img').attributes('src')).toBe('/after.png')
    expect(row.text()).toContain('NEW_BADGE')
    expect(row.text()).not.toContain('OLD_BADGE')
    expect((row.get('[data-testid="comment-draft"]').element as HTMLTextAreaElement).value).toBe('Keep my unfinished reply')
  })

  it('removes stale mention links when refreshed metadata changes but the text is unchanged', async () => {
    const view = await setup()
    const row = view.getComponent(CommentItem)
    expect(row.get('[data-mention-user-id="8"]').text()).toBe('@Mentioned')

    currentComment = { ...currentComment, mentions: [] }
    await refreshComments()

    expect(row.find('[data-mention-user-id]').exists()).toBe(false)
    expect(row.text()).toContain('Hello @Mentioned')
  })
})
