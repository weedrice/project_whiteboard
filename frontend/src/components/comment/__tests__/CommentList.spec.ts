import { ref, type Ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import CommentList from '../CommentList.vue'

const { authState, bestCommentsValue, commentsValue, commentsState, deleteComment, fetchNextPage, getComment, refetchComments } = vi.hoisted(() => ({
  authState: {
    isAuthenticated: true,
  },
  commentsValue: {
    content: [
      { commentId: 1, content: '첫 댓글' },
    ],
    totalElements: 1,
  },
  commentsState: {
    error: null as Error | null,
    hasNextPage: false,
  },
  bestCommentsValue: [] as Array<{ commentId: number; content: string }>,
  deleteComment: vi.fn(),
  fetchNextPage: vi.fn(),
  getComment: vi.fn(),
  refetchComments: vi.fn(),
}))

let capturedCommentParams: Ref<{ page: number; size: number; sort: string }> | null = null

vi.mock('vue-i18n', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-i18n')>()
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string, params?: Record<string, number>) => ({
        'comment.loadFailed': '댓글을 불러오지 못했습니다.',
        'comment.loadMore': `댓글 더 보기 (${params?.remaining}개 남음)`,
      })[key] ?? key,
    }),
  }
})

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/stores/toast', () => ({
  useToastStore: () => ({
    addToast: vi.fn(),
  }),
}))

vi.mock('@/composables/useConfirm', () => ({
  useConfirm: () => ({
    confirm: vi.fn(async () => true),
  }),
}))

vi.mock('@/api/comment', () => ({
  commentApi: {
    getComment,
  },
}))

vi.mock('@/features/comments/queries/useComment', () => ({
  useComment: () => ({
    useInfiniteComments: (_postId: Ref<string | number>, params: Ref<{ page: number; size: number; sort: string }>) => {
      capturedCommentParams = params
      return {
        data: ref({ pages: [commentsValue], pageParams: [0] }),
        isLoading: ref(false),
        isFetchingNextPage: ref(false),
        hasNextPage: ref(commentsState.hasNextPage),
        fetchNextPage,
        error: ref(commentsState.error),
        refetch: refetchComments,
      }
    },
    useBestComments: () => ({
      data: ref(bestCommentsValue),
    }),
    useDeleteComment: () => ({
      mutate: deleteComment,
    }),
  }),
}))

const mountCommentList = (props: { targetCommentId?: number | null; readOnly?: boolean } = {}) => mount(CommentList, {
  props: {
    postId: 1,
    boardUrl: 'free',
    ...props,
  },
  global: {
    mocks: {
      $t: (key: string, params?: Record<string, number>) => (
        key === 'comment.loadMore' ? `댓글 더 보기 (${params?.remaining}개 남음)` : key
      ),
    },
    stubs: {
      RouterLink: RouterLinkStub,
      CommentForm: {
        template: '<div data-testid="comment-form">form</div>',
      },
      CommentItem: {
        emits: ['delete'],
        props: ['comment', 'deepLinkCommentIds', 'readOnly'],
        template: '<div data-testid="comment-item" :data-comment-id="comment.commentId" :data-read-only="String(Boolean(readOnly))" :data-deep-link-path="(deepLinkCommentIds || []).join(\',\')"><span>{{ comment.content }}</span><button type="button" data-testid="delete-comment" @click="$emit(\'delete\', comment)">delete</button></div>',
      },
      BaseSkeleton: true,
      BaseButton: {
        emits: ['click'],
        template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
      },
      BaseSegmentedControl: {
        props: ['modelValue', 'options', 'label'],
        emits: ['update:modelValue'],
        template: '<div data-testid="sort-control"><button v-for="option in options" :key="option.value" type="button" @click="$emit(\'update:modelValue\', option.value)">{{ option.label }}</button></div>',
      },
    },
  },
})

