import { nextTick, reactive } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CommentList from '../CommentList.vue'
import { commentApi } from '@/api/comment'
import { apiDataResponse, apiSuccessDataResponse, pageResponseFixture } from '@/test/apiResponseFixtures'

const authState = reactive({ isAuthenticated: true, sessionGeneration: 0 })
vi.mock('@/stores/auth', () => ({ useAuthStore: () => authState }))
vi.mock('@/composables/useConfirm', () => ({ useConfirm: () => ({ confirm: vi.fn() }) }))
vi.mock('@/api/comment', () => ({
  commentApi: { getComments: vi.fn(), getBestComments: vi.fn(), getComment: vi.fn() },
}))

let queryClient: QueryClient
let wrapper: VueWrapper | undefined

function commentsPage(page: number, commentId = page === 0 ? 1 : 51, content = 'current') {
  return apiDataResponse<typeof commentApi.getComments>(pageResponseFixture(
    [{ commentId, content }],
    { number: page, size: 50, totalElements: 51, totalPages: 2, last: page === 1 },
  ))
}

function setup(targetCommentId = 51) {
  wrapper = mount(CommentList, {
    props: { postId: 1, boardUrl: 'free', targetCommentId },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      stubs: {
        CommentForm: true,
        CommentItem: {
          props: ['comment'],
          template: '<div :data-comment-id="comment.commentId">{{ comment.content }}</div>',
        },
        ErrorState: {
          emits: ['retry'],
          template: '<div data-testid="comment-error"><button @click="$emit(\'retry\')">retry</button></div>',
        },
        BaseSegmentedControl: {
          emits: ['update:modelValue'],
          template: '<button data-testid="newest-sort" @click="$emit(\'update:modelValue\', \'createdAt,desc\')">newest</button>',
        },
      },
    },
  })
  return wrapper
}

describe('CommentList deep links with Vue Query', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    authState.sessionGeneration = 0
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: Infinity } } })
    vi.mocked(commentApi.getBestComments).mockResolvedValue(apiDataResponse<typeof commentApi.getBestComments>([]))
    vi.mocked(commentApi.getComment).mockImplementation(async (id) => (
      apiSuccessDataResponse<typeof commentApi.getComment>({ commentId: Number(id), parentId: null, postId: Number(id) === 52 ? 2 : 1 })
    ))
    vi.mocked(commentApi.getComments).mockImplementation(async (_postId, params) => commentsPage(params.page ?? 0))
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    queryClient.clear()
  })

  it('traverses three successful pages in order to find the target root', async () => {
    vi.mocked(commentApi.getComments).mockImplementation(async (_postId, params) => {
      const page = params.page ?? 0
      return apiDataResponse<typeof commentApi.getComments>(pageResponseFixture(
        [{ commentId: page * 50 + 1, content: `page ${page}` }],
        { number: page, size: 50, totalElements: 101, totalPages: 3, last: page === 2 },
      ))
    })
    const view = setup(101)
    await vi.waitFor(() => expect(view.find('[data-comment-id="101"]').exists()).toBe(true))
    expect(vi.mocked(commentApi.getComments).mock.calls.map(([, params]) => params.page)).toEqual([0, 1, 2])
    expect(view.findAll('[data-comment-id]')).toHaveLength(3)
  })

  it('stops after an additional-page error and resumes only after the retry succeeds', async () => {
    let failNextPage = true
    vi.mocked(commentApi.getComments).mockImplementation(async (_postId, params) => {
      if (params.page === 1 && failNextPage) throw new Error('next page unavailable')
      return commentsPage(params.page ?? 0)
    })
    const view = setup()
    await vi.waitFor(() => expect(view.find('[role="alert"]').exists()).toBe(true))
    const firstComment = view.get('[data-comment-id="1"]').element
    await flushPromises()
    const nextPageCalls = () => vi.mocked(commentApi.getComments).mock.calls.filter(([, params]) => params.page === 1)
    expect(nextPageCalls()).toHaveLength(1)

    failNextPage = false
    await view.get('[role="alert"] button').trigger('click')
    await vi.waitFor(() => expect(view.find('[data-comment-id="51"]').exists()).toBe(true))
    expect(nextPageCalls()).toHaveLength(2)
    expect(view.find('[role="alert"]').exists()).toBe(false)
    expect(view.get('[data-comment-id="1"]').element).toBe(firstComment)
    expect(vi.mocked(commentApi.getComments).mock.calls.map(([, params]) => params.page)).toEqual([0, 1, 1])
  })

  it.each(['post', 'session', 'sort'] as const)('does not continue the old traversal after a %s change', async (change) => {
    let resolvePreviousPage!: (response: Awaited<ReturnType<typeof commentApi.getComments>>) => void
    let switched = false
    vi.mocked(commentApi.getComments).mockImplementation(async (postId, params) => {
      if (switched) return commentsPage(0, Number(postId) === 2 ? 52 : 51, 'new context')
      if (params.page === 1) return new Promise((resolve) => { resolvePreviousPage = resolve })
      return commentsPage(0)
    })
    const view = setup()
    await vi.waitFor(() => expect(resolvePreviousPage).toBeDefined())
    switched = true
    if (change === 'post') await view.setProps({ postId: 2, targetCommentId: 52 })
    if (change === 'session') {
      authState.sessionGeneration += 1
      await nextTick()
    }
    if (change === 'sort') await view.get('[data-testid="newest-sort"]').trigger('click')
    await vi.waitFor(() => expect(view.text()).toContain('new context'))
    const requestsAfterSwitch = vi.mocked(commentApi.getComments).mock.calls.length
    resolvePreviousPage(commentsPage(1, 51, 'old context'))
    await flushPromises()
    expect(view.text()).not.toContain('old context')
    expect(vi.mocked(commentApi.getComments).mock.calls).toHaveLength(requestsAfterSwitch)
    expect(view.find(`[data-comment-id="${change === 'post' ? 52 : 51}"]`).exists()).toBe(true)
  })

  it('cancels ancestry resolution across a session change', async () => {
    let resolveOldPath!: (response: Awaited<ReturnType<typeof commentApi.getComment>>) => void
    vi.mocked(commentApi.getComment).mockImplementationOnce(() => new Promise((resolve) => { resolveOldPath = resolve }))
    const view = setup()
    await vi.waitFor(() => expect(resolveOldPath).toBeDefined())
    authState.sessionGeneration += 1
    await vi.waitFor(() => expect(view.find('[data-comment-id="51"]').exists()).toBe(true))
    resolveOldPath(apiSuccessDataResponse<typeof commentApi.getComment>({ commentId: 99, parentId: null, postId: 1 }))
    await flushPromises()
    expect(view.find('[data-comment-id="99"]').exists()).toBe(false)
    expect(commentApi.getComment).toHaveBeenCalledTimes(2)
  })
})