describe('CommentList', () => {
  beforeEach(() => {
    authState.isAuthenticated = true
    commentsState.error = null
    commentsState.hasNextPage = false
    fetchNextPage.mockClear()
    refetchComments.mockClear()
    getComment.mockReset()
    commentsValue.content = [
      { commentId: 1, content: '첫 댓글' },
    ]
    commentsValue.totalElements = 1
    bestCommentsValue.splice(0)
    capturedCommentParams = null
  })

  it('renders the composer before the comment list', () => {
    const wrapper = mountCommentList()
    const html = wrapper.html()

    expect(wrapper.find('#comment-composer').exists()).toBe(true)
    expect(html.indexOf('data-testid="comment-form"')).toBeLessThan(html.indexOf('data-testid="comment-item"'))
  })

  it('shows the login prompt in the top composer area for guests', () => {
    authState.isAuthenticated = false
    commentsValue.content = []
    commentsValue.totalElements = 0

    const wrapper = mountCommentList()

    expect(wrapper.find('#comment-composer').text()).toContain('common.login')
    expect(wrapper.text()).toContain('comment.empty')
  })

  it('hides the composer and marks all archived comments read-only', () => {
    const wrapper = mountCommentList({ readOnly: true })

    expect(wrapper.find('[data-testid="comment-form"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="comment-item"]').attributes('data-read-only')).toBe('true')
  })

  it('shows a retryable error state when comments fail to load', async () => {
    commentsState.error = new Error('failed')

    const wrapper = mountCommentList()

    expect(wrapper.text()).toContain('댓글을 불러오지 못했습니다.')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    await wrapper.get('[role="alert"] button').trigger('click')
    expect(refetchComments).toHaveBeenCalled()
  })

  it('shows a load-more action when more comments exist and fetches the next page', async () => {
    commentsValue.content = Array.from({ length: 50 }, (_, index) => ({
      commentId: index + 1,
      content: `comment ${index + 1}`,
    }))
    commentsValue.totalElements = 75
    commentsState.hasNextPage = true

    const wrapper = mountCommentList()

    expect(wrapper.text()).toContain('댓글 더 보기 (25개 남음)')

    await wrapper.findAll('button').at(-1)?.trigger('click')

    expect(capturedCommentParams?.value).toEqual({ page: 0, size: 50, sort: 'createdAt,asc' })
    expect(fetchNextPage).toHaveBeenCalledTimes(1)
  })

  it('resolves a deep-link ancestry path and passes only that branch to comment items', async () => {
    getComment
      .mockResolvedValueOnce({
        data: { success: true, data: { commentId: 11, parentId: 1, postId: 1 } },
      })
      .mockResolvedValueOnce({
        data: { success: true, data: { commentId: 1, parentId: null, postId: 1 } },
      })

    const wrapper = mountCommentList({ targetCommentId: 11 })
    await flushPromises()

    expect(getComment).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-comment-id="1"]').attributes('data-deep-link-path')).toBe('1,11')
  })

  it('deletes comments with the current post id for scoped cache invalidation', async () => {
    const wrapper = mountCommentList()

    await wrapper.get('[data-testid="delete-comment"]').trigger('click')
    await Promise.resolve()

    expect(deleteComment).toHaveBeenCalledWith(
      { commentId: 1, postId: 1 },
      expect.any(Object),
    )
  })

  it('emits the latest comment entering the reading viewport band', () => {
    const intersectionCallback: { value?: IntersectionObserverCallback } = {}
    class ReadingIntersectionObserver {
      constructor(callback: IntersectionObserverCallback) {
        intersectionCallback.value = callback
      }
      observe() {}
      disconnect() {}
      unobserve() {}
      takeRecords() { return [] }
      readonly root = null
      readonly rootMargin = ''
      readonly thresholds = []
    }
    vi.stubGlobal('IntersectionObserver', ReadingIntersectionObserver)
    const wrapper = mountCommentList()
    const target = wrapper.get('[data-comment-id="1"]').element

    intersectionCallback.value?.([
      { isIntersecting: true, target } as IntersectionObserverEntry,
    ], {} as IntersectionObserver)

    expect(wrapper.emitted('last-read-comment')).toEqual([[1]])
  })

  it('observes only main comments in oldest-first order and excludes best comments', async () => {
    commentsValue.content = [
      { commentId: 1, content: '첫 번째' },
      { commentId: 2, content: '두 번째' },
    ]
    commentsValue.totalElements = 2
    bestCommentsValue.push({ commentId: 99, content: '베스트' })
    const observed: Element[] = []
    const unobserved: Element[] = []
    const intersectionCallback: { value?: IntersectionObserverCallback } = {}
    class ReadingIntersectionObserver {
      constructor(callback: IntersectionObserverCallback) {
        intersectionCallback.value = callback
      }
      observe(element: Element) { observed.push(element) }
      unobserve(element: Element) { unobserved.push(element) }
      disconnect() {}
      takeRecords() { return [] }
      readonly root = null
      readonly rootMargin = ''
      readonly thresholds = []
    }
    vi.stubGlobal('IntersectionObserver', ReadingIntersectionObserver)
    const wrapper = mountCommentList()
    await Promise.resolve()

    expect(observed.map((element) => (element as HTMLElement).dataset.commentId)).toEqual(['1', '2'])
    expect(observed.some((element) => (element as HTMLElement).dataset.commentId === '99')).toBe(false)

    const mainTarget = wrapper.get('[data-reading-comment][data-comment-id="2"]').element
    intersectionCallback.value?.([
      { isIntersecting: true, target: mainTarget } as IntersectionObserverEntry,
    ], {} as IntersectionObserver)
    expect(wrapper.emitted('last-read-comment')).toEqual([[2]])

    await wrapper.findAll('[data-testid="sort-control"] button')[1]!.trigger('click')
    await Promise.resolve()
    expect(unobserved).toEqual(expect.arrayContaining(observed))

    intersectionCallback.value?.([
      { isIntersecting: true, target: mainTarget } as IntersectionObserverEntry,
    ], {} as IntersectionObserver)
    expect(wrapper.emitted('last-read-comment')).toEqual([[2]])
  })
})
